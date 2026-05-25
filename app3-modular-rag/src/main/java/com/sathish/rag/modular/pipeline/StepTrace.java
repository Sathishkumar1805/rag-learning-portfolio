/**
 * Immutable trace record capturing execution metadata for a single pipeline step.
 *
 * <p>Collected during pipeline execution and included in the {@link com.sathish.rag.modular.dto.QueryResponse}
 * to provide observability into step-level timing and any step-specific diagnostic metadata.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline;

import java.util.Map;

/**
 * Per-step execution trace.
 *
 * @param stepName   the name of the pipeline step
 * @param durationMs wall-clock time the step took in milliseconds
 * @param metadata   arbitrary step-specific diagnostic data
 */
public record StepTrace(String stepName, long durationMs, Map<String, Object> metadata) {
}
