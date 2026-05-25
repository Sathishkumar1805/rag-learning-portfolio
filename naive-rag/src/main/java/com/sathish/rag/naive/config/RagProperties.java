/**
 * RagProperties.java
 *
 * <p><b>RAG Role:</b> Typed configuration holder for all Naive RAG tuning parameters.
 * Using @ConfigurationProperties is preferred over scattered @Value annotations because
 * it provides IDE auto-completion, type safety, and a single source of truth.
 *
 * <p><b>Learning Note:</b>
 * These properties directly control RAG quality:
 * - chunkSize: too large = noisy context; too small = missing context
 * - chunkOverlap: prevents sentence truncation at chunk boundaries
 * - topKResults: more chunks = richer context but higher token cost
 * Experiment with these values using the /api/v1/rag/query endpoint and observe
 * how answer quality changes. Keep notes — this builds RAG intuition fast.
 *
 * <p>TODO(learning): Build a /api/v1/rag/tune endpoint that accepts these params
 * at query time (bypassing defaults) so you can A/B test without restarting the app.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binds all {@code rag.*} properties from application.yml into a strongly-typed POJO.
 *
 * <p>Example application.yml snippet:
 * <pre>
 * rag:
 *   chunk-size: 512
 *   chunk-overlap: 50
 *   vector-store:
 *     top-k-results: 4
 *     snapshot-path: ./vector-store-snapshot.json
 * </pre>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    // ── Document Chunking ──────────────────────────────────────────────────────

    /**
     * Target token size for each document chunk.
     * Naive RAG uses fixed-size chunking (no semantic boundary detection).
     * LEARN: Advanced RAG (App #2) replaces this with semantic chunking.
     */
    @Min(128)
    @Max(2048)
    private int chunkSize = 512;

    /**
     * Number of tokens shared between consecutive chunks.
     * Overlap prevents context loss when a sentence spans a chunk boundary.
     */
    @Min(0)
    @Max(512)
    private int chunkOverlap = 50;

    // ── Vector Store ───────────────────────────────────────────────────────────

    private VectorStoreProperties vectorStore = new VectorStoreProperties();

    // ── LLM Prompt ────────────────────────────────────────────────────────────

    /**
     * System prompt template injected into every RAG query.
     * Use {context} and {question} placeholders — Spring AI's PromptTemplate
     * resolves them at runtime.
     */
    @NotBlank
    private String systemPromptTemplate = """
            You are a helpful assistant that answers questions strictly based on the
            provided context. If the context does not contain enough information to
            answer the question, say "I don't have enough information to answer that."
            Do not use prior knowledge outside of the context.
            
            Context:
            {context}
            
            Question: {question}
            """;

    // ── Nested config classes ──────────────────────────────────────────────────

    /**
     * Properties scoped to the vector store subsystem.
     */
    @Data
    public static class VectorStoreProperties {

        /** Number of nearest-neighbor chunks to retrieve per query. */
        @Min(1)
        @Max(20)
        private int topKResults = 4;

        /** Minimum cosine similarity score (0.0–1.0) for a chunk to be included. */
        private double similarityThreshold = 0.0;

        /** File path for SimpleVectorStore JSON snapshot persistence. */
        private String snapshotPath = "./vector-store-snapshot.json";

        /** Whether to persist the vector store snapshot after each ingest. */
        private boolean persistAfterIngest = true;

        /** Whether to load an existing snapshot on application startup. */
        private boolean loadSnapshotOnStart = true;
    }
}
