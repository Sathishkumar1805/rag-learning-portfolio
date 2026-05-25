/**
 * IngestResponse.java
 * <p><b>RAG Role:</b> Outbound DTO for document ingestion results. Reports how many
 * parent and child chunks were created and stored, along with the effective chunk sizes
 * used and pipeline latency — useful for tuning and monitoring ingestion quality.
 * <p><b>Learning Note:</b> Reporting parentChunksCreated vs childChunksStored separately
 * helps you understand the parent-child relationship ratio. A typical ratio might be
 * 1 parent: 3-5 children depending on chunk size settings.
 * <p><b>LEARN:</b> App 1 reported only chunksStored. App 2 distinguishes parent and child
 * counts to make the two-level chunking hierarchy visible in the API response.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestResponse {

    /** Number of child chunks stored in the vector store. */
    private int childChunksStored;

    /** Number of parent chunks created (each parent has multiple children). */
    private int parentChunksCreated;

    /** The source document name. */
    private String sourceName;

    /** The effective child chunk size used (from override or config). */
    private int childChunkSizeUsed;

    /** The effective parent chunk size used (from override or config). */
    private int parentChunkSizeUsed;

    /** End-to-end ingestion latency in milliseconds. */
    private long latencyMs;

    /** UTC timestamp when ingestion completed. */
    private Instant timestamp;
}
