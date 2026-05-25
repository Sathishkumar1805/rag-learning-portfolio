package com.sathish.rag.modular.service;

import com.sathish.rag.modular.dto.QueryRequest;
import com.sathish.rag.modular.dto.QueryResponse;
import com.sathish.rag.modular.pipeline.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    private final RagPipeline ragPipeline;

    /**
     * Executes the modular RAG pipeline for the given query request.
     *
     * @param request query request with question and optional overrides
     * @return fully populated response with answer and pipeline traces
     */
    public QueryResponse query(QueryRequest request) {
        log.debug("ENTER RagQueryService.query question='{}'", request.getQuestion());
        QueryResponse response = ragPipeline.execute(request);
        log.debug("EXIT RagQueryService.query latencyMs={}", response.getLatencyMs());
        return response;
    }
}
