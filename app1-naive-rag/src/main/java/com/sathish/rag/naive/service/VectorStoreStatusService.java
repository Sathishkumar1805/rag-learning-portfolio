/**
 * VectorStoreStatusService.java
 *
 * <p><b>RAG Role:</b> Observability service for the vector store.
 * Provides introspection into what documents have been ingested — critical for
 * debugging "why is my RAG answering wrong?" questions.
 *
 * <p><b>Learning Note:</b>
 * In production RAG systems, observability is non-negotiable. This service is
 * the simplest form: count chunks and list sources. Advanced RAG (App #2) adds
 * retrieval tracing with LangSmith-style span logging.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.service;

import com.sathish.rag.naive.config.RagProperties;
import com.sathish.rag.naive.dto.VectorStoreStatusResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides status information about the in-memory vector store contents.
 */
@Service
public class VectorStoreStatusService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * @param vectorStore   the configured SimpleVectorStore
     * @param ragProperties typed configuration for snapshot path info
     */
    public VectorStoreStatusService(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    /**
     * Gathers a status snapshot of the vector store.
     *
     * <p>Uses a broad similarity search with a wildcard-ish query to enumerate
     * all stored chunks. SimpleVectorStore does not expose an "list all" API,
     * so this is the pragmatic approach for the Naive RAG demo. In production,
     * use a proper DB with a count() query.
     *
     * @return status DTO with chunk counts, document list, and snapshot metadata
     */
    public VectorStoreStatusResponse getStatus() {
        // ── Retrieve a large sample to enumerate stored chunks ─────────────────
        // We use a generic query to get as many stored chunks as possible.
        // This is a SimpleVectorStore limitation — no "list all documents" API.
        // topK=1000 is the practical maximum for a status check.
        List<Document> allChunks = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("document information content")
                        .topK(1000)
                        .similarityThreshold(0.0) // return everything, regardless of score
                        .build()
        );

        // ── Group by source to build the document summary list ────────────────
        Map<String, Long> chunksBySource = allChunks.stream()
                .collect(Collectors.groupingBy(
                        doc -> (String) doc.getMetadata().getOrDefault("source", "Unknown"),
                        Collectors.counting()
                ));

        List<VectorStoreStatusResponse.DocumentSummary> documentSummaries = chunksBySource.entrySet().stream()
                .map(entry -> VectorStoreStatusResponse.DocumentSummary.builder()
                        .source(entry.getKey())
                        .chunkCount(entry.getValue().intValue())
                        .build())
                .collect(Collectors.toList());

        // ── Check snapshot file presence ───────────────────────────────────────
        File snapshotFile = new File(ragProperties.getVectorStore().getSnapshotPath());

        return VectorStoreStatusResponse.builder()
                .totalChunks(allChunks.size())
                .totalDocuments(documentSummaries.size())
                .snapshotExists(snapshotFile.exists())
                .snapshotPath(snapshotFile.getAbsolutePath())
                .documents(documentSummaries)
                .checkedAt(Instant.now())
                .build();
    }
}
