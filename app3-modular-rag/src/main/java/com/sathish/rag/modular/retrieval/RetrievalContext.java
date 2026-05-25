/**
 * Immutable value object carrying all parameters needed by a {@link DocumentRetriever}.
 *
 * <p>Passed through the pipeline from the RetrievalStep to the chosen retriever strategy.
 * The {@code queryEmbedding} may be null when using text-based strategies such as keyword search.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.retrieval;

import java.util.List;
import java.util.Map;

/**
 * Record encapsulating all retrieval parameters for a single query.
 *
 * @param query           the raw (or expanded) query string
 * @param queryEmbedding  pre-computed query embedding vector, may be null
 * @param topK            maximum number of results to return
 * @param threshold       minimum similarity score threshold
 * @param metadataFilter  optional metadata key/value filters
 */
public record RetrievalContext(
        String query,
        List<Double> queryEmbedding,
        int topK,
        double threshold,
        Map<String, String> metadataFilter) {
}
