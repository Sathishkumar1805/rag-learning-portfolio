/**
 * Configuration properties for the Modular RAG application.
 *
 * <p>Binds all {@code rag.*} properties from application.yml into typed nested classes.
 * Includes Qdrant, chunking, retrieval, generation, pipeline strategy, HyDE, and MMR settings.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "rag")
@Validated
@Getter
@Setter
public class RagProperties {

    @NotNull
    private QdrantProperties qdrant = new QdrantProperties();

    @NotNull
    private ChunkingProperties chunking = new ChunkingProperties();

    @NotNull
    private RetrievalProperties retrieval = new RetrievalProperties();

    @NotNull
    private GenerationProperties generation = new GenerationProperties();

    @NotNull
    private PipelineProperties pipeline = new PipelineProperties();

    @NotNull
    private HydeProperties hyde = new HydeProperties();

    @NotNull
    private MmrProperties mmr = new MmrProperties();

    @Getter
    @Setter
    public static class QdrantProperties {

        @NotBlank
        private String collectionName = "modular-rag-docs";

        @Positive
        private int vectorDimension = 768;
    }

    @Getter
    @Setter
    public static class ChunkingProperties {

        @Positive
        private int childChunkSize = 256;

        @PositiveOrZero
        private int childChunkOverlap = 40;

        @Positive
        private int parentChunkSize = 1024;

        @PositiveOrZero
        private int parentChunkOverlap = 100;
    }

    @Getter
    @Setter
    public static class RetrievalProperties {

        @Positive
        private int topKResults = 6;

        private double similarityThreshold = 0.65;
    }

    @Getter
    @Setter
    public static class GenerationProperties {

        @NotBlank
        private String systemPrompt = "You are a precise, helpful assistant. Answer using ONLY the provided context.";

        private boolean includeParentContext = true;
    }

    @Getter
    @Setter
    public static class PipelineProperties {

        @NotBlank
        private String retriever = "hyde";

        @NotBlank
        private String reranker = "mmr";
    }

    @Getter
    @Setter
    public static class HydeProperties {

        private boolean enabled = true;

        @NotBlank
        private String systemPrompt = "You are a technical document writer. Given a question, write a concise hypothetical answer paragraph as if it appeared in a technical document.";
    }

    @Getter
    @Setter
    public static class MmrProperties {

        private boolean enabled = true;

        private double lambda = 0.6;

        @Positive
        private int candidatePoolSize = 20;

        @Positive
        private int finalTopK = 4;
    }
}
