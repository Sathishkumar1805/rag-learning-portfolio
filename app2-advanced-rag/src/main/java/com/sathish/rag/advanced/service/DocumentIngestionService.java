/**
 * DocumentIngestionService.java
 * <p><b>RAG Role:</b> Orchestrates the document ingestion pipeline — accepts raw text,
 * delegates parent-child chunking to ParentChildChunkerService, and stores the resulting
 * child Documents (with parent metadata) in the Qdrant vector store.
 * <p><b>Learning Note:</b> The ingestion service is intentionally thin — it delegates
 * the chunking logic to a dedicated service (Single Responsibility Principle). This
 * makes each piece independently testable and replaceable (e.g., swap chunking strategy).
 * <p><b>LEARN:</b> App 1's ingestion was flat chunking → store. App 2 adds the parent-child
 * hierarchy and metadata enrichment before storage, enabling parent-context retrieval later.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.service;

import com.sathish.rag.advanced.config.RagProperties;
import com.sathish.rag.advanced.dto.IngestRequest;
import com.sathish.rag.advanced.dto.IngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final RagProperties ragProperties;
    private final ParentChildChunkerService parentChildChunkerService;
    private final VectorStore vectorStore;

    /**
     * Ingests a document by chunking it into parent-child pairs and storing child chunks.
     *
     * @param request IngestRequest containing content, sourceName, and optional chunk size overrides
     * @return IngestResponse with counts and latency
     */
    public IngestResponse ingest(IngestRequest request) {
        log.debug("ENTER ingest source={} contentLen={}",
                request.getSourceName(), request.getContent().length());

        long start = System.currentTimeMillis();

        try {
            // Delegate chunking to ParentChildChunkerService
            List<Document> childDocs = parentChildChunkerService.chunkIntoChildDocuments(
                    request.getContent(),
                    request.getSourceName(),
                    request.getChildChunkSize(),
                    request.getParentChunkSize()
            );

            if (childDocs.isEmpty()) {
                log.warn("No child documents produced for source={}", request.getSourceName());
                return IngestResponse.builder()
                        .childChunksStored(0)
                        .parentChunksCreated(0)
                        .sourceName(request.getSourceName())
                        .childChunkSizeUsed(resolveChildSize(request.getChildChunkSize()))
                        .parentChunkSizeUsed(resolveParentSize(request.getParentChunkSize()))
                        .latencyMs(System.currentTimeMillis() - start)
                        .timestamp(Instant.now())
                        .build();
            }

            // Count unique parent IDs to report parentChunksCreated
            Set<String> uniqueParentIds = new HashSet<>();
            for (Document doc : childDocs) {
                Object parentId = doc.getMetadata().get("parentId");
                if (parentId != null) {
                    uniqueParentIds.add(parentId.toString());
                }
            }
            int parentCount = uniqueParentIds.size();

            log.debug("Storing {} child documents ({} parent chunks) for source={}",
                    childDocs.size(), parentCount, request.getSourceName());

            // Store all child documents in Qdrant
            vectorStore.add(childDocs);

            long latencyMs = System.currentTimeMillis() - start;
            log.debug("EXIT ingest source={} childChunks={} parents={} latencyMs={}",
                    request.getSourceName(), childDocs.size(), parentCount, latencyMs);

            return IngestResponse.builder()
                    .childChunksStored(childDocs.size())
                    .parentChunksCreated(parentCount)
                    .sourceName(request.getSourceName())
                    .childChunkSizeUsed(resolveChildSize(request.getChildChunkSize()))
                    .parentChunkSizeUsed(resolveParentSize(request.getParentChunkSize()))
                    .latencyMs(latencyMs)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Ingestion failed for source={}: {}", request.getSourceName(), e.getMessage(), e);
            long latencyMs = System.currentTimeMillis() - start;
            return IngestResponse.builder()
                    .childChunksStored(0)
                    .parentChunksCreated(0)
                    .sourceName(request.getSourceName())
                    .childChunkSizeUsed(resolveChildSize(request.getChildChunkSize()))
                    .parentChunkSizeUsed(resolveParentSize(request.getParentChunkSize()))
                    .latencyMs(latencyMs)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private int resolveChildSize(Integer override) {
        return override != null ? override : ragProperties.getChunking().getChildChunkSize();
    }

    private int resolveParentSize(Integer override) {
        return override != null ? override : ragProperties.getChunking().getParentChunkSize();
    }
}
