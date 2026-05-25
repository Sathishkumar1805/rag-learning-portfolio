/**
 * NaiveRagApplication.java
 *
 * <p><b>RAG Role:</b> Application entry point for RAG App #1 — Naive RAG.
 * Bootstraps the Spring Boot context, triggers eager vector-store initialization,
 * and exposes the REST API for document ingestion and query.
 *
 * <p><b>Learning Note:</b>
 * Naive RAG is the simplest RAG pattern: chunk → embed → store → retrieve → augment → generate.
 * There is no re-ranking, no query rewriting, no hybrid search — just raw cosine similarity.
 * Master this baseline before advancing to RAG App #2 (Advanced RAG) where we add
 * query expansion, MMR re-ranking, and metadata filters.
 *
 * <p><b>Pipeline Summary:</b>
 * <pre>
 *  Document → Splitter → EmbeddingModel → SimpleVectorStore
 *                                                  ↓ (top-K similarity search)
 *  User Query → EmbeddingModel → VectorSearch → PromptTemplate → ChatModel → Answer
 * </pre>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NaiveRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(NaiveRagApplication.class, args);
    }
}
