# Naive RAG — Local Setup Guide
**RAG App 1 of 10 | Stack: Spring Boot 3.3 · Spring AI 1.0 · Gemini 1.5 Flash · SimpleVectorStore**

---

## What Is Naive RAG?

Naive RAG is the foundational RAG pattern. It implements the minimal viable pipeline:

```
Document → Split → Embed → Store
                               ↓
User Query → Embed → Cosine Search (top-K) → Augmented Prompt → LLM → Answer
```

**No query rewriting. No re-ranking. No metadata filtering. Just raw vector similarity.**

This is the baseline you'll compare every other RAG variant against. Master it first.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Google Cloud account | Free tier | https://console.cloud.google.com |

---

## Google Cloud Setup (Free Tier — $0)

### Step 1: Create a Project
```bash
gcloud projects create naive-rag-demo --name="Naive RAG Demo"
gcloud config set project naive-rag-demo
```

### Step 2: Enable Vertex AI API
```bash
gcloud services enable aiplatform.googleapis.com
```

### Step 3: Create Application Default Credentials
```bash
gcloud auth application-default login
```
This saves credentials to `~/.config/gcloud/application_default_credentials.json`.
Spring AI reads these automatically via the Google Auth SDK — no API key needed.

### Step 4: Verify Access
```bash
gcloud ai models list --region=us-central1
# Should list Gemini models if auth is correct
```

### Free Tier Limits (2025)
- **Gemini 1.5 Flash**: 15 RPM, 1M TPM, 1,500 req/day — free forever
- **text-embedding-004**: 1,500 RPM — more than enough for demos
- **Vertex AI API calls**: First $300 credit, then usage-based (Flash is very cheap)

---

## Environment Variables

Create a `.env` file in the project root (never commit this):

```bash
# .env — local development only
GOOGLE_CLOUD_PROJECT_ID=naive-rag-demo
GOOGLE_CLOUD_LOCATION=us-central1

# Optional overrides
RAG_SNAPSHOT_PATH=./vector-store-snapshot.json
PORT=8080
```

### Load env vars (choose one method):

**Option A — direnv (recommended):**
```bash
brew install direnv   # or: apt install direnv
echo 'export GOOGLE_CLOUD_PROJECT_ID=naive-rag-demo' > .envrc
echo 'export GOOGLE_CLOUD_LOCATION=us-central1' >> .envrc
direnv allow .
```

**Option B — manual export:**
```bash
export GOOGLE_CLOUD_PROJECT_ID=naive-rag-demo
export GOOGLE_CLOUD_LOCATION=us-central1
```

**Option C — IntelliJ IDEA:**
Run → Edit Configurations → Environment variables → paste the above

---

## Running the Application

```bash
# Clone / navigate to project
cd naive-rag

# Build (skip tests for first run)
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Or run the JAR directly
java -jar target/naive-rag-1.0.0-SNAPSHOT.jar
```

**Expected startup log:**
```
INFO  NaiveRagApplication - Started NaiveRagApplication in 4.2 seconds
INFO  VectorStoreConfig - No snapshot found at ./vector-store-snapshot.json. Starting with empty vector store.
```

---

## API Testing with curl

### 1. Health Check
```bash
curl http://localhost:8080/api/v1/rag/health
```
Expected:
```json
{"service":"naive-rag","status":"UP","ragType":"Naive RAG (App 1 of 10)"}
```

---

### 2. Ingest a Document

**Ingest Spring Boot overview text:**
```bash
curl -X POST http://localhost:8080/api/v1/rag/ingest-document \
  -H "Content-Type: application/json" \
  -d '{
    "source": "Spring Boot Reference Guide",
    "content": "Spring Boot makes it easy to create stand-alone, production-grade Spring-based applications. It takes an opinionated view of the Spring platform and third-party libraries so you can get started with minimum fuss. Spring Boot auto-configuration attempts to automatically configure your Spring application based on the jar dependencies that you have added. For example, if HSQLDB is on your classpath, and you have not manually configured any database connection beans, then Spring Boot auto-configures an in-memory database. Auto-configuration is non-invasive. At any point, you can start to define your own configuration to replace specific parts of the auto-configuration. If you need to find out what auto-configuration is currently being applied and why, start your application with the --debug switch. Doing so enables a debug log for a selection of core loggers and logs a conditions report to the console. Spring Boot includes a number of starters that let you add jars to your classpath. Starters are a set of convenient dependency descriptors that you can include in your application."
  }'
```

Expected response:
```json
{
  "documentId": "a1b2c3d4-...",
  "source": "Spring Boot Reference Guide",
  "chunksCreated": 3,
  "chunkSize": 512,
  "chunkOverlap": 50,
  "ingestedAt": "2024-01-01T00:00:00Z",
  "message": "Successfully ingested 3 chunks from 'Spring Boot Reference Guide'"
}
```

**Ingest a second document (for multi-document RAG):**
```bash
curl -X POST http://localhost:8080/api/v1/rag/ingest-document \
  -H "Content-Type: application/json" \
  -d '{
    "source": "Spring AI Documentation",
    "content": "Spring AI is an application framework for AI engineering. It applies core Spring design principles such as portability and modular design to the AI domain. Spring AI supports major AI providers including OpenAI, Anthropic, Google Vertex AI, Amazon Bedrock, Azure OpenAI, and many others. The framework provides a consistent ChatModel interface across all providers. For vector storage, Spring AI supports Chroma, Qdrant, Pinecone, Weaviate, Redis, PGVector, and SimpleVectorStore. The EmbeddingModel interface provides a consistent abstraction for generating vector embeddings from text. Spring AI also provides document readers for PDF, Word, HTML, and plain text via Apache Tika integration."
  }'
```

---

### 3. Check Vector Store Status
```bash
curl http://localhost:8080/api/v1/rag/vector-store/status
```

Expected:
```json
{
  "totalChunks": 5,
  "totalDocuments": 2,
  "snapshotExists": true,
  "documents": [
    {"source": "Spring Boot Reference Guide", "chunkCount": 3},
    {"source": "Spring AI Documentation", "chunkCount": 2}
  ]
}
```

---

### 4. Query — Basic
```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is Spring Boot auto-configuration?",
    "includeSourceChunks": false
  }'
```

---

### 5. Query — With Source Chunks (Debug Mode)
```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What vector stores does Spring AI support?",
    "topK": 3,
    "includeSourceChunks": true
  }'
```

Expected (truncated):
```json
{
  "question": "What vector stores does Spring AI support?",
  "answer": "Based on the provided context, Spring AI supports the following vector stores: Chroma, Qdrant, Pinecone, Weaviate, Redis, PGVector, and SimpleVectorStore.",
  "chunksRetrieved": 2,
  "sourceChunks": [
    {
      "content": "Spring AI supports Chroma, Qdrant, Pinecone...",
      "score": 0.91,
      "source": "Spring AI Documentation",
      "chunkIndex": 1
    }
  ],
  "latencyMs": 1243,
  "respondedAt": "2024-01-01T00:00:00Z"
}
```

---

### 6. Query — Out-of-Scope Question (Tests Grounding)
```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the capital of France?"}'
```

Expected: The LLM should respond with "I don't have enough information..." because
Paris is not in the ingested documents. This confirms RAG grounding is working.

---

## Learning Experiments

### Experiment 1: Chunk Size Impact
Ingest the same document with different chunk sizes (via application.yml) and compare:
- `rag.chunk-size: 128` → More chunks, more granular retrieval
- `rag.chunk-size: 1024` → Fewer chunks, richer but potentially noisy context

```bash
# Change in application.yml, restart, re-ingest, then query:
curl -X POST http://localhost:8080/api/v1/rag/query \
  -d '{"question": "Explain auto-configuration", "includeSourceChunks": true}'
# Count chunks retrieved and compare answer quality
```

### Experiment 2: Top-K Sensitivity
```bash
# topK=1: precise but may miss context
curl -X POST http://localhost:8080/api/v1/rag/query \
  -d '{"question": "What is Spring?", "topK": 1, "includeSourceChunks": true}'

# topK=10: rich context but more tokens consumed
curl -X POST http://localhost:8080/api/v1/rag/query \
  -d '{"question": "What is Spring?", "topK": 10, "includeSourceChunks": true}'
```

### Experiment 3: Identify the Vocabulary Mismatch Problem
Ingest a technical document, then ask a question using synonyms:
- Document says "auto-configuration" → ask about "automatic setup"
- Document says "beans" → ask about "components" or "objects"
Observe how Naive RAG fails here. This is the problem Advanced RAG (App #2) solves
with HyDE query expansion.

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `401 Unauthorized` from Gemini | ADC not configured | Run `gcloud auth application-default login` |
| `429 Too Many Requests` | Gemini free tier RPM limit | Wait 60 seconds; reduce query frequency |
| `Answer has no relevant info` | Vector store empty | Call `POST /ingest-document` first |
| `chunksCreated: 1` unexpectedly | Content too short | Use longer documents (>1000 chars) |
| Snapshot not loading on restart | Wrong path | Check `RAG_SNAPSHOT_PATH` env var |
| Port 8080 already in use | Another process | `export PORT=8081` or kill the other process |

---

## Project Structure

```
naive-rag/
├── src/main/java/com/sathish/rag/naive/
│   ├── NaiveRagApplication.java          # Entry point
│   ├── config/
│   │   ├── VectorStoreConfig.java        # SimpleVectorStore bean + snapshot loading
│   │   └── RagProperties.java            # @ConfigurationProperties for rag.*
│   ├── controller/
│   │   ├── RagController.java            # REST endpoints
│   │   └── GlobalExceptionHandler.java   # Error mapping
│   ├── service/
│   │   ├── DocumentIngestionService.java # Chunk → Embed → Store
│   │   ├── RagQueryService.java          # Retrieve → Augment → Generate
│   │   └── VectorStoreStatusService.java # Observability
│   └── dto/
│       ├── IngestRequest/Response.java
│       ├── QueryRequest/Response.java
│       └── VectorStoreStatusResponse.java
├── src/main/resources/
│   └── application.yml
├── src/test/
│   └── RagQueryServiceTest.java
├── README-LOCAL.md   ← you are here
├── README-DEPLOY.md  ← Render.com deployment guide
└── pom.xml
```
