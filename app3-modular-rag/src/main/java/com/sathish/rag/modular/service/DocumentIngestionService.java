package com.sathish.rag.modular.service;

import com.sathish.rag.modular.dto.IngestRequest;
import com.sathish.rag.modular.dto.IngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final ParentChildChunkerService parentChildChunkerService;
    private final VectorStore vectorStore;

    /**
     * Ingests a document by chunking it into parent-child pairs and storing child chunks.
     *
     * @param request IngestRequest containing content and source identifier
     * @return IngestResponse with counts and status
     */
    public IngestResponse ingest(IngestRequest request) {
        String source = request.getSource() != null ? request.getSource() : "unknown";
        log.debug("ENTER ingest source={} contentLen={}", source, request.getContent().length());

        try {
            List<Document> childDocs = parentChildChunkerService.chunkIntoChildDocuments(
                    request.getContent(), source);

            if (childDocs.isEmpty()) {
                log.warn("No child documents produced for source={}", source);
                return IngestResponse.builder()
                        .status("OK")
                        .source(source)
                        .childChunksCreated(0)
                        .parentChunksCreated(0)
                        .message("No chunks produced — content may be too short")
                        .build();
            }

            Set<String> uniqueParentIds = new HashSet<>();
            for (Document doc : childDocs) {
                Object parentId = doc.getMetadata().get("parentId");
                if (parentId != null) uniqueParentIds.add(parentId.toString());
            }
            int parentCount = uniqueParentIds.size();

            vectorStore.add(childDocs);
            log.debug("EXIT ingest source={} childChunks={} parents={}", source, childDocs.size(), parentCount);

            return IngestResponse.builder()
                    .status("OK")
                    .source(source)
                    .childChunksCreated(childDocs.size())
                    .parentChunksCreated(parentCount)
                    .message("Successfully ingested " + childDocs.size() + " chunks from " + parentCount + " parent segments")
                    .build();

        } catch (Exception e) {
            log.error("Ingestion failed for source={}: {}", source, e.getMessage(), e);
            return IngestResponse.builder()
                    .status("ERROR")
                    .source(source)
                    .childChunksCreated(0)
                    .parentChunksCreated(0)
                    .message("Ingestion failed: " + e.getMessage())
                    .build();
        }
    }
}
