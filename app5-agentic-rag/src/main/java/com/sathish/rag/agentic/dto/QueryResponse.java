package com.sathish.rag.agentic.dto;

import com.sathish.rag.agentic.model.AgentStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private String question;
    private String answer;
    private List<AgentStep> steps;
    private int iterationsUsed;
    private long latencyMs;
}
