/**
 * RagController.java
 * <p><b>RAG Role:</b> REST API surface for the Advanced RAG application. Exposes four
 * endpoints: document ingestion, RAG query, pipeline configuration info, and health check.
 * Delegates all business logic to the service layer, keeping the controller thin.
 * <p><b>Learning Note:</b> The /pipeline-info endpoint is a learning-focused addition —
 * it exposes the active configuration so you can see exactly what parameters the pipeline
 * is using without reading application.yml. This is invaluable during experimentation.
 * <p><b>LEARN:</b> App 1 had basic ingest + query endpoints. App 2 adds /pipeline-info
 * to expose the advanced configuration (HyDE, MMR, chunking) and show "improvements vs App1".
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced.controller;

import com.sathish.rag.advanced.config.RagProperties;
import com.sathish.rag.advanced.dto.IngestRequest;
import com.sathish.rag.advanced.dto.IngestResponse;
import com.sathish.rag.advanced.dto.QueryRequest;
import com.sathish.rag.advanced.dto.QueryResponse;
import com.sathish.rag.advanced.service.DocumentIngestionService;
import com.sathish.rag.advanced.service.RagQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final DocumentIngestionService documentIngestionService;
    private final RagQueryService ragQueryService;
    private final RagProperties ragProperties;

    /**
     * POST /api/v1/rag/ingest-document
     * Ingests a document using parent-child chunking and stores it in Qdrant.
     */
    @PostMapping("/ingest-document")
    public ResponseEntity<IngestResponse> ingestDocument(@Valid @RequestBody IngestRequest request) {
        log.debug("POST /ingest-document source={}", request.getSourceName());
        IngestResponse response = documentIngestionService.ingest(request);
        log.debug("Ingestion complete: childChunks={} parents={}",
                response.getChildChunksStored(), response.getParentChunksCreated());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/rag/query
     * Executes the full advanced RAG pipeline and returns an answer with metadata.
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.debug("POST /query question='{}'", request.getQuestion());
        QueryResponse response = ragQueryService.query(request);
        log.debug("Query complete: latencyMs={} hydeUsed={} mmrUsed={}",
                response.getLatencyMs(), response.isHydeUsed(), response.isMmrUsed());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/rag/pipeline-info
     * Returns the active pipeline configuration including all advanced RAG settings.
     */
    @GetMapping("/pipeline-info")
    public ResponseEntity<Map<String, Object>> pipelineInfo() {
        log.debug("GET /pipeline-info");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "advanced-rag");
        info.put("version", "1.0.0");
        info.put("description", "Advanced RAG with HyDE, Parent-Child Chunking, and MMR Re-ranking");

        // Chunking config
        Map<String, Object> chunking = new LinkedHashMap<>();
        chunking.put("childChunkSize", ragProperties.getChunking().getChildChunkSize());
        chunking.put("childChunkOverlap", ragProperties.getChunking().getChildChunkOverlap());
        chunking.put("parentChunkSize", ragProperties.getChunking().getParentChunkSize());
        chunking.put("parentChunkOverlap", ragProperties.getChunking().getParentChunkOverlap());
        info.put("chunking", chunking);

        // Retrieval config
        Map<String, Object> retrieval = new LinkedHashMap<>();
        retrieval.put("topKResults", ragProperties.getRetrieval().getTopKResults());
        retrieval.put("similarityThreshold", ragProperties.getRetrieval().getSimilarityThreshold());
        info.put("retrieval", retrieval);

        // HyDE config
        Map<String, Object> hyde = new LinkedHashMap<>();
        hyde.put("enabled", ragProperties.getHyde().isEnabled());
        info.put("hyde", hyde);

        // MMR config
        Map<String, Object> mmr = new LinkedHashMap<>();
        mmr.put("enabled", ragProperties.getMmr().isEnabled());
        mmr.put("lambda", ragProperties.getMmr().getLambda());
        mmr.put("candidatePoolSize", ragProperties.getMmr().getCandidatePoolSize());
        mmr.put("finalTopK", ragProperties.getMmr().getFinalTopK());
        info.put("mmr", mmr);

        // Generation config
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("includeParentContext", ragProperties.getGeneration().isIncludeParentContext());
        info.put("generation", generation);

        // Vector store config
        Map<String, Object> vectorStore = new LinkedHashMap<>();
        vectorStore.put("collection", ragProperties.getQdrant().getCollectionName());
        vectorStore.put("vectorDimension", ragProperties.getQdrant().getVectorDimension());
        info.put("vectorStore", vectorStore);

        // Improvements over App 1
        info.put("improvements vs App1", List.of(
                "HyDE: Embeds hypothetical answer instead of raw question to bridge vocabulary gaps",
                "Parent-Child Chunking: Small child chunks for precise retrieval, large parent chunks for rich LLM context",
                "MMR Re-ranking: Removes redundant chunks while maximising diversity in the context window",
                "Per-request overrides: hydeEnabled, mmrEnabled, mmrLambda, topK tunable at query time",
                "Pipeline observability: chunksBeforeReranking, chunksAfterReranking, hydeHypothesis in response"
        ));

        info.put("timestamp", Instant.now());
        return ResponseEntity.ok(info);
    }

    /**
     * GET /api/v1/rag/health
     * Simple health check endpoint for monitoring.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("GET /health");
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("app", "advanced-rag");
        health.put("timestamp", Instant.now());
        return ResponseEntity.ok(health);
    }
}
