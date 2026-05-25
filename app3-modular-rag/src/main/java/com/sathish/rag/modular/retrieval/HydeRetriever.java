/**
 * HyDE (Hypothetical Document Embeddings) retriever implementation.
 *
 * <p>Expands the user query into a hypothetical document passage using an LLM,
 * then uses that passage for vector similarity search. This typically improves
 * recall for complex or abstract questions compared to plain vector retrieval.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.retrieval;

import com.sathish.rag.modular.service.HydeQueryExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HydeRetriever implements DocumentRetriever {

    private final VectorRetriever vectorRetriever;
    private final HydeQueryExpansionService hydeQueryExpansionService;

    @Override
    public String getName() {
        return "hyde";
    }

    /**
     * Expands the query via HyDE and delegates the expanded query to the VectorRetriever.
     *
     * @param ctx retrieval context with the original user query
     * @return list of documents retrieved using the hypothetical passage
     */
    @Override
    public List<Document> retrieve(RetrievalContext ctx) {
        log.debug("HydeRetriever.retrieve() query='{}'", ctx.query());

        HydeQueryExpansionService.HydeResult hydeResult =
                hydeQueryExpansionService.expand(ctx.query());

        if (hydeResult.isDisabled() || hydeResult.hypotheticalPassage().isBlank()) {
            log.debug("HyDE disabled or empty passage — falling back to original query");
            return vectorRetriever.retrieve(ctx);
        }

        log.debug("HyDE expanded passage (first 100 chars): '{}'",
                hydeResult.hypotheticalPassage().substring(
                        0, Math.min(100, hydeResult.hypotheticalPassage().length())));

        RetrievalContext expandedCtx = new RetrievalContext(
                hydeResult.hypotheticalPassage(),
                hydeResult.embedding(),
                ctx.topK(),
                ctx.threshold(),
                ctx.metadataFilter());

        List<Document> results = vectorRetriever.retrieve(expandedCtx);
        log.debug("HydeRetriever found {} documents", results.size());
        return results;
    }
}
