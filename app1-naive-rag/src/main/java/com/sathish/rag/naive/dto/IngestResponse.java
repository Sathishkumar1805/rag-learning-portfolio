/**
 * IngestResponse.java
 *
 * <p><b>RAG Role:</b> DTO returned after a successful document ingestion.
 * Provides feedback to the caller about how the document was chunked and stored.
 *
 * <p><b>Learning Note:</b>
 * The chunksCreated count is educational — it reveals how the splitter divided
 * your document. Low count = large chunks = dense context. High count = small chunks
 * = sparse context. There is no universally "right" value; it depends on the LLM's
 * context window and the nature of the document.
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

/**
 * Response body for {@code POST /api/v1/rag/ingest-document}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {

    /** Echoes back the document ID (generated or client-supplied). */
    private String documentId;

    /** Human-readable source label as stored in vector metadata. */
    private String source;

    /** Number of chunks created from the document after splitting. */
    private int chunksCreated;

    /** Chunk size used during this ingestion (tokens). */
    private int chunkSize;

    /** Chunk overlap used during this ingestion (tokens). */
    private int chunkOverlap;

    /** Timestamp when ingestion completed. */
    private Instant ingestedAt;

    /** Short confirmation message. */
    private String message;
}
