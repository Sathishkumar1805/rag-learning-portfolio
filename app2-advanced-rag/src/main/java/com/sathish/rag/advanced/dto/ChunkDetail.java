/**
 * ChunkDetail.java
 * <p><b>RAG Role:</b> Represents a single retrieved chunk in the query response, carrying
 * both the child text (used for retrieval) and the parent text (used for LLM context).
 * Also records whether MMR selected this chunk or if it was filtered out.
 * <p><b>Learning Note:</b> Exposing chunk-level details in the API response is a key
 * observability feature — it lets you compare what was retrieved vs what was selected,
 * making it easy to understand and tune the retrieval pipeline.
 * <p><b>LEARN:</b> App 1 returned only the final answer; App 2 exposes the full
 * retrieval trace (child/parent texts, MMR flag) to enable debugging and learning.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkDetail {

    /** The small child chunk text that was matched during similarity search. */
    private String childText;

    /** The full parent chunk text included for richer LLM context (if enabled). */
    private String parentText;

    /** Cosine similarity score returned by the vector store. */
    private double similarityScore;

    /** Source document name or identifier. */
    private String source;

    /** Index of this chunk within the source document. */
    private int chunkIndex;

    /** Whether MMR selected this chunk for inclusion in the LLM prompt. */
    private boolean selectedByMmr;

    /** Full metadata map from the vector store Document. */
    private Map<String, Object> metadata;
}
