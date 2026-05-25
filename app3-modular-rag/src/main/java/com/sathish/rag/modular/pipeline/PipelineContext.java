/**
 * Mutable shared state threaded through all stages of the Modular RAG pipeline.
 *
 * <p>Each {@link RagPipelineStep} reads from this context and writes its outputs back.
 * The context is created fresh for each incoming query by {@link RagPipeline}.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline;

import com.sathish.rag.modular.dto.QueryRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PipelineContext {

    /** The original unmodified query string from the user. */
    private String originalQuery;

    /** The full incoming request DTO. */
    private QueryRequest request;

    /** Query embedding produced by the QueryExpansionStep (may be null if HyDE is disabled). */
    private List<Double> queryEmbedding;

    /** Raw candidate documents returned by the RetrievalStep. */
    private List<Document> candidates = new ArrayList<>();

    /** Final selected chunks after re-ranking. */
    private List<Document> selectedChunks = new ArrayList<>();

    /** Assembled context block (possibly including parent text) for the LLM prompt. */
    private String contextBlock;

    /** The final generated answer from the LLM. */
    private String answer;

    /** Ordered list of per-step execution traces. */
    private List<StepTrace> traces = new ArrayList<>();

    /**
     * Appends a step trace to the execution log.
     *
     * @param trace the trace record to add
     */
    public void addTrace(StepTrace trace) {
        traces.add(trace);
    }
}
