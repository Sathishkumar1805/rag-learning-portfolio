package com.sathish.rag.modular.controller;

import com.sathish.rag.modular.config.RagProperties;
import com.sathish.rag.modular.dto.IngestRequest;
import com.sathish.rag.modular.dto.IngestResponse;
import com.sathish.rag.modular.dto.QueryRequest;
import com.sathish.rag.modular.dto.QueryResponse;
import com.sathish.rag.modular.service.DocumentIngestionService;
import com.sathish.rag.modular.service.RagQueryService;
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
     * POST /api/v1/rag/ingest
     * Ingests a document using parent-child chunking and stores it in the vector store.
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        log.debug("POST /ingest source={}", request.getSource());
        return ResponseEntity.ok(documentIngestionService.ingest(request));
    }

    /**
     * POST /api/v1/rag/query
     * Executes the modular RAG pipeline and returns an answer with pipeline traces.
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.debug("POST /query question='{}'", request.getQuestion());
        return ResponseEntity.ok(ragQueryService.query(request));
    }

    /**
     * GET /api/v1/rag/pipeline-info
     * Returns the active pipeline configuration and step descriptions.
     */
    @GetMapping("/pipeline-info")
    public ResponseEntity<Map<String, Object>> pipelineInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "modular-rag");
        info.put("version", "1.0.0");
        info.put("description", "Modular RAG with pluggable pipeline steps and swappable retrieval strategies");

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("activeRetriever", ragProperties.getPipeline().getRetriever());
        pipeline.put("activeReranker", ragProperties.getPipeline().getReranker());
        pipeline.put("steps", List.of("QueryExpansion", "Retrieval", "Reranking", "ContextAssembly", "Generation"));
        info.put("pipeline", pipeline);

        Map<String, Object> hyde = new LinkedHashMap<>();
        hyde.put("enabled", ragProperties.getHyde().isEnabled());
        info.put("hyde", hyde);

        Map<String, Object> mmr = new LinkedHashMap<>();
        mmr.put("enabled", ragProperties.getMmr().isEnabled());
        mmr.put("lambda", ragProperties.getMmr().getLambda());
        mmr.put("finalTopK", ragProperties.getMmr().getFinalTopK());
        info.put("mmr", mmr);

        Map<String, Object> chunking = new LinkedHashMap<>();
        chunking.put("childChunkSize", ragProperties.getChunking().getChildChunkSize());
        chunking.put("parentChunkSize", ragProperties.getChunking().getParentChunkSize());
        info.put("chunking", chunking);

        info.put("improvements vs App2", List.of(
                "Modular pipeline: each step is an independent Spring bean — swap without touching other steps",
                "Strategy pattern for retrieval: vector, keyword, or hyde selected via config (zero code change)",
                "PipelineContext: shared mutable state threads cleanly through all steps",
                "Per-step traces: execution time for every step visible in the response",
                "KeywordRetriever: Jaccard-based fallback when vector infra is unavailable"
        ));

        info.put("timestamp", Instant.now());
        return ResponseEntity.ok(info);
    }

    /**
     * GET /api/v1/rag/retrievers
     * Lists all available retrieval strategies and the currently active one.
     */
    @GetMapping("/retrievers")
    public ResponseEntity<Map<String, Object>> retrievers() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", ragProperties.getPipeline().getRetriever());
        result.put("available", List.of(
                Map.of("name", "vector", "description", "Dense vector similarity search using Vertex AI embeddings"),
                Map.of("name", "hyde", "description", "HyDE: embeds a hypothetical answer instead of the raw question"),
                Map.of("name", "keyword", "description", "Jaccard-based in-memory keyword retrieval (no embedding model needed)")
        ));
        result.put("howToSwitch", "Set rag.pipeline.retriever in application.yml to 'vector', 'hyde', or 'keyword'");
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/rag/health
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("app", "modular-rag");
        health.put("timestamp", Instant.now());
        return ResponseEntity.ok(health);
    }
}
