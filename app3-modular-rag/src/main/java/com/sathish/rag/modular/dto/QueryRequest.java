/**
 * Request DTO for RAG pipeline query operations.
 *
 * <p>Carries the user's question and optional per-request overrides for retrieval parameters.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.dto;

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
public class QueryRequest {

    @NotBlank(message = "Question must not be blank")
    private String question;

    private Integer topK;

    private Double similarityThreshold;

    private String retrieverOverride;
}
