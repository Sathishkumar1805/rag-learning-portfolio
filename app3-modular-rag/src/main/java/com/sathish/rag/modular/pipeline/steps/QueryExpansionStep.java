/**
 * Pipeline step that optionally expands the user query using HyDE.
 *
 * <p>When HyDE is enabled, this step calls {@link com.sathish.rag.modular.service.HydeQueryExpansionService}
 * to generate a hypothetical document passage and its embedding, which are stored in the
 * {@link com.sathish.rag.modular.pipeline.PipelineContext} for downstream steps.
 * When HyDE is disabled the query embedding is produced via plain embedding lookup.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline.steps;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import com.sathish.rag.modular.service.HydeQueryExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryExpansionStep implements RagPipelineStep {

    private final HydeQueryExpansionService hydeQueryExpansionService;
    private final RagProperties ragProperties;

    @Override
    public String getStepName() {
        return "QueryExpansion";
    }

    @Override
    public void execute(PipelineContext context) {
        log.debug("QueryExpansionStep.execute() query='{}'", context.getOriginalQuery());

        if (ragProperties.getHyde().isEnabled()) {
            HydeQueryExpansionService.HydeResult result =
                    hydeQueryExpansionService.expand(context.getOriginalQuery());
            if (!result.isDisabled() && result.embedding() != null) {
                context.setQueryEmbedding(result.embedding());
                log.debug("QueryExpansionStep: HyDE embedding set, size={}",
                        result.embedding().size());
            } else {
                embedDirectly(context);
            }
        } else {
            embedDirectly(context);
        }
    }

    private void embedDirectly(PipelineContext context) {
        List<Double> embedding = hydeQueryExpansionService.embedQuestion(context.getOriginalQuery());
        context.setQueryEmbedding(embedding);
        log.debug("QueryExpansionStep: direct embedding set, size={}",
                embedding != null ? embedding.size() : 0);
    }
}
