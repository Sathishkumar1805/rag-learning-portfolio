/**
 * Response DTO returned by the RAG pipeline after processing a query.
 *
 * <p>Contains the generated answer, the original question, pipeline execution traces,
 * total latency, and timestamp. The {@code pipelineTrace} field exposes step-level
 * timing for observability and debugging.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sathish.rag.modular.pipeline.StepTrace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResponse {

    private String answer;

    private String question;

    private List<StepTrace> pipelineTrace;

    private long latencyMs;

    private Instant timestamp;
}
