/**
 * DocumentIngestionService.java
 *
 * <p><b>RAG Role:</b> Ingestion pipeline — the "I" in RAG.
 * Accepts raw text, splits it into fixed-size chunks, embeds each chunk via
 * Gemini embedding-004, and stores the resulting vectors in SimpleVectorStore.
 *
 * <p><b>Naive RAG Ingestion Pipeline:</b>
 * <pre>
 *  Raw Text
 *    └─► TokenTextSplitter (chunkSize, chunkOverlap)
 *          └─► List[Document]  (each has text + metadata)
 *                └─► EmbeddingModel.embed(text) → float[]  [Gemini API call]
 *                      └─► SimpleVectorStore.add(documents)
 *                            └─► Optional: persist snapshot to disk
 * </pre>
 *
 * <p><b>Learning Note:</b>
 * The bottleneck here is the embedding API call — each chunk costs one HTTP round-trip
 * to Gemini. Spring AI batches these automatically in EmbeddingModel implementations
 * that support batch mode. Gemini embedding-004 supports batch=up to 100 texts,
 * so a 100-chunk document = 1 API call, not 100.
 *
 * <p>LEARN: Advanced RAG (App #2) replaces TokenTextSplitter with a semantic splitter
 * that detects topic boundaries using sentence embeddings — compare ingestion quality.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive.service;

import com.sathish.rag.naive.config.RagProperties;
import com.sathish.rag.naive.dto.IngestRequest;
import com.sathish.rag.naive.dto.IngestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the full document ingestion lifecycle for Naive RAG.
 *
 * <p>Thread safety: SimpleVectorStore uses a ConcurrentHashMap internally,
 * so concurrent ingestion calls are safe but may produce interleaved chunk ordering.
 * This is acceptable for Naive RAG where chunk order does not affect retrieval.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * Constructor injection — preferred over @Autowired for testability.
     *
     * @param vectorStore    the configured SimpleVectorStore (from VectorStoreConfig)
     * @param ragProperties  typed config from application.yml
     */
    public DocumentIngestionService(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    /**
     * Ingests a document by splitting, embedding, and storing all chunks.
     *
     * <p>Steps:
     * <ol>
     *   <li>Resolve or generate a document ID</li>
     *   <li>Wrap the raw text in a Spring AI {@link Document}</li>
     *   <li>Split into fixed-size token chunks with overlap</li>
     *   <li>Add source metadata to each chunk for downstream attribution</li>
     *   <li>Store all chunks in the vector store (triggers embedding API calls)</li>
     *   <li>Optionally persist the snapshot to disk</li>
     * </ol>
     *
     * @param request the ingestion request containing content, source, and optional documentId
     * @return ingestion summary with chunk count and timing metadata
     */
    public IngestResponse ingestDocument(IngestRequest request) {
        long startTime = System.currentTimeMillis();

        // ── STEP 1: Resolve document identity ──────────────────────────────────
        // Use client-supplied ID for deduplication; fall back to UUID for simplicity.
        // NOTE: SimpleVectorStore does NOT deduplicate by ID — re-ingesting the same
        // documentId will ADD duplicate chunks. Real systems need a "delete then re-add" pattern.
        String documentId = (request.getDocumentId() != null && !request.getDocumentId().isBlank())
                ? request.getDocumentId()
                : UUID.randomUUID().toString();

        log.info("Starting ingestion for documentId={}, source={}", documentId, request.getSource());

        // ── STEP 2: Create root Document with metadata ─────────────────────────
        // Spring AI's Document carries both content and a metadata map.
        // We store source and documentId in metadata so they survive chunking
        // and are retrievable alongside each chunk at query time.
        Map<String, Object> metadata = Map.of(
                "source", request.getSource(),
                "documentId", documentId,
                "ingestedAt", Instant.now().toString()
        );
        Document rootDocument = new Document(request.getContent(), metadata);

        // ── STEP 3: Split into fixed-size chunks ───────────────────────────────
        // TokenTextSplitter uses a token count (approximated as chars/4) by default.
        // chunkSize=512 tokens ~ 2KB of text. chunkOverlap=50 tokens prevents
        // sentences from being cut off at boundaries.
        // LEARN: Try chunkSize=256 for precise tech docs, 1024 for narrative content.
        TokenTextSplitter splitter = new TokenTextSplitter(
                ragProperties.getChunkSize(),
                ragProperties.getChunkOverlap(),
                5,          // minChunkSizeChars — discard fragments shorter than this
                10_000,     // maxNumChunks — safety cap
                true        // keepSeparator — preserve sentence-ending punctuation
        );

        List<Document> chunks = splitter.apply(List.of(rootDocument));
        log.info("Split documentId={} into {} chunks (chunkSize={}, overlap={})",
                documentId, chunks.size(),
                ragProperties.getChunkSize(), ragProperties.getChunkOverlap());

        // ── STEP 4: Enrich each chunk with positional metadata ─────────────────
        // The splitter copies parent metadata to children but doesn't add chunk index.
        // We add it manually so QueryResponse.SourceChunk.chunkIndex is meaningful.
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunkIndex", String.valueOf(i));
            chunks.get(i).getMetadata().put("totalChunks", String.valueOf(chunks.size()));
        }

        // ── STEP 5: Embed + store all chunks ───────────────────────────────────
        // VectorStore.add() calls EmbeddingModel.embedForStorage(text) for each chunk.
        // Gemini embedding-004 supports batched embedding — Spring AI sends all chunks
        // in one API call when the model supports it, reducing latency significantly.
        vectorStore.add(chunks);
        log.info("Successfully stored {} chunks for documentId={}", chunks.size(), documentId);

        // ── STEP 6: Persist snapshot (optional, dev-only) ──────────────────────
        // SimpleVectorStore can serialize its state to JSON for dev restarts.
        // IMPORTANT: Render.com free tier has an ephemeral FS — snapshots don't survive redeploys.
        if (ragProperties.getVectorStore().isPersistAfterIngest()) {
            persistSnapshot();
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("Ingestion complete for documentId={} in {}ms", documentId, latencyMs);

        return IngestResponse.builder()
                .documentId(documentId)
                .source(request.getSource())
                .chunksCreated(chunks.size())
                .chunkSize(ragProperties.getChunkSize())
                .chunkOverlap(ragProperties.getChunkOverlap())
                .ingestedAt(Instant.now())
                .message(String.format("Successfully ingested %d chunks from '%s'",
                        chunks.size(), request.getSource()))
                .build();
    }

    /**
     * Persists the current vector store state to a JSON snapshot file on disk.
     *
     * <p>Failure to persist is non-fatal — the document is already stored in memory.
     * The warning is logged so Render.com log tails surface the issue.
     */
    public void persistSnapshot() {
        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            try {
                File snapshotFile = new File(ragProperties.getVectorStore().getSnapshotPath());
                // Ensure parent directories exist (relevant for custom paths like /tmp/rag/snapshot.json)
                snapshotFile.getParentFile().mkdirs();
                simpleStore.save(snapshotFile);
                log.debug("Vector store snapshot persisted to: {}", snapshotFile.getAbsolutePath());
            } catch (Exception e) {
                // Non-fatal — the in-memory store is intact, only disk persistence failed
                log.warn("Failed to persist vector store snapshot: {}", e.getMessage());
            }
        }
    }
}
