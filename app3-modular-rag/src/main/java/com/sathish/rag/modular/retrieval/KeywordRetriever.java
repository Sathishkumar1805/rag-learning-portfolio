/**
 * In-memory keyword-based retriever using Jaccard similarity scoring.
 *
 * <p>Tokenises documents and queries on non-alphanumeric boundaries, then scores each
 * document against the query using the Jaccard coefficient. Returns the top-K highest
 * scoring documents. This implementation is intentionally lightweight and is most useful
 * for exact-term matching or as a fallback when vector infrastructure is unavailable.</p>
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0-SNAPSHOT
 */
package com.sathish.rag.modular.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KeywordRetriever implements DocumentRetriever {

    /** In-memory document text store keyed by document ID. */
    private final Map<String, String> docIdToText = new ConcurrentHashMap<>();

    /** In-memory document metadata store keyed by document ID. */
    private final Map<String, Map<String, Object>> docIdToMetadata = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "keyword";
    }

    /**
     * Registers a document for keyword-based retrieval.
     *
     * @param docId    unique document identifier
     * @param text     full document text
     * @param metadata arbitrary metadata to attach
     */
    public void addDocument(String docId, String text, Map<String, Object> metadata) {
        log.debug("KeywordRetriever.addDocument() docId='{}'", docId);
        docIdToText.put(docId, text);
        docIdToMetadata.put(docId, metadata != null ? metadata : Map.of());
    }

    /**
     * Retrieves top-K documents most similar to the query by Jaccard similarity.
     *
     * @param ctx retrieval context with the query and topK parameters
     * @return ranked list of matching documents
     */
    @Override
    public List<Document> retrieve(RetrievalContext ctx) {
        log.debug("KeywordRetriever.retrieve() query='{}' topK={}", ctx.query(), ctx.topK());

        Set<String> queryTokens = tokenize(ctx.query());

        if (queryTokens.isEmpty() || docIdToText.isEmpty()) {
            log.debug("KeywordRetriever: no tokens or no documents — returning empty list");
            return List.of();
        }

        List<Map.Entry<String, Double>> scored = docIdToText.entrySet().stream()
                .map(entry -> {
                    Set<String> docTokens = tokenize(entry.getValue());
                    double score = jaccardScore(queryTokens, docTokens);
                    return Map.entry(entry.getKey(), score);
                })
                .filter(e -> e.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(ctx.topK())
                .collect(Collectors.toList());

        log.debug("KeywordRetriever found {} qualifying documents", scored.size());

        return scored.stream()
                .map(e -> {
                    String text = docIdToText.get(e.getKey());
                    Map<String, Object> meta = new HashMap<>(docIdToMetadata.getOrDefault(e.getKey(), Map.of()));
                    meta.put("keywordScore", e.getValue());
                    meta.put("docId", e.getKey());
                    return new Document(text, meta);
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split("[^a-zA-Z0-9]+"))
                .map(String::toLowerCase)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }

    private double jaccardScore(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }
}
