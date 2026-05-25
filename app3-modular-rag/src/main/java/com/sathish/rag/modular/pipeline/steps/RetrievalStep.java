/**
 * Pipeline step that retrieves candidate documents using the configured retriever strategy.
 *
 * <p>Reads the original query and optional embedding from the {@link PipelineContext},
 * delegates to the injected {@link com.sathish.rag.modular.retrieval.DocumentRetriever},
 * and writes the candidate documents back to the context for downstream re-ranking.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline.steps;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.dto.QueryRequest;
import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import com.sathish.rag.modular.retrieval.DocumentRetriever;
import com.sathish.rag.modular.retrieval.RetrievalContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalStep implements RagPipelineStep {

    private final DocumentRetriever documentRetriever;
    private final RagProperties ragProperties;

    @Override
    public String getStepName() {
        return "Retrieval";
    }

    @Override
    public void execute(PipelineContext context) {
        log.debug("RetrievalStep.execute() query='{}' retriever='{}'",
                context.getOriginalQuery(), documentRetriever.getName());

        QueryRequest req = context.getRequest();
        int topK = (req != null && req.getTopK() != null)
                ? req.getTopK()
                : ragProperties.getRetrieval().getTopKResults();
        double threshold = (req != null && req.getSimilarityThreshold() != null)
                ? req.getSimilarityThreshold()
                : ragProperties.getRetrieval().getSimilarityThreshold();

        RetrievalContext retrievalCtx = new RetrievalContext(
                context.getOriginalQuery(),
                context.getQueryEmbedding(),
                topK,
                threshold,
                null);

        List<Document> candidates = documentRetriever.retrieve(retrievalCtx);
        context.setCandidates(candidates);
        log.debug("RetrievalStep: {} candidates retrieved", candidates.size());
    }
}
