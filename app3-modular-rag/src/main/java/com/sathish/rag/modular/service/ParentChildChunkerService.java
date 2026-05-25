package com.sathish.rag.modular.service;

import com.sathish.rag.modular.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentChildChunkerService {

    private final RagProperties ragProperties;

    /**
     * Splits text into child Documents with parent text embedded in each child's metadata.
     *
     * @param text       raw text content to chunk
     * @param sourceName human-readable source identifier
     * @return list of child Documents enriched with parent metadata
     */
    public List<Document> chunkIntoChildDocuments(String text, String sourceName) {
        log.debug("ENTER chunkIntoChildDocuments source={} textLen={}", sourceName, text.length());

        RagProperties.ChunkingProperties cfg = ragProperties.getChunking();
        int parentSize = cfg.getParentChunkSize();
        int parentOverlap = cfg.getParentChunkOverlap();
        int childSize = cfg.getChildChunkSize();
        int childOverlap = cfg.getChildChunkOverlap();

        TokenTextSplitter parentSplitter = new TokenTextSplitter(
                parentSize, parentOverlap, 5, 10000, true);

        List<Document> parentDocs = parentSplitter.apply(List.of(new Document(text)));
        log.debug("Created {} parent chunks from source={}", parentDocs.size(), sourceName);

        List<Document> allChildDocs = new ArrayList<>();
        int globalChildIndex = 0;

        for (int parentIndex = 0; parentIndex < parentDocs.size(); parentIndex++) {
            String parentText = parentDocs.get(parentIndex).getText();
            String parentId = UUID.randomUUID().toString();

            TokenTextSplitter childSplitter = new TokenTextSplitter(
                    childSize, childOverlap, 5, 10000, true);

            List<Document> childDocs = childSplitter.apply(List.of(new Document(parentText)));

            for (int childIndex = 0; childIndex < childDocs.size(); childIndex++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", sourceName);
                metadata.put("parentId", parentId);
                metadata.put("parentIndex", parentIndex);
                metadata.put("parentText", parentText);
                metadata.put("childIndex", childIndex);
                metadata.put("globalChildIndex", globalChildIndex);

                allChildDocs.add(new Document(childDocs.get(childIndex).getText(), metadata));
                globalChildIndex++;
            }
        }

        log.debug("EXIT chunkIntoChildDocuments source={} parents={} children={}",
                sourceName, parentDocs.size(), allChildDocs.size());
        return allChildDocs;
    }
}
