/**
 * Spring configuration factory that assembles the {@link RagPipeline} bean.
 *
 * <p>Reads {@code rag.pipeline.retriever} from {@link com.sathish.rag.modular.config.RagProperties}
 * to select the active {@link com.sathish.rag.modular.retrieval.DocumentRetriever} strategy
 * (vector | hyde | keyword). Wires all five pipeline steps in canonical order.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.pipeline.steps.*;
import com.sathish.rag.modular.retrieval.DocumentRetriever;
import com.sathish.rag.modular.retrieval.HydeRetriever;
import com.sathish.rag.modular.retrieval.KeywordRetriever;
import com.sathish.rag.modular.retrieval.VectorRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RagPipelineFactory {

    private final RagProperties ragProperties;
    private final VectorRetriever vectorRetriever;
    private final HydeRetriever hydeRetriever;
    private final KeywordRetriever keywordRetriever;
    private final QueryExpansionStep queryExpansionStep;
    private final RerankingStep rerankingStep;
    private final ContextAssemblyStep contextAssemblyStep;
    private final GenerationStep generationStep;

    /**
     * Selects the active DocumentRetriever based on {@code rag.pipeline.retriever} config.
     *
     * @return the primary DocumentRetriever bean for this deployment
     */
    @Bean
    @Primary
    public DocumentRetriever activeDocumentRetriever() {
        String configured = ragProperties.getPipeline().getRetriever();
        log.info("RagPipelineFactory: selecting retriever='{}'", configured);
        return switch (configured.toLowerCase()) {
            case "vector" -> vectorRetriever;
            case "keyword" -> keywordRetriever;
            default -> {
                log.info("RagPipelineFactory: defaulting to HyDE retriever");
                yield hydeRetriever;
            }
        };
    }

    /**
     * Builds the ordered RagPipeline with all five canonical steps.
     *
     * @param retrievalStep the RetrievalStep, which has the active retriever injected
     * @return fully wired RagPipeline
     */
    @Bean
    public RagPipeline ragPipeline(RetrievalStep retrievalStep) {
        List<RagPipelineStep> steps = List.of(
                queryExpansionStep,
                retrievalStep,
                rerankingStep,
                contextAssemblyStep,
                generationStep);
        log.info("RagPipelineFactory: pipeline assembled with {} steps", steps.size());
        return new RagPipeline(steps);
    }
}
