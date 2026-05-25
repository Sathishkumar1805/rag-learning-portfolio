/**
 * IngestRequest.java
 * <p><b>RAG Role:</b> Inbound DTO for document ingestion requests. Carries the raw text
 * content to be chunked and embedded, plus optional per-request overrides for chunk sizes
 * to support experimentation without restarting the app.
 * <p><b>Learning Note:</b> Accepting raw text via API (rather than file uploads) keeps
 * the ingestion pipeline simple and testable. In production you would add multipart
 * file upload support and document parsing (PDF, DOCX, HTML).
 * <p><b>LEARN:</b> App 1 had a simpler ingest that stored flat chunks. App 2 ingests
 * parent-child chunk pairs, storing child docs with parentText metadata for context retrieval.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestRequest {

    /** The raw text content to ingest. Must not be blank. */
    @NotBlank(message = "Content must not be blank")
    private String content;

    /** A human-readable name or identifier for the source document. */
    @NotBlank(message = "Source name must not be blank")
    private String sourceName;

    /** Optional override for child chunk size (defaults to rag.chunking.child-chunk-size). */
    private Integer childChunkSize;

    /** Optional override for parent chunk size (defaults to rag.chunking.parent-chunk-size). */
    private Integer parentChunkSize;
}
