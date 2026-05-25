/**
 * Pipeline step that generates the final answer using the configured LLM.
 *
 * <p>Reads the assembled context block from the {@link PipelineContext} and calls the
 * Spring AI {@link org.springframework.ai.chat.model.ChatModel} with the system prompt
 * and context-grounded user message. If no candidates are available, a fallback message
 * is set without invoking the LLM.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.pipeline.steps;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationStep implements RagPipelineStep {

    private final ChatModel chatModel;
    private final RagProperties ragProperties;

    @Override
    public String getStepName() {
        return "Generation";
    }

    @Override
    public void execute(PipelineContext context) {
        log.debug("GenerationStep.execute() candidates={}", context.getCandidates().size());

        if (context.getCandidates().isEmpty()) {
            log.debug("GenerationStep: no candidates — returning fallback answer");
            context.setAnswer("I could not find relevant information in the knowledge base to answer your question.");
            return;
        }

        String contextBlock = context.getContextBlock();
        if (contextBlock == null || contextBlock.isBlank()) {
            log.debug("GenerationStep: empty context block — returning fallback answer");
            context.setAnswer("I could not find relevant information in the knowledge base to answer your question.");
            return;
        }

        String systemPrompt = ragProperties.getGeneration().getSystemPrompt();
        String userContent = buildUserMessage(context.getOriginalQuery(), contextBlock);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userContent)));

        log.debug("GenerationStep: calling LLM with context length={}", contextBlock.length());

        String answer = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        context.setAnswer(answer);
        log.debug("GenerationStep: answer generated, length={}", answer != null ? answer.length() : 0);
    }

    private String buildUserMessage(String question, String contextBlock) {
        return "Context:\n" + contextBlock + "\n\nQuestion: " + question + "\n\nAnswer:";
    }
}
