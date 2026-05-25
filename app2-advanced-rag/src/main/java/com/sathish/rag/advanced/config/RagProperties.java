/**
 * RagProperties.java
 * <p><b>RAG Role:</b> Type-safe configuration container for all RAG-related settings.
 * Binds YAML properties under the "rag" prefix to strongly-typed Java objects,
 * providing IDE auto-completion, validation, and refactoring support.
 * <p><b>Learning Note:</b> @ConfigurationProperties with nested static classes is the
 * Spring Boot best practice for hierarchical configuration. Combined with @Validated,
 * it fails fast at startup if required properties are missing — much better than
 * discovering null values at runtime.
 * <p><b>LEARN:</b> Compare with App 1 — as RAG complexity grows, centralised properties
 * make it easy to tune chunking, retrieval, and generation parameters without code changes.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "rag")
@Validated
@Getter
@Setter
public class RagProperties {

    @Valid
    private QdrantProperties qdrant = new QdrantProperties();

    @Valid
    private ChunkingProperties chunking = new ChunkingProperties();

    @Valid
    private RetrievalProperties retrieval = new RetrievalProperties();

    @Valid
    private GenerationProperties generation = new GenerationProperties();

    @Valid
    private HydeProperties hyde = new HydeProperties();

    @Valid
    private MmrProperties mmr = new MmrProperties();

    @Getter
    @Setter
    public static class QdrantProperties {
        @NotBlank
        private String collectionName = "advanced-rag-docs";
        private int vectorDimension = 768;
    }

    @Getter
    @Setter
    public static class ChunkingProperties {
        private int childChunkSize = 256;
        private int childChunkOverlap = 40;
        private int parentChunkSize = 1024;
        private int parentChunkOverlap = 100;
    }

    @Getter
    @Setter
    public static class RetrievalProperties {
        private int topKResults = 6;
        private double similarityThreshold = 0.65;
    }

    @Getter
    @Setter
    public static class GenerationProperties {
        @NotBlank
        private String systemPrompt = "You are a precise, helpful assistant. Answer using ONLY the provided context. If the context does not contain enough information, say so. Do not fabricate.";
        private boolean includeParentContext = true;
    }

    @Getter
    @Setter
    public static class HydeProperties {
        private boolean enabled = true;
        private String systemPrompt = "You are a technical document writer. Given a question, write a concise hypothetical answer paragraph (2-3 sentences) as if it appeared in a technical document. Do not include preamble. Output only the paragraph.";
    }

    @Getter
    @Setter
    public static class MmrProperties {
        private boolean enabled = true;
        private double lambda = 0.6;
        private int candidatePoolSize = 20;
        private int finalTopK = 4;
    }
}
