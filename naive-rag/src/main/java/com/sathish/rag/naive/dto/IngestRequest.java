/**
 * IngestRequest.java
 *
 * <p><b>RAG Role:</b> DTO for the document ingestion endpoint.
 * Represents a plain-text document submitted via the REST API for chunking,
 * embedding, and storage in the vector store.
 *
 * <p><b>Learning Note:</b>
 * Naive RAG accepts raw text only. Advanced RAG (App #2) will extend this DTO
 * to support file uploads (PDF, DOCX, HTML) via MultipartFile, enabling
 * the full document-loader pipeline with Apache Tika.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/rag/ingest-document}.
 *
 * <p>The {@code content} field is the raw text to be chunked and embedded.
 * The {@code documentId} is an optional client-side identifier for deduplication.
 * The {@code source} is a human-readable label shown in retrieval metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequest {

    /**
     * Optional client-supplied document identifier.
     * If provided, the vector store uses this as the document key.
     * If absent, a UUID is generated automatically.
     */
    @Size(max = 255)
    private String documentId;

    /**
     * Human-readable source label (e.g., "Spring Boot Reference v3.3").
     * Stored as document metadata and returned in query responses so the
     * user knows which source contributed to the answer.
     */
    @NotBlank(message = "source must not be blank")
    @Size(max = 500)
    private String source;

    /**
     * The raw document text to ingest.
     * Will be split into chunks of rag.chunk-size tokens with rag.chunk-overlap overlap.
     */
    @NotBlank(message = "content must not be blank")
    @Size(min = 10, max = 500_000, message = "content must be between 10 and 500,000 characters")
    private String content;
}
