/**
 * Executes the ordered sequence of {@link RagPipelineStep} beans to answer a query.
 *
 * <p>Creates a fresh {@link PipelineContext} per request, drives each step in the
 * configured order, collects per-step traces, and returns a fully populated
 * {@link com.sathish.rag.modular.dto.QueryResponse}.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline;

import com.sathish.rag.modular.dto.QueryRequest;
import com.sathish.rag.modular.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagPipeline {

    private final List<RagPipelineStep> steps;

    /**
     * Executes all pipeline steps in order and returns the assembled response.
     *
     * @param request the incoming query request
     * @return fully populated query response with answer and traces
     */
    public QueryResponse execute(QueryRequest request) {
        log.debug("RagPipeline.execute() query='{}' steps={}", request.getQuestion(), steps.size());
        long start = System.currentTimeMillis();

        PipelineContext ctx = new PipelineContext();
        ctx.setOriginalQuery(request.getQuestion());
        ctx.setRequest(request);

        for (RagPipelineStep step : steps) {
            long stepStart = System.currentTimeMillis();
            log.debug("RagPipeline: executing step '{}'", step.getStepName());
            try {
                step.execute(ctx);
            } catch (Exception ex) {
                log.error("RagPipeline: step '{}' failed: {}", step.getStepName(), ex.getMessage(), ex);
            }
            ctx.addTrace(new StepTrace(
                    step.getStepName(),
                    System.currentTimeMillis() - stepStart,
                    Map.of()));
        }

        long totalMs = System.currentTimeMillis() - start;
        log.debug("RagPipeline.execute() completed in {}ms", totalMs);

        return QueryResponse.builder()
                .answer(ctx.getAnswer())
                .question(request.getQuestion())
                .pipelineTrace(ctx.getTraces())
                .latencyMs(totalMs)
                .timestamp(Instant.now())
                .build();
    }
}
