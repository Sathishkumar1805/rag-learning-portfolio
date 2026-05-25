/**
 * Pipeline step that re-ranks candidate documents using Maximal Marginal Relevance (MMR).
 *
 * <p>Reads candidate documents from the {@link PipelineContext}, delegates to the
 * {@link com.sathish.rag.modular.service.MmrRerankerService}, and writes the selected
 * chunks back to the context. If no candidates are available the step is a no-op.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline.steps;

import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import com.sathish.rag.modular.service.MmrRerankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RerankingStep implements RagPipelineStep {

    private final MmrRerankerService mmrRerankerService;

    @Override
    public String getStepName() {
        return "Reranking";
    }

    @Override
    public void execute(PipelineContext context) {
        log.debug("RerankingStep.execute() candidates={}", context.getCandidates().size());

        if (context.getCandidates().isEmpty()) {
            log.debug("RerankingStep: no candidates — skipping");
            context.setSelectedChunks(List.of());
            return;
        }

        List<Double> queryEmbedding = context.getQueryEmbedding();

        MmrRerankerService.MmrResult result =
                mmrRerankerService.rerank(context.getCandidates(), queryEmbedding);

        context.setSelectedChunks(result.selectedDocuments());
        log.debug("RerankingStep: {} chunks selected after MMR", result.selectedDocuments().size());
    }
}
