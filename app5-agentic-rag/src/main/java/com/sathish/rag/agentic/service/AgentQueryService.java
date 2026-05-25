package com.sathish.rag.agentic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sathish.rag.agentic.config.AgenticRagProperties;
import com.sathish.rag.agentic.dto.QueryRequest;
import com.sathish.rag.agentic.dto.QueryResponse;
import com.sathish.rag.agentic.model.AgentStep;
import com.sathish.rag.agentic.model.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AgentQueryService {

    private final ChatModel chatModel;
    private final RagTools ragTools;
    private final AgenticRagProperties properties;
    private final ObjectMapper objectMapper;

    public AgentQueryService(ChatModel chatModel, RagTools ragTools, AgenticRagProperties properties) {
        this.chatModel = chatModel;
        this.ragTools = ragTools;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public QueryResponse query(QueryRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getQuestion();
        log.debug("Agent query question='{}'", question);

        int maxIterations = properties.getAgent().getMaxIterations();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(properties.getAgent().getSystemPrompt()));
        messages.add(new UserMessage(question));

        List<AgentStep> steps = new ArrayList<>();
        String finalAnswer = null;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.debug("Agent iteration {}/{}", iteration + 1, maxIterations);

            String response = chatModel.call(new Prompt(messages))
                    .getResult().getOutput().getText();
            log.debug("LLM response: {}", response);

            Optional<String> maybeAnswer = parseFinalAnswer(response);
            if (maybeAnswer.isPresent()) {
                finalAnswer = maybeAnswer.get();
                log.debug("Agent found FINAL_ANSWER after {} iterations", iteration + 1);
                break;
            }

            Optional<ToolCall> maybeToolCall = parseToolCall(response);
            if (maybeToolCall.isPresent()) {
                ToolCall toolCall = maybeToolCall.get();
                String observation = dispatchTool(toolCall);
                log.debug("Tool '{}' returned observation length={}", toolCall.toolName(), observation.length());

                steps.add(new AgentStep(toolCall.toolName(), toolCall.argsJson(), observation));

                messages.add(new AssistantMessage(response));
                messages.add(new UserMessage("Tool result: " + observation));
            } else {
                // LLM responded but neither format matched — treat as thinking, continue
                messages.add(new AssistantMessage(response));
                messages.add(new UserMessage("Please continue. Use TOOL_CALL: or FINAL_ANSWER:."));
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "Unable to answer within " + maxIterations + " iterations.";
        }

        long latency = System.currentTimeMillis() - start;
        return QueryResponse.builder()
                .question(question)
                .answer(finalAnswer)
                .steps(steps)
                .iterationsUsed(steps.size())
                .latencyMs(latency)
                .build();
    }

    private Optional<String> parseFinalAnswer(String response) {
        int idx = response.indexOf("FINAL_ANSWER:");
        if (idx == -1) return Optional.empty();
        return Optional.of(response.substring(idx + "FINAL_ANSWER:".length()).strip());
    }

    private Optional<ToolCall> parseToolCall(String response) {
        int idx = response.indexOf("TOOL_CALL:");
        if (idx == -1) return Optional.empty();

        String after = response.substring(idx + "TOOL_CALL:".length()).strip();
        int parenOpen = after.indexOf('(');
        if (parenOpen == -1) return Optional.empty();

        String toolName = after.substring(0, parenOpen).strip();
        String rest = after.substring(parenOpen + 1);
        int parenClose = rest.lastIndexOf(')');
        if (parenClose == -1) return Optional.empty();

        String argsJson = rest.substring(0, parenClose).strip();
        return Optional.of(new ToolCall(toolName, argsJson));
    }

    private String dispatchTool(ToolCall toolCall) {
        try {
            JsonNode args = objectMapper.readTree(toolCall.argsJson().isEmpty() ? "{}" : toolCall.argsJson());
            int defaultTopK = properties.getAgent().getSearchTopK();
            return switch (toolCall.toolName()) {
                case "searchDocuments" -> {
                    String query = args.path("query").asText();
                    int topK = args.path("topK").asInt(defaultTopK);
                    yield ragTools.searchDocuments(query, topK);
                }
                case "searchDocumentsBySource" -> {
                    String source = args.path("source").asText();
                    int topK = args.path("topK").asInt(defaultTopK);
                    yield ragTools.searchDocumentsBySource(source, topK);
                }
                case "listAvailableDocuments" -> ragTools.listAvailableDocuments();
                case "decomposeQuestion" -> {
                    String question = args.path("question").asText();
                    yield ragTools.decomposeQuestion(question);
                }
                default -> "Unknown tool: " + toolCall.toolName();
            };
        } catch (Exception e) {
            log.warn("Tool dispatch failed for '{}': {}", toolCall.toolName(), e.getMessage());
            return "Tool execution failed: " + e.getMessage();
        }
    }
}
