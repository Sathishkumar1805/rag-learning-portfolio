/**
 * Strategy interface for document retrieval in the Modular RAG pipeline.
 *
 * <p>Implementations include vector similarity retrieval, HyDE-expanded retrieval,
 * and keyword-based retrieval. The active strategy is selected via application.yml
 * without any code changes.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.retrieval;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Contract for all document retriever strategies.
 */
public interface DocumentRetriever {

    /**
     * Retrieve documents relevant to the given context.
     *
     * @param context encapsulates query, embedding, topK, threshold and metadata filters
     * @return ordered list of relevant documents
     */
    List<Document> retrieve(RetrievalContext context);

    /**
     * Returns the unique name of this retriever strategy.
     *
     * @return strategy name (e.g. "vector", "hyde", "keyword")
     */
    String getName();
}
