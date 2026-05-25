/**
 * Strategy interface for a single step in the Modular RAG pipeline.
 *
 * <p>Each step reads from and writes to a shared {@link PipelineContext}, allowing
 * steps to be composed, reordered, or replaced without modifying other steps.
 * Implementations are Spring beans discovered and ordered by the {@code RagPipelineFactory}.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline;

/**
 * A single composable step in the RAG processing pipeline.
 */
public interface RagPipelineStep {

    /**
     * Executes this step, reading from and mutating the shared pipeline context.
     *
     * @param context mutable shared state passed between all pipeline steps
     */
    void execute(PipelineContext context);

    /**
     * Returns the human-readable name of this step for tracing purposes.
     *
     * @return step name
     */
    String getStepName();
}
