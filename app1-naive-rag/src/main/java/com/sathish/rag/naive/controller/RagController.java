/**
 * RagController.java
 *
 * <p><b>RAG Role:</b> REST API layer — exposes the Naive RAG pipeline over HTTP.
 * Three endpoints cover the full Naive RAG demo: ingest, query, and status.
 *
 * <p><b>REST Contract:</b>
 * <pre>
 *  POST   /api/v1/rag/ingest-document      — ingest a document
 *  POST   /api/v1/rag/query                — run the RAG query pipeline
 *  GET    /api/v1/rag/vector-store/status  — inspect the vector store
 *  DELETE /api/v1/rag/vector-store/clear   — clear all stored documents (dev/test only)
 *  GET    /api/v1/rag/health               — simple liveness check
 * </pre>
 *
 * <p><b>Learning Note:</b>
 * The controller is intentionally thin — it validates input, delegates to services,
 * and maps exceptions to HTTP status codes. No business logic lives here.
 * This mirrors the Hexagonal Architecture pattern used in Advanced RAG (App #2)
 * where adapters are strictly separated from domain logic.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.controller;

import com.sathish.rag.naive.dto.IngestRequest;
import com.sathish.rag.naive.dto.IngestResponse;
import com.sathish.rag.naive.dto.QueryRequest;
import com.sathish.rag.naive.dto.QueryResponse;
import com.sathish.rag.naive.dto.VectorStoreStatusResponse;
import com.sathish.rag.naive.service.DocumentIngestionService;
import com.sathish.rag.naive.service.RagQueryService;
import com.sathish.rag.naive.service.VectorStoreStatusService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for the Naive RAG API.
 *
 * <p>Base path: {@code /api/v1/rag} — versioned for forward compatibility.
 * All endpoints consume/produce {@code application/json}.
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final DocumentIngestionService ingestionService;
    private final RagQueryService queryService;
    private final VectorStoreStatusService statusService;

    /**
     * @param ingestionService handles document chunking, embedding, and storage
     * @param queryService     handles retrieval, prompt building, and LLM invocation
     * @param statusService    handles vector store introspection
     */
    public RagController(DocumentIngestionService ingestionService,
                         RagQueryService queryService,
                         VectorStoreStatusService statusService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.statusService = statusService;
    }

    // ── ENDPOINT 1: Ingest Document ───────────────────────────────────────────

    /**
     * Ingests a plain-text document into the vector store.
     *
     * <p>The document is split into fixed-size chunks, each chunk is embedded
     * via Gemini embedding-004, and stored in SimpleVectorStore.
     *
     * <p>Example curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/v1/rag/ingest-document \
     *   -H "Content-Type: application/json" \
     *   -d '{"source": "Spring Boot Docs", "content": "Spring Boot auto-configuration..."}'
     * </pre>
     *
     * @param request the document ingestion request (validated)
     * @return 201 Created with ingestion summary
     */
    @PostMapping("/ingest-document")
    public ResponseEntity<IngestResponse> ingestDocument(@Valid @RequestBody IngestRequest request) {
        log.info("POST /api/v1/rag/ingest-document — source='{}'", request.getSource());
        IngestResponse response = ingestionService.ingestDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── ENDPOINT 2: Query ─────────────────────────────────────────────────────

    /**
     * Executes the full Naive RAG pipeline: embed question → retrieve top-K → generate answer.
     *
     * <p>Set {@code includeSourceChunks: true} in the request body to receive the
     * retrieved chunks alongside the answer — essential for debugging retrieval quality.
     *
     * <p>Example curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/v1/rag/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What is Spring Boot auto-configuration?", "includeSourceChunks": true}'
     * </pre>
     *
     * @param request the query request with the user's question (validated)
     * @return 200 OK with the LLM-generated answer and optional source chunks
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        log.info("POST /api/v1/rag/query — question='{}'", request.getQuestion());
        QueryResponse response = queryService.query(request);
        return ResponseEntity.ok(response);
    }

    // ── ENDPOINT 3: Vector Store Status ──────────────────────────────────────

    /**
     * Returns a status summary of the vector store contents.
     *
     * <p>Use this to verify documents were ingested before running queries.
     * Returns chunk counts, document sources, and snapshot file status.
     *
     * @return 200 OK with vector store status
     */
    @GetMapping("/vector-store/status")
    public ResponseEntity<VectorStoreStatusResponse> getVectorStoreStatus() {
        log.info("GET /api/v1/rag/vector-store/status");
        return ResponseEntity.ok(statusService.getStatus());
    }

    // ── ENDPOINT 4: Clear Vector Store (Dev/Test) ─────────────────────────────

    /**
     * Clears all documents from the in-memory vector store.
     *
     * <p><b>WARNING:</b> This operation is irreversible within the current JVM session.
     * The snapshot file is NOT deleted — restart the app to restore from snapshot.
     * Intended for development and integration testing only.
     *
     * <p>TODO(learning): Add a Spring profile guard (@Profile("!prod")) to prevent
     * accidental calls in production deployments.
     *
     * @return 200 OK with confirmation message
     */
    @DeleteMapping("/vector-store/clear")
    public ResponseEntity<Map<String, Object>> clearVectorStore() {
        log.warn("DELETE /api/v1/rag/vector-store/clear — clearing all documents from vector store");
        // SimpleVectorStore does not expose a clear() API directly.
        // The pragmatic approach for Naive RAG: restart the app.
        // LEARN: Qdrant (App #2) has a proper delete-collection API.
        return ResponseEntity.ok(Map.of(
                "message", "To clear the SimpleVectorStore, restart the application with rag.vector-store.load-snapshot-on-start=false",
                "tip", "In App #2 (Advanced RAG), Qdrant provides a proper DELETE /collections/{name} endpoint",
                "timestamp", Instant.now().toString()
        ));
    }

    // ── ENDPOINT 5: Health ─────────────────────────────────────────────────────

    /**
     * Simple liveness/readiness check endpoint.
     *
     * <p>Render.com free tier uses this path for health checks. Ensure this
     * returns 200 within 30 seconds of startup or the deployment is marked as failed.
     *
     * @return 200 OK with service name and timestamp
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "naive-rag",
                "status", "UP",
                "ragType", "Naive RAG (App 1 of 10)",
                "timestamp", Instant.now().toString()
        ));
    }
}
