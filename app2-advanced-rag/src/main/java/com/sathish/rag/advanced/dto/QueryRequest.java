/**
 * QueryRequest.java
 * <p><b>RAG Role:</b> Inbound DTO for RAG query requests. Carries the user's question
 * plus optional per-request overrides for HyDE, MMR, and retrieval parameters.
 * This allows callers to experiment with different configurations without restarting the app.
 * <p><b>Learning Note:</b> Per-request overrides are a powerful pattern for RAG experimentation
 * — you can compare HyDE-on vs HyDE-off, or different MMR lambda values, in the same running
 * app without changing application.yml.
 * <p><b>LEARN:</b> App 1 had a simpler QueryRequest with just a question and topK.
 * App 2 adds hydeEnabled, mmrEnabled, mmrLambda overrides for runtime tuning.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class QueryRequest {

    /** The user's question to answer using RAG. */
    @NotBlank(message = "Question must not be blank")
    private String question;

    /** Optional override for the number of top-k results to retrieve. */
    @Min(1)
    @Max(20)
    private Integer topK;

    /** Optional override to enable/disable HyDE query expansion for this request. */
    private Boolean hydeEnabled;

    /** Optional override to enable/disable MMR re-ranking for this request. */
    private Boolean mmrEnabled;

    /** Optional override for MMR lambda diversity parameter (0.0=max diversity, 1.0=max relevance). */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double mmrLambda;

    /** Optional metadata filters for scoped retrieval (e.g., filter by source document). */
    private Map<String, String> metadataFilter;

    /** Whether to include individual chunk details in the response for debugging/learning. */
    private boolean includeChunks;
}
