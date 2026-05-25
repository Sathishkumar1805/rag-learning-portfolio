package com.sathish.rag.agentic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class AgenticRagProperties {

    private Agent agent = new Agent();
    private Chunking chunking = new Chunking();

    @Data
    public static class Agent {
        private int maxIterations = 5;
        private int searchTopK = 5;
        private String systemPrompt =
            "You are a helpful assistant that answers questions using document retrieval tools.\n\n" +
            "Available tools:\n" +
            "- searchDocuments(query, topK): Search documents by semantic similarity\n" +
            "- searchDocumentsBySource(source, topK): Search documents from a specific source\n" +
            "- listAvailableDocuments(): List all available document sources\n" +
            "- decomposeQuestion(question): Break a complex question into simpler sub-questions\n\n" +
            "To use a tool, respond with exactly:\n" +
            "TOOL_CALL: toolName({\"param\": \"value\"})\n\n" +
            "When you have enough information to answer, respond with exactly:\n" +
            "FINAL_ANSWER: your complete answer here\n\n" +
            "Think step by step. Use tools to gather information before answering.";
    }

    @Data
    public static class Chunking {
        private int chunkSize = 500;
        private int minChunkSize = 100;
    }
}
