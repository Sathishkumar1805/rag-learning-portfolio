/**
 * QueryRequest.java
 *
 * <p><b>RAG Role:</b> DTO for the RAG query endpoint.
 * Carries the user's natural-language question to the retrieval-augmented
 * generation pipeline.
 *
 * <p><b>Learning Note:</b>
 * In Naive RAG, the question is used AS-IS for vector similarity search.
 * Advanced RAG (App #2) adds query rewriting (HyDE — Hypothetical Document
 * Embeddings) where the LLM first generates a hypothetical answer, then
 * embeds THAT for retrieval — dramatically improving recall on vague questions.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/rag/query}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    /**
     * The user's question in natural language.
     * This is embedded and used for cosine-similarity search against stored chunks.
     */
    @NotBlank(message = "question must not be blank")
    @Size(min = 3, max = 2000, message = "question must be between 3 and 2000 characters")
    private String question;

    /**
     * Override the global top-K setting for this specific query.
     * If null, the value from rag.vector-store.top-k-results is used.
     *
     * <p>TODO(learning): Try topK=1 vs topK=10 on the same question.
     * Observe how answer precision vs recall trade off.
     */
    @Min(1)
    @Max(20)
    private Integer topK;

    /**
     * Whether to include the retrieved source chunks in the response.
     * Useful for debugging retrieval quality — set to true during development.
     */
    @Builder.Default
    private boolean includeSourceChunks = false;
}
