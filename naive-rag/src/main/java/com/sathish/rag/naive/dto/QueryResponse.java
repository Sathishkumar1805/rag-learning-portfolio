/**
 * QueryResponse.java
 *
 * <p><b>RAG Role:</b> DTO returned after executing the RAG query pipeline.
 * Contains the LLM-generated answer plus optional retrieval metadata for debugging.
 *
 * <p><b>Learning Note:</b>
 * The sourceChunks field is your debugging superpower in Naive RAG.
 * When the answer is wrong, inspect the chunks — bad retrieval (wrong chunks) is
 * far more common than bad generation. Fix retrieval first, then fix prompts.
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
 * Response body for {@code POST /api/v1/rag/query}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    /** The original question as received. */
    private String question;

    /** The LLM-generated answer grounded in the retrieved context. */
    private String answer;

    /** Number of chunks retrieved from the vector store. */
    private int chunksRetrieved;

    /**
     * Top-K chunks used to build the context (only populated when
     * QueryRequest.includeSourceChunks = true).
     */
    private List<SourceChunk> sourceChunks;

    /** Wall-clock latency of the full RAG pipeline in milliseconds. */
    private long latencyMs;

    /** Timestamp of this response. */
    private Instant respondedAt;

    // ── Nested DTO ─────────────────────────────────────────────────────────────

    /**
     * A single retrieved document chunk with its similarity score and metadata.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceChunk {

        /** The chunk's text content as stored in the vector store. */
        private String content;

        /** Cosine similarity score [0.0, 1.0]. Higher = more relevant. */
        private double score;

        /** Source label from the original IngestRequest. */
        private String source;

        /** Internal document ID assigned during ingestion. */
        private String documentId;

        /** Zero-based position of this chunk within the original document. */
        private int chunkIndex;
    }
}
