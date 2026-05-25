/**
 * HydeQueryExpansionService.java
 * <p><b>RAG Role:</b> Implements HyDE (Hypothetical Document Embeddings) query expansion.
 * Instead of embedding the raw user question (which may use different vocabulary from the
 * stored documents), HyDE first asks the LLM to write a hypothetical answer, then embeds
 * that hypothesis. This bridges the vocabulary gap between questions and answers.
 * <p><b>Learning Note:</b> HyDE was proposed in the paper "Precise Zero-Shot Dense Retrieval
 * without Relevance Labels" (Gao et al., 2022). It works because hypothetical answers
 * share vocabulary with real document answers, making the embedding space alignment tighter.
 * Trade-off: adds one extra LLM call per query, increasing latency and cost.
 * <p><b>LEARN:</b> App 1 embedded the raw question directly. App 2's HyDE embeds a
 * hypothetical answer — compare retrieval recall between the two approaches using
 * the same query against the same Qdrant collection.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.service;

import com.sathish.rag.advanced.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HydeQueryExpansionService {

    private final RagProperties ragProperties;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    /**
     * Result record for a HyDE expansion attempt.
     * Use {@link #of(String, List)} when HyDE produced a hypothesis and embedding.
     * Use {@link #disabled()} when HyDE is disabled or failed.
     */
    public record HydeResult(
            String hypothesis,
            List<Double> embedding,
            boolean hydeUsed) {

        /** Factory: HyDE succeeded — returns hypothesis text and its embedding. */
        public static HydeResult of(String hypothesis, List<Double> embedding) {
            return new HydeResult(hypothesis, embedding, true);
        }

        /** Factory: HyDE was disabled or fell back due to an error. */
        public static HydeResult disabled() {
            return new HydeResult(null, null, false);
        }
    }

    /**
     * Expands the query using HyDE if enabled. Generates a hypothetical answer via the LLM,
     * embeds it, and returns the result. Falls back gracefully on LLM errors.
     *
     * @param question         The user's original question
     * @param hydeEnabledOverride Optional per-request override (null = use config)
     * @return HydeResult with hypothesis + embedding, or disabled() on error/disabled
     */
    public HydeResult expand(String question, Boolean hydeEnabledOverride) {
        log.debug("ENTER HyDE expand question='{}' override={}", question, hydeEnabledOverride);

        boolean enabled = hydeEnabledOverride != null
                ? hydeEnabledOverride
                : ragProperties.getHyde().isEnabled();

        if (!enabled) {
            log.debug("HyDE is disabled, returning disabled result");
            return HydeResult.disabled();
        }

        try {
            String hydeSystemPrompt = ragProperties.getHyde().getSystemPrompt();
            Prompt hydePrompt = new Prompt(List.of(
                    new SystemMessage(hydeSystemPrompt),
                    new UserMessage(question)
            ));

            log.debug("Calling LLM for HyDE hypothesis generation");
            long hydeStart = System.currentTimeMillis();
            String hypothesis = chatModel.call(hydePrompt)
                    .getResult()
                    .getOutput()
                    .getText();
            long hydeLlmMs = System.currentTimeMillis() - hydeStart;
            log.debug("HyDE hypothesis generated in {}ms: '{}'", hydeLlmMs,
                    hypothesis.length() > 100 ? hypothesis.substring(0, 100) + "..." : hypothesis);

            List<Double> embedding = embedText(hypothesis);
            log.debug("EXIT HyDE expand success embeddingSize={}", embedding.size());
            return HydeResult.of(hypothesis, embedding);

        } catch (Exception e) {
            log.warn("HyDE expansion failed (falling back to direct question embedding): {}", e.getMessage());
            return HydeResult.disabled();
        }
    }

    /**
     * Embeds the raw question text directly (used when HyDE is disabled or failed).
     *
     * @param question The user's original question
     * @return Embedding vector as List&lt;Double&gt;
     */
    public List<Double> embedQuestion(String question) {
        log.debug("ENTER embedQuestion question='{}'", question);
        List<Double> embedding = embedText(question);
        log.debug("EXIT embedQuestion embeddingSize={}", embedding.size());
        return embedding;
    }

    /**
     * Internal helper: embeds any text string using the configured EmbeddingModel.
     * Converts float[] returned by Spring AI 1.0 to List&lt;Double&gt;.
     */
    private List<Double> embedText(String text) {
        float[] floatArray = embeddingModel.embed(text);
        List<Double> result = new ArrayList<>(floatArray.length);
        for (float v : floatArray) {
            result.add((double) v);
        }
        return result;
    }
}
