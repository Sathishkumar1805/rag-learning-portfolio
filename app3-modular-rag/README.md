# App 3 — Modular RAG

**Spring Boot · Spring AI 1.0.7 · Gemini 1.5 Flash · SimpleVectorStore**

App 3 takes everything from Apps 1 and 2 and refactors the query pipeline into a proper
**pluggable architecture**: each step is an independent Spring bean, retrieval strategy is
swapped via config (zero code change), and every request returns a per-step execution trace.

---

## What Is New in App 3 vs App 1 and App 2

### vs App 1 (Naive RAG)

| Dimension | App 1 | App 3 |
|---|---|---|
| Pipeline | 4 procedural steps inside `RagQueryService` | 5 independent beans implementing `RagPipelineStep` |
| Retrieval strategy | Fixed: embed raw question → search | Pluggable: `vector`, `hyde`, or `keyword` via config |
| Chunking | Flat single-level (512 tokens) | Parent-child two-level (parent 1024 / child 256) |
| Query expansion | None | HyDE: embeds a hypothetical LLM-generated answer |
| Re-ranking | None | MMR: selects diverse, relevant subset from a large candidate pool |
| Context to LLM | Child chunk text | Full parent chunk text (richer context) |
| Keyword retrieval | None | Jaccard-based in-memory retriever (no embedding API needed) |
| Response metadata | Basic (chunks retrieved, latency) | Per-step `pipelineTrace` with name + duration for every step |
| Similarity threshold | 0.0 (no filtering) | 0.65 (strict filtering) |
| Shared pipeline state | Local variables in one method | `PipelineContext` object threaded through all steps |
| Testability | Hard — all logic in one method | Each step unit-testable in isolation |

### vs App 2 (Advanced RAG)

App 2 introduced HyDE, parent-child chunking, and MMR but implemented the query pipeline as a
single 9-step method in `RagQueryService`. App 3 keeps all three techniques and restructures the
code into a clean modular architecture:

| Dimension | App 2 | App 3 |
|---|---|---|
| Query pipeline | 9 steps hardcoded in one `RagQueryService.query()` method | `RagPipelineStep` interface — each step is an independent `@Component` |
| Pipeline assembly | Implicit (all wired inside one class) | `RagPipelineFactory` explicitly assembles ordered step list |
| Shared state | Local variables passed between code blocks | `PipelineContext` — typed, mutable carrier object |
| Retrieval strategy | Single strategy (HyDE always on/off per request) | Strategy pattern — `DocumentRetriever` interface with 3 implementations; active one selected at startup via `rag.pipeline.retriever` |
| Keyword fallback | None | `KeywordRetriever` — Jaccard similarity, works with zero embedding API calls |
| Pipeline trace | None (only total `latencyMs`) | `pipelineTrace[]` in every response — step name + duration for all 5 steps |
| Vector store | Qdrant (external server) | SimpleVectorStore (in-memory, no external infra) |
| Port | 8080 | 8081 |
| `/ingest-document` endpoint | `POST /api/v1/rag/ingest-document` | `POST /api/v1/rag/ingest` |
| New endpoint | — | `GET /api/v1/rag/retrievers` — lists all strategies + active one |
| `RagQueryService` | 257-line orchestrator | 3-line delegate: `ragPipeline.execute(request)` |

### The Core Architectural Change: Pipeline Pattern

**App 2** — all logic in one method:
```java
// RagQueryService.java — App 2 (257 lines)
public QueryResponse query(QueryRequest request) {
    // STEP 1: HyDE
    // STEP 2: Select embedding
    // STEP 3: Build search request
    // STEP 4: Retrieval
    // STEP 5: MMR
    // STEP 6: Parent context
    // STEP 7: Context block
    // STEP 8: LLM generation
    // STEP 9: Assemble response
}
```

**App 3** — each step is a separate bean:
```java
// RagPipelineFactory.java — App 3
List<RagPipelineStep> steps = List.of(
    queryExpansionStep,   // was steps 1-2
    retrievalStep,        // was step 4
    rerankingStep,        // was step 5
    contextAssemblyStep,  // was steps 6-7
    generationStep        // was step 8
);
```

Each step implements the same interface:
```java
public interface RagPipelineStep {
    void execute(PipelineContext context);
    String getStepName();
}
```

Steps communicate through a shared `PipelineContext` (not return values or arguments):
```
PipelineContext fields:
  originalQuery     ← set by RagPipeline before step 1
  queryEmbedding    ← written by QueryExpansionStep, read by RetrievalStep
  candidates        ← written by RetrievalStep, read by RerankingStep
  selectedChunks    ← written by RerankingStep, read by ContextAssemblyStep
  contextBlock      ← written by ContextAssemblyStep, read by GenerationStep
  answer            ← written by GenerationStep, read by RagPipeline
  traces            ← each step appended; returned in QueryResponse
```

### Swappable Retrieval Strategy

App 3 introduces the `DocumentRetriever` strategy interface:

```
DocumentRetriever (interface)
  ├── VectorRetriever  — cosine similarity search using Qdrant/SimpleVectorStore
  ├── HydeRetriever    — generates hypothetical answer, then delegates to VectorRetriever
  └── KeywordRetriever — Jaccard-based in-memory keyword matching (no embedding API)
```

Switch strategies by changing one line in `application.yml` — no code changes required:
```yaml
rag:
  pipeline:
    retriever: hyde      # options: hyde | vector | keyword
```

`RagPipelineFactory` reads this at startup and marks the correct bean as `@Primary`.

### KeywordRetriever — New in App 3

`KeywordRetriever` uses **Jaccard similarity** to score documents against the query:

```
Jaccard(query, doc) = |intersection of tokens| / |union of tokens|
```

- Tokenises on non-alphanumeric boundaries, lowercases, ignores blanks.
- Operates entirely in-memory — no embedding model API call, no vector store.
- Useful when the embedding API is unavailable or for exact-term matching.
- Trade-off: no semantic understanding; relies on exact token overlap.

### Pipeline Trace in Every Response

Every query response now includes a `pipelineTrace` array:
```json
{
  "answer": "...",
  "pipelineTrace": [
    { "stepName": "QueryExpansion", "durationMs": 312 },
    { "stepName": "Retrieval",      "durationMs": 89  },
    { "stepName": "Reranking",      "durationMs": 45  },
    { "stepName": "ContextAssembly","durationMs": 2   },
    { "stepName": "Generation",     "durationMs": 780 }
  ],
  "latencyMs": 1234,
  "timestamp": "..."
}
```

This makes it easy to spot which step is the bottleneck.

---

## Package Structure

```
com.sathish.rag.modular
├── config/
│   ├── RagProperties.java          — typed @ConfigurationProperties for all rag.* keys
│   └── VectorStoreConfig.java      — creates SimpleVectorStore bean
├── controller/
│   └── RagController.java          — REST endpoints
├── dto/
│   ├── IngestRequest/Response.java
│   └── QueryRequest/Response.java  — includes pipelineTrace field
├── pipeline/
│   ├── RagPipelineStep.java        — strategy interface
│   ├── PipelineContext.java        — shared mutable state
│   ├── StepTrace.java              — record(stepName, durationMs, metadata)
│   ├── RagPipeline.java            — executes ordered step list, collects traces
│   ├── RagPipelineFactory.java     — assembles pipeline, selects active retriever
│   └── steps/
│       ├── QueryExpansionStep.java — HyDE expansion or direct embedding
│       ├── RetrievalStep.java      — delegates to active DocumentRetriever
│       ├── RerankingStep.java      — MMR re-ranking
│       ├── ContextAssemblyStep.java— parent context resolution + dedup
│       └── GenerationStep.java     — LLM call
├── retrieval/
│   ├── DocumentRetriever.java      — strategy interface
│   ├── RetrievalContext.java       — query, embedding, topK, threshold, filter
│   ├── VectorRetriever.java        — cosine similarity via VectorStore
│   ├── HydeRetriever.java          — HyDE expansion → VectorRetriever
│   └── KeywordRetriever.java       — Jaccard in-memory matching
└── service/
    ├── DocumentIngestionService.java       — parent-child chunking + ingest
    ├── ParentChildChunkerService.java      — two-level TokenTextSplitter
    ├── HydeQueryExpansionService.java      — LLM hypothesis generation + embedding
    ├── MmrRerankerService.java             — greedy MMR selection
    └── RagQueryService.java                — thin delegate → RagPipeline
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rag/ingest` | Ingest document using parent-child chunking |
| `POST` | `/api/v1/rag/query` | Run the modular pipeline, returns answer + `pipelineTrace` |
| `GET` | `/api/v1/rag/pipeline-info` | Active config (retriever, reranker, chunking, HyDE, MMR) |
| `GET` | `/api/v1/rag/retrievers` | Lists all available strategies + active one + how to switch |
| `GET` | `/api/v1/rag/health` | Liveness check |

### Ingest Request
```json
{
  "source": "Spring Boot Docs",
  "content": "Spring Boot auto-configuration..."
}
```

### Query Request
```json
{
  "question": "What is Spring Boot auto-configuration?",
  "topK": 4
}
```

### Query Response
```json
{
  "answer": "...",
  "question": "...",
  "pipelineTrace": [
    { "stepName": "QueryExpansion", "durationMs": 312, "metadata": {} },
    { "stepName": "Retrieval",      "durationMs": 89,  "metadata": {} },
    { "stepName": "Reranking",      "durationMs": 45,  "metadata": {} },
    { "stepName": "ContextAssembly","durationMs": 2,   "metadata": {} },
    { "stepName": "Generation",     "durationMs": 780, "metadata": {} }
  ],
  "latencyMs": 1234,
  "timestamp": "2026-05-24T10:00:00Z"
}
```

---

## Prerequisites

| Tool | Minimum version | Purpose |
|---|---|---|
| Java | 17 | Runtime and compile target |
| Maven | 3.8 | Build tool |
| Google Cloud SDK (`gcloud`) | latest | Authentication for Vertex AI |
| A Google Cloud project | — | Hosts the Vertex AI API |

Vertex AI APIs to enable in your project:
- **Vertex AI API** (`aiplatform.googleapis.com`)
- **Generative Language API** is not required — this uses Vertex AI, not the Gemini API key path.

---

## Run Locally

### 1. Authenticate with Google Cloud

```bash
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

### 2. Set environment variables

**Windows (PowerShell):**
```powershell
$env:GOOGLE_CLOUD_PROJECT_ID = "your-gcp-project-id"
$env:GOOGLE_CLOUD_LOCATION   = "us-central1"
```

**Linux / macOS:**
```bash
export GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id
export GOOGLE_CLOUD_LOCATION=us-central1
```

### 3. Build and run

```bash
cd app3-modular-rag

# Option A — Maven Spring Boot plugin (auto-reloads on code changes)
mvn spring-boot:run

# Option B — build JAR then run
mvn clean package -DskipTests
java -jar target/modular-rag-*.jar
```

The server starts on **port 8081** (`server.port=8081` in `application.yml`).

### 4. Verify startup

```bash
curl http://localhost:8081/api/v1/rag/health
```
Expected: `{"status":"UP","app":"modular-rag",...}`

```bash
curl http://localhost:8081/api/v1/rag/retrievers
```
Expected: active retriever name + all 3 available strategies.

### 5. Ingest a document

```bash
curl -X POST http://localhost:8081/api/v1/rag/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "source": "My Document",
    "content": "Spring Boot auto-configuration attempts to automatically configure your Spring application based on the jar dependencies that you have added."
  }'
```

### 6. Query

```bash
curl -X POST http://localhost:8081/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How does Spring Boot auto-configuration work?"}'
```

### 7. Switch retrieval strategy locally

Edit `application.yml` line and restart:
```yaml
rag:
  pipeline:
    retriever: keyword   # or: vector, hyde
```
No code changes needed.

### 8. Run tests

```bash
mvn test
```

---

## Deploy to a Server

App 3 has **no external infrastructure dependencies** — it uses SimpleVectorStore (in-memory). The only external service is Google's Vertex AI API. This means you can deploy it to any platform that can run a JVM.

> **Note on data persistence:** SimpleVectorStore is in-memory. All ingested documents are lost on restart. For production use, swap `VectorStoreConfig` to use Qdrant or another persistent store (as done in App 2).

---

### Option A — Docker (any cloud or on-prem)

#### 1. Create a `Dockerfile` in the project root

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/modular-rag-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 2. Build the JAR and Docker image

```bash
mvn clean package -DskipTests
docker build -t modular-rag:latest .
```

#### 3. Run the container

```bash
docker run -p 8081:8081 \
  -e GOOGLE_CLOUD_PROJECT_ID=your-project-id \
  -e GOOGLE_CLOUD_LOCATION=us-central1 \
  -v "$HOME/.config/gcloud:/root/.config/gcloud:ro" \
  modular-rag:latest
```

The `-v` mount passes your local Application Default Credentials into the container.
For production, use a **service account key** or Workload Identity instead (see below).

---

### Option B — Google Cloud Run

Cloud Run is the easiest serverless option for a Spring Boot API — no cluster management, scales to zero.

#### 1. Build and push to Artifact Registry

```bash
# Replace REGION and PROJECT_ID
PROJECT_ID=your-project-id
REGION=us-central1
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/rag-apps/modular-rag:latest

mvn clean package -DskipTests
docker build -t $IMAGE .
gcloud auth configure-docker $REGION-docker.pkg.dev
docker push $IMAGE
```

#### 2. Create a service account for the deployment

```bash
gcloud iam service-accounts create modular-rag-sa \
  --display-name="Modular RAG Service Account"

# Grant Vertex AI access
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:modular-rag-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

#### 3. Deploy to Cloud Run

```bash
gcloud run deploy modular-rag \
  --image=$IMAGE \
  --region=$REGION \
  --platform=managed \
  --allow-unauthenticated \
  --port=8081 \
  --memory=1Gi \
  --cpu=1 \
  --service-account=modular-rag-sa@$PROJECT_ID.iam.gserviceaccount.com \
  --set-env-vars="GOOGLE_CLOUD_PROJECT_ID=$PROJECT_ID,GOOGLE_CLOUD_LOCATION=$REGION"
```

Cloud Run automatically uses the service account's credentials — no key file needed.

#### 4. Get the service URL

```bash
gcloud run services describe modular-rag --region=$REGION --format="value(status.url)"
```

---

### Option C — Render.com (free tier)

Render.com can run Docker containers on its free tier. The service sleeps after 15 minutes of inactivity (free tier behaviour).

#### 1. Push your image to Docker Hub (or another registry)

```bash
docker tag modular-rag:latest your-dockerhub-username/modular-rag:latest
docker push your-dockerhub-username/modular-rag:latest
```

#### 2. Create a new Web Service on Render

- Go to **render.com → New → Web Service**
- Select **Deploy an existing image**
- Image URL: `your-dockerhub-username/modular-rag:latest`
- Port: `8081`

#### 3. Add environment variables in Render dashboard

| Key | Value |
|---|---|
| `GOOGLE_CLOUD_PROJECT_ID` | your GCP project ID |
| `GOOGLE_CLOUD_LOCATION` | `us-central1` |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | *(contents of your service account JSON key — see below)* |

#### 4. Handle Google credentials on Render

Render does not have a gcloud CLI. Use a service account JSON key:

```bash
# Create and download a key
gcloud iam service-accounts keys create sa-key.json \
  --iam-account=modular-rag-sa@$PROJECT_ID.iam.gserviceaccount.com
```

Add a startup script or set the env var `GOOGLE_APPLICATION_CREDENTIALS` pointing to a file,
or use the `GOOGLE_APPLICATION_CREDENTIALS_JSON` pattern with a startup wrapper that writes
the JSON to a temp file:

```bash
# In your entrypoint or Docker CMD
echo $GOOGLE_APPLICATION_CREDENTIALS_JSON > /tmp/gcp-sa.json
export GOOGLE_APPLICATION_CREDENTIALS=/tmp/gcp-sa.json
exec java -jar /app/app.jar
```

---

### Option D — AWS EC2 / any Linux VM

#### 1. Install prerequisites

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# Install gcloud CLI
curl https://sdk.cloud.google.com | bash
exec -l $SHELL
gcloud init
```

#### 2. Copy the JAR

```bash
# From your local machine
mvn clean package -DskipTests
scp target/modular-rag-*.jar ec2-user@<EC2-IP>:/home/ec2-user/
```

#### 3. Set credentials on the VM

Option A — Application Default Credentials (dev only):
```bash
gcloud auth application-default login
```

Option B — Service account key (recommended for production):
```bash
# Upload key to VM
scp sa-key.json ec2-user@<EC2-IP>:/home/ec2-user/

# On the VM
export GOOGLE_APPLICATION_CREDENTIALS=/home/ec2-user/sa-key.json
```

#### 4. Run the application

```bash
export GOOGLE_CLOUD_PROJECT_ID=your-project-id
export GOOGLE_CLOUD_LOCATION=us-central1

java -jar modular-rag-*.jar
```

To keep it running after logout, use `nohup` or create a `systemd` service:

```bash
# Simple nohup
nohup java -jar modular-rag-*.jar > app.log 2>&1 &

# Or create /etc/systemd/system/modular-rag.service
[Unit]
Description=Modular RAG
After=network.target

[Service]
User=ec2-user
Environment=GOOGLE_CLOUD_PROJECT_ID=your-project-id
Environment=GOOGLE_CLOUD_LOCATION=us-central1
Environment=GOOGLE_APPLICATION_CREDENTIALS=/home/ec2-user/sa-key.json
ExecStart=/usr/bin/java -jar /home/ec2-user/modular-rag-*.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable modular-rag
sudo systemctl start modular-rag
sudo systemctl status modular-rag
```

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `GOOGLE_CLOUD_PROJECT_ID` | Yes | — | GCP project that has Vertex AI enabled |
| `GOOGLE_CLOUD_LOCATION` | No | `us-central1` | Vertex AI region |
| `GOOGLE_APPLICATION_CREDENTIALS` | No* | — | Path to service account JSON key file |
| `SERVER_PORT` | No | `8081` | Override the HTTP port |

\* Not required when using Application Default Credentials (`gcloud auth application-default login`) or Workload Identity on GCP.

---

## Configuration Reference

All tuneable knobs are in `application.yml` under the `rag:` prefix.

```yaml
rag:
  pipeline:
    retriever: hyde        # Active strategy: hyde | vector | keyword
    reranker: mmr          # Reranker (currently only mmr supported)
  chunking:
    child-chunk-size: 256      # Tokens per child chunk (embedded into vector store)
    child-chunk-overlap: 40    # Token overlap between child chunks
    parent-chunk-size: 1024    # Tokens per parent chunk (fed to LLM as context)
    parent-chunk-overlap: 100  # Token overlap between parent chunks
  retrieval:
    top-k-results: 6           # Candidate pool size before MMR re-ranking
    similarity-threshold: 0.65 # Min cosine similarity to include a candidate
  generation:
    system-prompt: "..."        # System message for the LLM
    include-parent-context: true # true = feed parent text to LLM; false = child only
  hyde:
    enabled: true
    system-prompt: "..."        # Prompt for hypothetical answer generation
  mmr:
    enabled: true
    lambda: 0.6                # 1.0 = pure relevance; 0.0 = pure diversity
    candidate-pool-size: 20    # Docs retrieved before MMR selects final subset
    final-top-k: 4             # Docs kept after MMR for the LLM prompt
```

---

## Tuning Guide

| Goal | Change |
|---|---|
| More precise retrieval | Increase `similarity-threshold` (try 0.7–0.8) |
| More recall / fewer misses | Decrease `similarity-threshold` (try 0.5) |
| Richer LLM answers | Increase `parent-chunk-size` (try 1500) |
| Faster queries (skip HyDE LLM call) | Set `rag.pipeline.retriever: vector` |
| No embedding API available | Set `rag.pipeline.retriever: keyword` |
| Less redundancy in context | Decrease MMR `lambda` (try 0.4–0.5) |
| Prioritise relevance over diversity | Increase MMR `lambda` (try 0.8–0.9) |

---

## Learning Progression

```
App 1 — naive-rag          Baseline: flat chunk → embed → cosine search → generate
App 2 — advanced-rag       Adds HyDE + parent-child chunking + MMR (9-step monolith)
App 3 — modular-rag  ◄     Same techniques, refactored into pluggable pipeline + strategy pattern
App 4 — graph-rag          Adds knowledge graph for relationship-aware retrieval
App 5 — agentic-rag        Adds an autonomous agent that decides when and what to retrieve
App 6 — self-rag           Model critiques its own retrieval and regenerates if needed
App 7 — corrective-rag     Falls back to web search when retrieved docs are insufficient
```
