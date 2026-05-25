package com.sathish.rag.agentic.service;

import com.sathish.rag.agentic.config.AgenticRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagTools {

    private final VectorStore vectorStore;
    private final AgenticRagProperties properties;

    public String searchDocuments(String query, int topK) {
        log.debug("searchDocuments query='{}' topK={}", query, topK);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        if (docs.isEmpty()) {
            return "No documents found for query: " + query;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sb.append("[").append(i + 1).append("]");
            Object source = doc.getMetadata().get("source");
            if (source != null) sb.append(" Source: ").append(source);
            sb.append("\n").append(doc.getText()).append("\n\n");
        }
        return sb.toString().strip();
    }

    public String searchDocumentsBySource(String source, int topK) {
        log.debug("searchDocumentsBySource source='{}' topK={}", source, topK);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(source).topK(topK * 3).build());
        String result = docs.stream()
                .filter(d -> source.equals(d.getMetadata().get("source")))
                .limit(topK)
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        return result.isEmpty() ? "No documents found from source: " + source : result;
    }

    public String listAvailableDocuments() {
        log.debug("listAvailableDocuments");
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query("document information data knowledge").topK(100).build());
            String sources = docs.stream()
                    .map(d -> d.getMetadata().getOrDefault("source", "unknown").toString())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("\n- ", "- ", ""));
            return sources.isEmpty() ? "No documents ingested yet" : "Available sources:\n" + sources;
        } catch (Exception e) {
            return "Unable to list documents: " + e.getMessage();
        }
    }

    public String decomposeQuestion(String question) {
        log.debug("decomposeQuestion question='{}'", question);
        String[] parts = question.split("\\?|\\band\\b|\\bor\\b");
        String decomposed = Arrays.stream(parts)
                .map(String::strip)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.joining("; "));
        return decomposed.isEmpty() ? question : "Sub-questions: " + decomposed;
    }
}
