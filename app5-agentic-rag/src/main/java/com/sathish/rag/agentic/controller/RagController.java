package com.sathish.rag.agentic.controller;

import com.sathish.rag.agentic.config.AgenticRagProperties;
import com.sathish.rag.agentic.dto.IngestRequest;
import com.sathish.rag.agentic.dto.IngestResponse;
import com.sathish.rag.agentic.dto.QueryRequest;
import com.sathish.rag.agentic.dto.QueryResponse;
import com.sathish.rag.agentic.service.AgentQueryService;
import com.sathish.rag.agentic.service.DocumentIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final DocumentIngestionService ingestionService;
    private final AgentQueryService agentQueryService;
    private final AgenticRagProperties properties;

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        log.debug("POST /ingest source='{}'", request.getSource());
        return ResponseEntity.ok(ingestionService.ingest(request));
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.debug("POST /query question='{}'", request.getQuestion());
        return ResponseEntity.ok(agentQueryService.query(request));
    }

    @GetMapping("/agent-info")
    public ResponseEntity<Map<String, Object>> agentInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", "agentic-rag");
        info.put("version", "1.0.0");
        info.put("description", "Agentic RAG with manual ReAct agent loop and tool dispatch");
        info.put("maxIterations", properties.getAgent().getMaxIterations());
        info.put("tools", List.of("searchDocuments", "searchDocumentsBySource",
                "listAvailableDocuments", "decomposeQuestion"));
        info.put("improvements vs App4", List.of(
                "Agent loop: LLM iteratively calls tools until FINAL_ANSWER",
                "Tool dispatch: TOOL_CALL: toolName({args}) directive parsed by AgentQueryService",
                "Step trace: each tool call + observation recorded in QueryResponse",
                "Graceful termination: max-iterations guard prevents infinite loops"
        ));
        info.put("timestamp", Instant.now());
        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("app", "agentic-rag");
        h.put("timestamp", Instant.now());
        return ResponseEntity.ok(h);
    }
}
