package com.sathish.rag.graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private String source;
    private int chunksStored;
    private int entitiesExtracted;
    private int relationshipsExtracted;
    private long latencyMs;
}
