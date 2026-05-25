/**
 * VectorStoreStatusResponse.java
 *
 * <p><b>RAG Role:</b> DTO for the vector store status/health endpoint.
 * Provides visibility into what documents have been ingested — essential for
 * debugging a "I asked a question but got no relevant chunks" failure.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code GET /api/v1/rag/vector-store/status}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorStoreStatusResponse {

    /** Total number of document chunks currently stored. */
    private int totalChunks;

    /** Total number of distinct documents (by source) ingested. */
    private int totalDocuments;

    /** Whether the vector store snapshot file exists on disk. */
    private boolean snapshotExists;

    /** Path to the snapshot file. */
    private String snapshotPath;

    /** Summary of ingested documents. */
    private List<DocumentSummary> documents;

    /** Timestamp when this status was generated. */
    private Instant checkedAt;

    /**
     * Per-document summary shown in the status response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSummary {
        private String source;
        private int chunkCount;
    }
}
