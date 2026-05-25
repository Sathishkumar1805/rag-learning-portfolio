# App 1 (Naive RAG) vs App 2 (Advanced RAG) — Comparison

This document explains the key differences between `naive-rag` (App 1) and `app2-advanced-rag` (App 2), covering architecture, chunking strategy, retrieval pipeline, vector store choice, and API surface.

---

## 1. High-Level Summary

| Dimension | App 1 — Naive RAG | App 2 — Advanced RAG |
|---|---|---|
| Goal | Baseline RAG, every step visible | Production-quality RAG with 3 advanced techniques |
| Vector store | `SimpleVectorStore` (in-memory / JSON file) | Qdrant (persistent, standalone server) |
| Chunking | Flat single-level split | Two-level parent-child hierarchy |
| Query expansion | None — embeds raw question | HyDE — embeds a hypothetical answer |
| Re-ranking | None — returns top-K by score | MMR — balances relevance and diversity |
| Context fed to LLM | Child chunk text only | Full parent chunk text (richer context) |
| Pipeline steps | 4 steps | 9 steps |
| Services | 3 services | 5 services |
| Similarity threshold | 0.0 (no filtering) | 0.65 (high-precision filter) |
| Lombok | No (manual constructors) | Yes (`@RequiredArgsConstructor`, `@Slf4j`) |
| LLM model | `gemini-1.5-flash-001` | `gemini-1.5-flash` |
| Temperature | 0.1 | 0.2 |

---

## 2. Vector Store

### App 1 — SimpleVectorStore
- Stores embeddings as a `ConcurrentHashMap` in JVM heap.
- Can serialize state to a JSON snapshot file on disk (`vector-store-snapshot.json`).
- Snapshot is loaded on startup for development convenience.
- **Limitation:** ephemeral on cloud deploys (Render.com free tier has no persistent disk); the snapshot does not survive a redeploy.
- No built-in delete-by-ID — clearing requires a restart.

### App 2 — Qdrant
- Qdrant is a dedicated vector database that runs as a separate server.
- Configured collection: `advanced-rag-docs`, vector dimension: 768.
- Documents survive application restarts and redeployments.
- Supports proper `DELETE /collections/{name}` for clearing data.
- Enables metadata filtering which is required by the advanced pipeline.

---

## 3. Document Chunking

### App 1 — Flat Single-Level Chunking

```
Raw Text
  └─► TokenTextSplitter(chunkSize=512, overlap=50)
        └─► List<Document>  (all stored in SimpleVectorStore)
```

- One pass of `TokenTextSplitter` produces fixed-size chunks.
- Metadata per chunk: `source`, `documentId`, `ingestedAt`, `chunkIndex`, `totalChunks`.
- The same chunk text is used for both **retrieval embedding** and **LLM context**.

### App 2 — Parent-Child Two-Level Chunking

```
Raw Text
  └─► TokenTextSplitter(parentSize=1024, overlap=100)   ← parent pass
        └─► List<ParentDoc>
              └─► TokenTextSplitter(childSize=256, overlap=40)  ← child pass
                    └─► List<ChildDoc>  (stored in Qdrant, each carries parentText in metadata)
```

- Two passes of `TokenTextSplitter` create a hierarchy.
- Only **child chunks** (small, precise) are embedded and stored in the vector store.
- Each child chunk carries the full **parent text** in its metadata (`parentText` field).
- During generation, the pipeline expands each retrieved child to its parent text, giving the LLM much richer context while keeping embeddings precise.

**The trade-off this solves:** Small chunks have precise embeddings but lack surrounding context. Large chunks have rich context but imprecise embeddings. Parent-child chunking gets the best of both worlds — embed small, retrieve small, generate large.

---

## 4. Query Pipeline

### App 1 — 4-Step Naive Pipeline

```
User Question
  └─ [1] Embed question → float[]                  (Gemini embedding-004)
       └─ [2] VectorStore.similaritySearch(topK=4) → List<Document>
             └─ [3] Build context string (concatenate chunk texts)
                   └─ [4] ChatModel.call(prompt)   (Gemini 1.5 Flash)
                         └─ QueryResponse
```

- The raw question text is embedded directly — vocabulary mismatch between questions and stored document text is a known weakness.
- Retrieved chunks are concatenated with `[Source: X | Chunk Y]` headers.
- No filtering of redundant chunks — if 4 chunks all say the same thing, the LLM gets 4 duplicates.

### App 2 — 9-Step Advanced Pipeline

```
User Question
  └─ [1] HyDE Expansion: LLM writes a hypothetical answer paragraph
       └─ [2] Embed HyDE hypothesis (or raw question if HyDE disabled)
             └─ [3] Build SearchRequest (candidatePool=20, threshold=0.65)
                   └─ [4] VectorStore.similaritySearch → List<Candidate> (up to 20 docs)
                         └─ [5] MMR Re-ranking → List<Selected> (finalTopK=4, lambda=0.6)
                               └─ [6] Parent Context Resolution: replace child text → parent text
                                     └─ [7] Deduplicate and build context block
                                           └─ [8] ChatModel.call(SystemMessage + UserMessage)
                                                 └─ [9] Assemble QueryResponse with full metadata
```

---

## 5. Advanced Techniques Explained

### 5.1 HyDE (Hypothetical Document Embeddings)

**Problem it solves:** A user's question ("What are auto-config pitfalls?") uses different vocabulary than stored document text ("Conditional beans are the main gotcha in auto-configuration"). Direct question embedding misses relevant chunks due to this vocabulary gap.

**How it works:**
1. The LLM is prompted to write a short hypothetical answer paragraph as if it appeared in a technical document.
2. That hypothetical answer is embedded instead of the raw question.
3. The hypothesis embedding shares vocabulary with real document text, so cosine similarity search finds better matches.

**Trade-off:** One extra LLM call per query — increases latency and cost.

**Config:**
```yaml
rag:
  hyde:
    enabled: true
    system-prompt: "You are a technical document writer. Given a question, write a concise
      hypothetical answer paragraph (2-3 sentences)..."
```

**Per-request override:** Send `"hydeEnabled": false` in the query body to disable HyDE for a single request.

### 5.2 MMR Re-ranking (Maximal Marginal Relevance)

**Problem it solves:** Top-K cosine similarity returns the K most relevant chunks — but if your document has 10 paragraphs on the same topic, the top 4 chunks may all be near-duplicates. The LLM gets redundant context and potentially misses other relevant aspects.

**How it works:**
1. A large candidate pool (20 documents) is retrieved from the vector store.
2. MMR selects documents greedily: at each iteration, pick the document that maximizes `λ × relevance − (1−λ) × redundancy_with_already_selected`.
3. `λ = 0.6` means 60% weight on relevance, 40% on diversity.
4. Final result: `finalTopK = 4` diverse, relevant chunks.

**Trade-off:** O(n × k) cosine similarity computations on candidate embeddings — adds compute cost proportional to pool size.

**Config:**
```yaml
rag:
  mmr:
    enabled: true
    lambda: 0.6
    candidate-pool-size: 20
    final-top-k: 4
```

**Per-request override:** Send `"mmrEnabled": false` or `"mmrLambda": 0.9` to adjust per request.

### 5.3 Parent Context Resolution

**How it works:**
- After MMR selects the best child chunks, the pipeline checks `doc.getMetadata().get("parentText")`.
- If present (and `include-parent-context: true`), the parent text replaces the child text in the context block.
- Duplicate parent texts are deduplicated so two child chunks from the same parent don't repeat the parent text twice.

**Config:**
```yaml
rag:
  generation:
    include-parent-context: true
```

---

## 6. API Endpoints

### App 1 — Naive RAG Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rag/ingest-document` | Ingest a document (returns 201) |
| `POST` | `/api/v1/rag/query` | Run RAG query, returns answer + optional source chunks |
| `GET` | `/api/v1/rag/vector-store/status` | Inspect vector store (chunk count, sources, snapshot) |
| `DELETE` | `/api/v1/rag/vector-store/clear` | Returns a message explaining restart is needed to clear |
| `GET` | `/api/v1/rag/health` | Liveness check |

### App 2 — Advanced RAG Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rag/ingest-document` | Ingest using parent-child chunking, stores in Qdrant |
| `POST` | `/api/v1/rag/query` | Full 9-step pipeline with HyDE + MMR + parent context |
| `GET` | `/api/v1/rag/pipeline-info` | Live view of all active configuration + improvement list vs App 1 |
| `GET` | `/api/v1/rag/health` | Liveness check |

#### App 2 Query Request Fields

App 2's query request supports per-request overrides not present in App 1:

```json
{
  "question": "What is Spring Boot auto-configuration?",
  "topK": 4,
  "hydeEnabled": true,
  "mmrEnabled": true,
  "mmrLambda": 0.6,
  "includeChunks": true
}
```

#### App 2 Query Response Fields

App 2's response includes pipeline observability metadata:

```json
{
  "answer": "...",
  "question": "...",
  "hydeUsed": true,
  "hydeHypothesis": "The hypothetical answer text used for embedding...",
  "mmrUsed": true,
  "mmrLambda": 0.6,
  "chunksBeforeReranking": 15,
  "chunksAfterReranking": 4,
  "latencyMs": 1234,
  "timestamp": "...",
  "retrievedChunks": [...]
}
```

---

## 7. Configuration Comparison

| Parameter | App 1 (Naive RAG) | App 2 (Advanced RAG) |
|---|---|---|
| Chunk size | 512 tokens (single level) | Child: 256 / Parent: 1024 tokens |
| Chunk overlap | 50 tokens | Child: 40 / Parent: 100 tokens |
| Top-K results | 4 | Candidate pool: 20, Final: 4 (after MMR) |
| Similarity threshold | 0.0 (no filtering) | 0.65 (strict filtering) |
| LLM temperature | 0.1 | 0.2 |
| Max output tokens | 2048 | 1024 |
| Snapshot / persistence | JSON file on local disk | Qdrant persistent store |

---

## 8. Service Layer Comparison

### App 1 — 3 Services

| Service | Responsibility |
|---|---|
| `DocumentIngestionService` | Split text with `TokenTextSplitter`, embed, store in `SimpleVectorStore`, optionally persist snapshot |
| `RagQueryService` | Embed question, search, build context string, call LLM, return answer |
| `VectorStoreStatusService` | Introspect `SimpleVectorStore` for chunk count and source list |

### App 2 — 5 Services

| Service | Responsibility |
|---|---|
| `DocumentIngestionService` | Thin orchestrator — delegates to `ParentChildChunkerService`, stores child docs in Qdrant |
| `RagQueryService` | Orchestrates all 9 pipeline steps, assembles final response |
| `ParentChildChunkerService` | Two-level `TokenTextSplitter` — creates child docs with parent metadata embedded |
| `HydeQueryExpansionService` | Generates hypothetical answer via LLM, embeds it; falls back gracefully on error |
| `MmrRerankerService` | Greedy MMR selection over candidate pool; computes cosine similarity in-process |

---

## 9. When to Use Each Approach

**Use Naive RAG (App 1) when:**
- You are learning RAG fundamentals and want every step transparent.
- The document collection is small and topics are well-separated.
- Latency is critical and you cannot afford extra LLM calls for HyDE.
- You want zero infrastructure beyond a JVM (no separate vector DB).

**Use Advanced RAG (App 2) when:**
- Documents have overlapping or similar passages (MMR prevents redundant context).
- Users ask questions using different vocabulary than the stored text (HyDE bridges the gap).
- Documents are long and chunks without surrounding context lose meaning (parent context).
- You need a persistent, production-grade vector store (Qdrant).
- You want per-request tuning without restarting the server.

---

## 10. Learning Progression

The apps in this portfolio build on each other:

```
naive-rag (App 1)          — baseline: flat chunk → embed → search → generate
app2-advanced-rag (App 2)  — adds HyDE + parent-child chunking + MMR
app3-modular-rag (App 3)   — same techniques, refactored into clean modular architecture
app4-graph-rag (App 4)     — adds knowledge graph for relationship-aware retrieval
app5-agentic-rag (App 5)   — adds an autonomous agent that decides when to retrieve
app6-self-rag (App 6)      — model critiques its own retrieval and regenerates if needed
app7-corrective-rag (App 7) — adds external web search as a fallback when retrieved docs are poor
```

To observe the improvement from App 1 to App 2, run the same question against both with `includeSourceChunks: true` / `includeChunks: true` and compare:
1. Which chunks were retrieved (did App 2 find more relevant ones via HyDE?)
2. Are App 2's chunks more diverse (fewer near-duplicates due to MMR)?
3. Is App 2's answer more complete (richer context from parent chunks)?
