/**
 * Vector similarity retriever implementation using Qdrant.
 *
 * <p>Performs standard nearest-neighbour search in the vector store using the query text
 * with configurable topK and similarity threshold. This is the simplest retrieval strategy
 * and serves as the base for other strategies such as HyDE.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;

    @Override
    public String getName() {
        return "vector";
    }

    /**
     * Performs similarity search in the vector store using the context query.
     *
     * @param ctx retrieval parameters including query text, topK and threshold
     * @return list of similar documents
     */
    @Override
    public List<Document> retrieve(RetrievalContext ctx) {
        log.debug("VectorRetriever.retrieve() query='{}' topK={} threshold={}",
                ctx.query(), ctx.topK(), ctx.threshold());

        SearchRequest request = SearchRequest.builder()
                .query(ctx.query())
                .topK(ctx.topK())
                .similarityThreshold(ctx.threshold())
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("VectorRetriever found {} documents", results.size());
        return results;
    }
}
