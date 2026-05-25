/**
 * RagQueryService.java
 * <p><b>RAG Role:</b> Orchestrates the full advanced RAG query pipeline across 9 steps:
 * (1) HyDE query expansion, (2) embedding selection, (3) similarity search request,
 * (4) vector store retrieval, (5) MMR re-ranking, (6) parent context resolution,
 * (7) context block construction, (8) LLM generation, (9) response assembly.
 * <p><b>Learning Note:</b> The 9-step pipeline makes each transformation explicit and
 * independently debuggable. Each step's output feeds the next, and the final QueryResponse
 * carries metadata from every step so you can trace exactly how the answer was produced.
 * <p><b>LEARN:</b> App 1's query service had 4 steps (embed → search → context → generate).
 * App 2 adds HyDE before embedding, MMR after retrieval, and parent context expansion
 * after selection — showing how advanced RAG techniques stack on the same foundation.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.service;

import com.sathish.rag.advanced.config.RagProperties;
import com.sathish.rag.advanced.dto.ChunkDetail;
import com.sathish.rag.advanced.dto.QueryRequest;
import com.sathish.rag.advanced.dto.QueryResponse;
import com.sathish.rag.advanced.service.HydeQueryExpansionService.HydeResult;
import com.sathish.rag.advanced.service.MmrRerankerService.MmrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    private final RagProperties ragProperties;
    private final HydeQueryExpansionService hydeService;
    private final MmrRerankerService mmrService;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    /**
     * Executes the full 9-step advanced RAG query pipeline.
     *
     * @param request QueryRequest with question and optional overrides
     * @return QueryResponse with answer and full pipeline metadata
     */
    public QueryResponse query(QueryRequest request) {
        log.debug("ENTER query question='{}'", request.getQuestion());
        long start = System.currentTimeMillis();

        try {
            // ─── STEP 1: HyDE Query Expansion ────────────────────────────────────────
            log.debug("STEP 1: HyDE query expansion");
            HydeResult hydeResult = hydeService.expand(request.getQuestion(), request.getHydeEnabled());
            log.debug("STEP 1 complete hydeUsed={}", hydeResult.hydeUsed());

            // ─── STEP 2: Select Query Embedding ──────────────────────────────────────
            log.debug("STEP 2: Select query embedding");
            List<Double> queryEmbedding;
            if (hydeResult.hydeUsed()) {
                queryEmbedding = hydeResult.embedding();
                log.debug("STEP 2: Using HyDE hypothesis embedding size={}", queryEmbedding.size());
            } else {
                queryEmbedding = hydeService.embedQuestion(request.getQuestion());
                log.debug("STEP 2: Using direct question embedding size={}", queryEmbedding.size());
            }

            // ─── STEP 3: Build Similarity Search Request ─────────────────────────────
            log.debug("STEP 3: Building SearchRequest");
            RagProperties.RetrievalProperties retrievalCfg = ragProperties.getRetrieval();
            RagProperties.MmrProperties mmrCfg = ragProperties.getMmr();

            int candidatePoolSize = mmrCfg.getCandidatePoolSize();
            int topKParam = request.getTopK() != null ? request.getTopK() : retrievalCfg.getTopKResults();
            double threshold = retrievalCfg.getSimilarityThreshold();

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(request.getQuestion())
                    .topK(candidatePoolSize)
                    .similarityThreshold(threshold)
                    .build();
            log.debug("STEP 3: SearchRequest topK={} threshold={}", candidatePoolSize, threshold);

            // ─── STEP 4: Vector Store Retrieval ──────────────────────────────────────
            log.debug("STEP 4: Similarity search");
            List<Document> candidates = vectorStore.similaritySearch(searchRequest);
            log.debug("STEP 4: Retrieved {} candidates", candidates.size());

            if (candidates == null || candidates.isEmpty()) {
                log.warn("STEP 4: No candidates found — returning empty response");
                return buildEmptyResponse(request.getQuestion(), hydeResult, start);
            }

            // ─── STEP 5: MMR Re-ranking ───────────────────────────────────────────────
            log.debug("STEP 5: MMR re-ranking");
            MmrResult mmrResult = mmrService.rerank(
                    candidates,
                    queryEmbedding,
                    request.getMmrLambda(),
                    request.getMmrEnabled(),
                    null
            );
            List<Document> selectedDocs = mmrResult.selectedDocuments();
            log.debug("STEP 5: MMR selected {}/{} docs mmrApplied={}",
                    selectedDocs.size(), candidates.size(), mmrResult.mmrApplied());

            // ─── STEP 6: Parent Context Resolution + Deduplication ───────────────────
            log.debug("STEP 6: Resolving parent context");
            boolean useParentContext = ragProperties.getGeneration().isIncludeParentContext();
            List<String> contextTexts = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Document doc : selectedDocs) {
                String text;
                if (useParentContext) {
                    Object parentText = doc.getMetadata().get("parentText");
                    text = (parentText != null && !parentText.toString().isBlank())
                            ? parentText.toString()
                            : doc.getText();
                } else {
                    text = doc.getText();
                }
                // Deduplicate by content
                if (seen.add(text)) {
                    contextTexts.add(text);
                }
            }
            log.debug("STEP 6: {} unique context texts (useParent={})", contextTexts.size(), useParentContext);

            // ─── STEP 7: Build Context Block ─────────────────────────────────────────
            log.debug("STEP 7: Building context block");
            StringBuilder contextBlock = new StringBuilder();
            for (int i = 0; i < contextTexts.size(); i++) {
                contextBlock.append("[").append(i + 1).append("] ")
                        .append(contextTexts.get(i))
                        .append("\n\n");
            }
            log.debug("STEP 7: Context block length={}", contextBlock.length());

            // ─── STEP 8: LLM Generation ───────────────────────────────────────────────
            log.debug("STEP 8: LLM generation");
            String systemPrompt = ragProperties.getGeneration().getSystemPrompt();
            String userMessage = "Context:\n" + contextBlock.toString().trim()
                    + "\n\nQuestion: " + request.getQuestion();

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            ));

            long llmStart = System.currentTimeMillis();
            String answer = chatModel.call(prompt)
                    .getResult()
                    .getOutput()
                    .getText();
            log.debug("STEP 8: LLM responded in {}ms", System.currentTimeMillis() - llmStart);

            // ─── STEP 9: Assemble QueryResponse ──────────────────────────────────────
            log.debug("STEP 9: Assembling response");
            long latencyMs = System.currentTimeMillis() - start;

            List<ChunkDetail> chunkDetails = null;
            if (request.isIncludeChunks()) {
                Set<Integer> selectedDocSet = new HashSet<>();
                for (Document selDoc : selectedDocs) {
                    for (int i = 0; i < candidates.size(); i++) {
                        if (candidates.get(i) == selDoc) {
                            selectedDocSet.add(i);
                        }
                    }
                }
                chunkDetails = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    Document doc = candidates.get(i);
                    Map<String, Object> meta = doc.getMetadata();
                    Object parentTextObj = meta.get("parentText");
                    Object sourceObj = meta.get("source");
                    Object childIdxObj = meta.get("globalChildIndex");

                    chunkDetails.add(ChunkDetail.builder()
                            .childText(doc.getText())
                            .parentText(parentTextObj != null ? parentTextObj.toString() : null)
                            .source(sourceObj != null ? sourceObj.toString() : "unknown")
                            .chunkIndex(childIdxObj != null ? Integer.parseInt(childIdxObj.toString()) : i)
                            .selectedByMmr(selectedDocSet.contains(i))
                            .metadata(meta)
                            .build());
                }
            }

            QueryResponse response = QueryResponse.builder()
                    .answer(answer)
                    .question(request.getQuestion())
                    .hydeHypothesis(hydeResult.hydeUsed() ? hydeResult.hypothesis() : null)
                    .hydeUsed(hydeResult.hydeUsed())
                    .mmrUsed(mmrResult.mmrApplied())
                    .mmrLambda(mmrResult.mmrApplied() ? mmrResult.lambdaUsed() : null)
                    .chunksBeforeReranking(candidates.size())
                    .chunksAfterReranking(selectedDocs.size())
                    .latencyMs(latencyMs)
                    .timestamp(Instant.now())
                    .retrievedChunks(chunkDetails)
                    .build();

            log.debug("EXIT query latencyMs={} hydeUsed={} mmrUsed={} chunks={}/{}",
                    latencyMs, response.isHydeUsed(), response.isMmrUsed(),
                    response.getChunksAfterReranking(), response.getChunksBeforeReranking());

            return response;

        } catch (Exception e) {
            log.error("Query pipeline failed: {}", e.getMessage(), e);
            long latencyMs = System.currentTimeMillis() - start;
            return QueryResponse.builder()
                    .answer("An error occurred while processing your query: " + e.getMessage())
                    .question(request.getQuestion())
                    .hydeUsed(false)
                    .mmrUsed(false)
                    .latencyMs(latencyMs)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    /**
     * Builds a graceful "no results" response when the vector store returns no candidates.
     */
    private QueryResponse buildEmptyResponse(String question, HydeResult hydeResult, long start) {
        long latencyMs = System.currentTimeMillis() - start;
        return QueryResponse.builder()
                .answer("I could not find relevant information in the knowledge base to answer your question. "
                        + "Please try rephrasing your question or ensure relevant documents have been ingested.")
                .question(question)
                .hydeHypothesis(hydeResult.hydeUsed() ? hydeResult.hypothesis() : null)
                .hydeUsed(hydeResult.hydeUsed())
                .mmrUsed(false)
                .chunksBeforeReranking(0)
                .chunksAfterReranking(0)
                .latencyMs(latencyMs)
                .timestamp(Instant.now())
                .build();
    }
}
