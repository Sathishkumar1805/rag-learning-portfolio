package com.sathish.rag.agentic.dto;

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
    private long latencyMs;
}
