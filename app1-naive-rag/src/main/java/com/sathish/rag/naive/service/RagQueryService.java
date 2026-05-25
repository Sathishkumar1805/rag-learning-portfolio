/**
 * RagQueryService.java
 *
 * <p><b>RAG Role:</b> The query pipeline — the "R+A+G" in RAG.
 * Embeds the user question, retrieves top-K similar chunks from the vector store,
 * builds an augmented prompt, calls Gemini 1.5 Flash, and returns the answer.
 *
 * <p><b>Naive RAG Query Pipeline:</b>
 * <pre>
 *  User Question
 *    └─► EmbeddingModel.embed(question) → float[]   [Gemini embedding-004]
 *          └─► VectorStore.similaritySearch(vector, topK) → List[Document]
 *                └─► PromptTemplate.render(context, question) → Prompt
 *                      └─► ChatModel.call(prompt) → ChatResponse   [Gemini 1.5 Flash]
 *                            └─► QueryResponse (answer + metadata)
 * </pre>
 *
 * <p><b>Learning Note:</b>
 * The critical weakness of Naive RAG is in step 2: the raw question embedding
 * may not semantically match the stored chunks (vocabulary mismatch problem).
 * Example: Question "What are Spring Boot auto-config pitfalls?" may miss a chunk
 * that says "conditional beans are the main gotcha in auto-configuration."
 * Advanced RAG (App #2) fixes this with HyDE query expansion.
 *
 * <p>LEARN: Compare this service with RagQueryService in App #2 where we add
 * query rewriting, MMR re-ranking, and metadata filtering before generation.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.service;

import com.sathish.rag.naive.config.RagProperties;
import com.sathish.rag.naive.dto.QueryRequest;
import com.sathish.rag.naive.dto.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes the Naive RAG query pipeline: retrieve → augment → generate.
 *
 * <p>This service is deliberately simple and imperative so every step of the
 * pipeline is visible and understandable. Spring AI provides a
 * {@code QuestionAnswerAdvisor} shortcut that wraps this exact pattern —
 * but learning the manual way builds intuition for debugging retrieval failures.
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;
    private final ChatClient chatClient;

    /**
     * Constructor injection — all dependencies from Spring context.
     *
     * @param vectorStore    SimpleVectorStore populated by DocumentIngestionService
     * @param chatModel      Gemini 1.5 Flash (auto-configured by Spring AI)
     * @param ragProperties  typed configuration (topK, similarityThreshold, systemPrompt)
     */
    public RagQueryService(VectorStore vectorStore,
                           ChatModel chatModel,
                           RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.ragProperties = ragProperties;
        // ChatClient is a fluent API over ChatModel — easier for prompt composition
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * Executes the full Naive RAG pipeline for a given question.
     *
     * <p>The pipeline is synchronous and blocking. For streaming responses,
     * TODO(learning): refactor to return Flux&lt;String&gt; using
     * {@code ChatClient.stream().content()} and a Server-Sent Events endpoint.
     *
     * @param request the query containing the user's question and optional overrides
     * @return the LLM-generated answer with retrieval metadata
     */
    public QueryResponse query(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("RAG query received: question='{}', topK={}, includeChunks={}",
                request.getQuestion(),
                request.getTopK(),
                request.isIncludeSourceChunks());

        // ── STEP 1: Determine effective topK ───────────────────────────────────
        // QueryRequest.topK overrides the global default from application.yml.
        // This allows per-request tuning without restarting the server — useful
        // for interactive experiments and the TODO(learning) /api/v1/rag/tune endpoint.
        int effectiveTopK = (request.getTopK() != null)
                ? request.getTopK()
                : ragProperties.getVectorStore().getTopKResults();

        // ── STEP 2: Retrieve top-K similar chunks ──────────────────────────────
        // SearchRequest embeds the question text internally via EmbeddingModel,
        // then performs cosine similarity search against all stored vectors.
        // The similarityThreshold filters out low-confidence chunks (default=0.0).
        // LEARN: Raise threshold to 0.7 and observe how many chunks get filtered —
        // high thresholds improve precision but risk returning zero results.
        SearchRequest searchRequest = SearchRequest.builder()
                .query(request.getQuestion())
                .topK(effectiveTopK)
                .similarityThreshold(ragProperties.getVectorStore().getSimilarityThreshold())
                .build();

        List<Document> retrievedChunks = vectorStore.similaritySearch(searchRequest);
        log.info("Retrieved {} chunks for question='{}'", retrievedChunks.size(), request.getQuestion());

        // ── STEP 3: Build augmented context string ─────────────────────────────
        // Each chunk's text is concatenated with a source attribution header.
        // The format matters: LLMs pay more attention to content at the start and end
        // of the context window ("lost in the middle" problem).
        // TODO(learning): Experiment with ordering (best-first vs interleaved) and
        // observe if answer quality changes on multi-document questions.
        String context = buildContext(retrievedChunks);

        // ── STEP 4: Handle no-results case ─────────────────────────────────────
        // If the vector store is empty or no chunks pass the similarity threshold,
        // we skip the LLM call and return a clear "no documents" message.
        // This prevents hallucination on an empty context ("I know nothing" is better
        // than "Based on the context... [fabricated answer]").
        if (retrievedChunks.isEmpty()) {
            log.warn("No chunks retrieved for question='{}' — returning empty-context response",
                    request.getQuestion());
            return QueryResponse.builder()
                    .question(request.getQuestion())
                    .answer("No relevant documents found. Please ingest documents first via POST /api/v1/rag/ingest-document")
                    .chunksRetrieved(0)
                    .sourceChunks(List.of())
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .respondedAt(Instant.now())
                    .build();
        }

        // ── STEP 5: Render prompt and call LLM ────────────────────────────────
        // The system prompt template uses {context} and {question} placeholders.
        // ChatClient's fluent API handles template rendering and API call.
        // Gemini 1.5 Flash has a 1M token context window — more than enough for topK=10 chunks.
        // FREE TIER: Gemini 1.5 Flash allows 15 RPM / 1M TPM on the free tier.
        String renderedPrompt = ragProperties.getSystemPromptTemplate()
                .replace("{context}", context)
                .replace("{question}", request.getQuestion());

        String answer = chatClient.prompt()
                .user(renderedPrompt)
                .call()
                .content();

        log.info("LLM answer generated for question='{}' in {}ms",
                request.getQuestion(), System.currentTimeMillis() - startTime);

        // ── STEP 6: Assemble response ──────────────────────────────────────────
        List<QueryResponse.SourceChunk> sourceChunks = List.of();
        if (request.isIncludeSourceChunks()) {
            sourceChunks = buildSourceChunks(retrievedChunks);
        }

        return QueryResponse.builder()
                .question(request.getQuestion())
                .answer(answer)
                .chunksRetrieved(retrievedChunks.size())
                .sourceChunks(sourceChunks)
                .latencyMs(System.currentTimeMillis() - startTime)
                .respondedAt(Instant.now())
                .build();
    }

    /**
     * Concatenates retrieved chunks into a single context string for prompt injection.
     *
     * <p>Each chunk is preceded by a "[Source: X | Chunk Y]" header so the LLM
     * can attribute statements to specific sources when generating the answer.
     *
     * @param chunks the retrieved documents from the vector store
     * @return formatted multi-chunk context string
     */
    private String buildContext(List<Document> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String source = (String) chunk.getMetadata().getOrDefault("source", "Unknown");
            String chunkIdx = (String) chunk.getMetadata().getOrDefault("chunkIndex", String.valueOf(i));

            sb.append("[Source: ").append(source)
              .append(" | Chunk ").append(chunkIdx).append("]\n")
              .append(chunk.getText())
              .append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Converts retrieved Spring AI Documents into QueryResponse.SourceChunk DTOs.
     *
     * @param chunks raw documents from the vector store similarity search
     * @return list of SourceChunk DTOs for API response serialization
     */
    private List<QueryResponse.SourceChunk> buildSourceChunks(List<Document> chunks) {
        return chunks.stream()
                .map(doc -> {
                    String source = (String) doc.getMetadata().getOrDefault("source", "Unknown");
                    String docId = (String) doc.getMetadata().getOrDefault("documentId", "");
                    int chunkIndex = Integer.parseInt(
                            (String) doc.getMetadata().getOrDefault("chunkIndex", "0"));
                    // Score: Spring AI stores the similarity score in metadata after search
                    double score = doc.getScore() != null ? doc.getScore() : 0.0;

                    return QueryResponse.SourceChunk.builder()
                            .content(doc.getText())
                            .score(score)
                            .source(source)
                            .documentId(docId)
                            .chunkIndex(chunkIndex)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
