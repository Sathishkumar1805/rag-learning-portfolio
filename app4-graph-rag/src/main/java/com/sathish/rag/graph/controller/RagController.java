package com.sathish.rag.graph.controller;

import com.sathish.rag.graph.config.GraphRagProperties;
import com.sathish.rag.graph.dto.IngestRequest;
import com.sathish.rag.graph.dto.IngestResponse;
import com.sathish.rag.graph.dto.QueryRequest;
import com.sathish.rag.graph.dto.QueryResponse;
import com.sathish.rag.graph.model.Entity;
import com.sathish.rag.graph.repository.InMemoryGraphRepository;
import com.sathish.rag.graph.service.GraphIngestionService;
import com.sathish.rag.graph.service.RagQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final GraphIngestionService ingestionService;
    private final RagQueryService queryService;
    private final InMemoryGraphRepository graphRepository;
    private final GraphRagProperties properties;

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        log.debug("POST /ingest source='{}'", request.getSource());
        return ResponseEntity.ok(ingestionService.ingest(request));
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.debug("POST /query question='{}'", request.getQuestion());
        return ResponseEntity.ok(queryService.query(request));
    }

    @GetMapping("/graph/entities")
    public ResponseEntity<Map<String, Object>> graphEntities() {
        List<Entity> entities = graphRepository.getAllEntities();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", entities.size());
        result.put("entities", entities.stream()
                .map(e -> Map.of("name", e.name(), "type", e.type(), "description", e.description()))
                .collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/graph/stats")
    public ResponseEntity<Map<String, Object>> graphStats() {
        Map<String, Object> stats = new LinkedHashMap<>(graphRepository.getStats());
        stats.put("bfsMaxHops", properties.getGraph().getMaxBfsHops());
        stats.put("timestamp", Instant.now());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("app", "graph-rag");
        h.put("timestamp", Instant.now());
        return ResponseEntity.ok(h);
    }
}
