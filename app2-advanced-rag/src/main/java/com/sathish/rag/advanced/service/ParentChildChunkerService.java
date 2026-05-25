/**
 * ParentChildChunkerService.java
 * <p><b>RAG Role:</b> Implements parent-child chunking — a two-level document segmentation
 * strategy where large parent chunks (for rich LLM context) are subdivided into small
 * child chunks (for precise semantic retrieval). Only child chunks are stored in the
 * vector store, but each carries the full parent text in its metadata.
 * <p><b>Learning Note:</b> Parent-child chunking solves the "chunk size dilemma": small
 * chunks have precise embeddings but lack context; large chunks have rich context but
 * imprecise embeddings. By embedding small, retrieving small, then expanding to parent
 * context for the LLM, you get the best of both worlds.
 * <p><b>LEARN:</b> App 1 used flat chunking (TokenTextSplitter once). App 2 applies
 * TokenTextSplitter twice — first at parent granularity, then at child granularity
 * within each parent — to build the two-level hierarchy.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.service;

import com.sathish.rag.advanced.config.RagProperties;
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
     * Splits the given text into child Documents using a two-level parent-child hierarchy.
     * Each child Document's metadata contains the full parent text for context retrieval.
     *
     * @param text               Raw text content to chunk
     * @param sourceName         Human-readable source identifier stored in metadata
     * @param childSizeOverride  Optional override for child chunk size (null = use config)
     * @param parentSizeOverride Optional override for parent chunk size (null = use config)
     * @return List of child Documents, each enriched with parent metadata
     */
    public List<Document> chunkIntoChildDocuments(
            String text,
            String sourceName,
            Integer childSizeOverride,
            Integer parentSizeOverride) {

        log.debug("ENTER chunkIntoChildDocuments source={} textLen={} childOverride={} parentOverride={}",
                sourceName, text.length(), childSizeOverride, parentSizeOverride);

        long start = System.currentTimeMillis();

        RagProperties.ChunkingProperties chunkCfg = ragProperties.getChunking();
        int parentSize = parentSizeOverride != null ? parentSizeOverride : chunkCfg.getParentChunkSize();
        int parentOverlap = chunkCfg.getParentChunkOverlap();
        int childSize = childSizeOverride != null ? childSizeOverride : chunkCfg.getChildChunkSize();
        int childOverlap = chunkCfg.getChildChunkOverlap();

        log.debug("Using parentSize={} parentOverlap={} childSize={} childOverlap={}",
                parentSize, parentOverlap, childSize, childOverlap);

        // Step 1: Split into parent chunks
        TokenTextSplitter parentSplitter = new TokenTextSplitter(
                parentSize, parentOverlap, 5, 10000, true);

        List<Document> parentDocs = parentSplitter.apply(List.of(new Document(text)));
        log.debug("Created {} parent chunks from source={}", parentDocs.size(), sourceName);

        // Step 2: For each parent chunk, create child chunks with parent metadata
        List<Document> allChildDocs = new ArrayList<>();
        int globalChildIndex = 0;

        for (int parentIndex = 0; parentIndex < parentDocs.size(); parentIndex++) {
            Document parentDoc = parentDocs.get(parentIndex);
            String parentText = parentDoc.getText();
            String parentId = UUID.randomUUID().toString();

            // Split parent into children
            TokenTextSplitter childSplitter = new TokenTextSplitter(
                    childSize, childOverlap, 5, 10000, true);

            List<Document> childDocs = childSplitter.apply(List.of(new Document(parentText)));

            for (int childIndex = 0; childIndex < childDocs.size(); childIndex++) {
                Document childDoc = childDocs.get(childIndex);
                String childText = childDoc.getText();

                // Build enriched metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", sourceName);
                metadata.put("parentId", parentId);
                metadata.put("parentIndex", parentIndex);
                metadata.put("parentText", parentText);
                metadata.put("childIndex", childIndex);
                metadata.put("globalChildIndex", globalChildIndex);

                Document enrichedChild = new Document(childText, metadata);
                allChildDocs.add(enrichedChild);
                globalChildIndex++;
            }

            log.debug("Parent {} (id={}) produced {} children",
                    parentIndex, parentId, childDocs.size());
        }

        long latencyMs = System.currentTimeMillis() - start;
        log.debug("EXIT chunkIntoChildDocuments source={} parents={} children={} latencyMs={}",
                sourceName, parentDocs.size(), allChildDocs.size(), latencyMs);

        return allChildDocs;
    }
}
