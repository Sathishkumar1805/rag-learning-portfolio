/**
 * VectorStoreConfig.java
 *
 * <p><b>RAG Role:</b> Infrastructure layer — wires together the EmbeddingModel
 * and in-memory SimpleVectorStore. This is the heart of the Naive RAG architecture:
 * no external vector DB, no persistence across restarts — purely in-JVM cosine similarity.
 *
 * <p><b>Learning Note:</b>
 * SimpleVectorStore keeps all vectors in a ConcurrentHashMap in heap memory.
 * Similarity search is O(n) — fine for hundreds of chunks, too slow for thousands.
 * When you graduate to RAG App #2 (Advanced RAG), swap this bean for Qdrant Cloud
 * (also free tier) to get HNSW indexing and sub-millisecond ANN search.
 *
 * <p><b>Free-Tier Warning:</b>
 * Gemini embedding-004 is free at 1500 req/min. Each document chunk = 1 embedding call.
 * A 50-page PDF split into 512-token chunks ~= 100 calls — well within limits.
 *
 * <p>LEARN: Compare this to VectorStoreConfig in App #2 (Qdrant) and App #9 (Hybrid)
 * where we combine dense + sparse vectors for BM25+cosine hybrid retrieval.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configures the in-memory vector store backed by Gemini embeddings.
 *
 * <p>The store is optionally persisted to a JSON snapshot file on disk so that
 * embedded documents survive a JVM restart during local development. On Render.com
 * free tier (ephemeral filesystem) this persistence does NOT survive a redeploy --
 * re-ingest documents after each deploy.
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    /** Path to the JSON snapshot file for persisting the vector store between restarts. */
    @Value("${rag.vector-store.snapshot-path:./vector-store-snapshot.json}")
    private String snapshotPath;

    /** Whether to auto-load an existing snapshot on startup. */
    @Value("${rag.vector-store.load-snapshot-on-start:true}")
    private boolean loadSnapshotOnStart;

    /**
     * Creates and optionally hydrates the in-memory SimpleVectorStore.
     *
     * <p>Spring AI's SimpleVectorStore is a thin wrapper around a HashMap of
     * documentId -> float[]. The EmbeddingModel is injected by Spring AI's
     * auto-configuration from spring.ai.vertex.ai.gemini.* properties.
     *
     * <p>TODO(learning): After completing App #1, replace this bean with
     * QdrantVectorStore and observe the difference in cold-start latency
     * (Qdrant's HNSW index is pre-built; SimpleVectorStore scans linearly).
     *
     * @param embeddingModel auto-configured Gemini embedding-004 model
     * @return configured VectorStore ready for document insertion and similarity search
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // ── Load existing snapshot if present (avoids re-embedding on restart) ──
        if (loadSnapshotOnStart) {
            File snapshotFile = new File(snapshotPath);
            if (snapshotFile.exists()) {
                log.info("Loading vector store snapshot from: {}", snapshotFile.getAbsolutePath());
                store.load(snapshotFile);
                log.info("Vector store snapshot loaded successfully");
            } else {
                log.info("No snapshot found at {}. Starting with empty vector store.", snapshotPath);
            }
        }

        return store;
    }
}
