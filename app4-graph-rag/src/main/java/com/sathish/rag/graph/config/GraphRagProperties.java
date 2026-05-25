package com.sathish.rag.graph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class GraphRagProperties {

    private Chunking chunking = new Chunking();
    private Graph graph = new Graph();
    private Query query = new Query();

    @Data
    public static class Chunking {
        private int chunkSize = 500;
        private int minChunkSize = 100;
    }

    @Data
    public static class Graph {
        private int maxBfsHops = 2;
        private String extractionSystemPrompt =
            "You are an entity and relationship extractor. Given a text passage, extract all named entities " +
            "and their relationships. Return ONLY a JSON object (no markdown, no explanation) with this structure:\n" +
            "{\n" +
            "  \"entities\": [{\"name\": \"...\", \"type\": \"PERSON|ORGANIZATION|CONCEPT|TECHNOLOGY|LOCATION\", \"description\": \"...\"}],\n" +
            "  \"relationships\": [{\"fromEntity\": \"...\", \"toEntity\": \"...\", \"relationType\": \"VERB_PHRASE\", \"description\": \"...\"}]\n" +
            "}\n" +
            "If no entities are found, return {\"entities\": [], \"relationships\": []}. Do not include markdown code blocks.";
    }

    @Data
    public static class Query {
        private int topK = 5;
        private String systemPrompt =
            "You are a knowledgeable assistant. Answer the user's question based solely on the provided context.\n" +
            "The context comes from two sources:\n" +
            "1. VECTOR CONTEXT: Semantically similar passages found by vector search.\n" +
            "2. GRAPH CONTEXT: Related entities and relationships from a knowledge graph.\n\n" +
            "If the answer is not in the context, say \"I don't have enough information to answer this question.\"\n" +
            "Be concise and accurate.";
    }
}
