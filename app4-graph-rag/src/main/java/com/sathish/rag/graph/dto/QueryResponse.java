package com.sathish.rag.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    private String question;
    private String answer;
    private int vectorHits;
    private int graphEntityHits;
    private int graphRelationshipHits;
    private long latencyMs;
}
