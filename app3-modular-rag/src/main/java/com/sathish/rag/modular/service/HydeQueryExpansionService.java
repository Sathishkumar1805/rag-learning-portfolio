package com.sathish.rag.modular.service;

import com.sathish.rag.modular.config.RagProperties;
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
     * Result of a HyDE expansion attempt.
     * {@link #of(String, List)} when expansion succeeded; {@link #disabled()} otherwise.
     */
    public record HydeResult(String hypotheticalPassage, List<Double> embedding, boolean disabled) {

        public boolean isDisabled() {
            return disabled;
        }

        public static HydeResult of(String passage, List<Double> embedding) {
            return new HydeResult(passage, embedding, false);
        }

        public static HydeResult ofDisabled() {
            return new HydeResult("", null, true);
        }
    }

    /**
     * Expands the query using HyDE if enabled in config. Falls back gracefully on error.
     *
     * @param question the user's original question
     * @return HydeResult with hypothetical passage and embedding, or disabled() on error/disabled
     */
    public HydeResult expand(String question) {
        log.debug("ENTER HyDE expand question='{}'", question);

        if (!ragProperties.getHyde().isEnabled()) {
            log.debug("HyDE is disabled, returning disabled result");
            return HydeResult.ofDisabled();
        }

        try {
            String hydeSystemPrompt = ragProperties.getHyde().getSystemPrompt();
            Prompt hydePrompt = new Prompt(List.of(
                    new SystemMessage(hydeSystemPrompt),
                    new UserMessage(question)
            ));

            log.debug("Calling LLM for HyDE hypothesis generation");
            long hydeStart = System.currentTimeMillis();
            String passage = chatModel.call(hydePrompt)
                    .getResult()
                    .getOutput()
                    .getText();
            log.debug("HyDE passage generated in {}ms", System.currentTimeMillis() - hydeStart);

            List<Double> embedding = embedText(passage);
            log.debug("EXIT HyDE expand success embeddingSize={}", embedding.size());
            return HydeResult.of(passage, embedding);

        } catch (Exception e) {
            log.warn("HyDE expansion failed (falling back to direct embedding): {}", e.getMessage());
            return HydeResult.ofDisabled();
        }
    }

    /**
     * Embeds the raw question text (used when HyDE is disabled or failed).
     */
    public List<Double> embedQuestion(String question) {
        return embedText(question);
    }

    private List<Double> embedText(String text) {
        float[] floatArray = embeddingModel.embed(text);
        List<Double> result = new ArrayList<>(floatArray.length);
        for (float v : floatArray) {
            result.add((double) v);
        }
        return result;
    }
}
