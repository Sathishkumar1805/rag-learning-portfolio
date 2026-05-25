# App 2 — Advanced RAG

## What is RAG?

**Retrieval-Augmented Generation (RAG)** is a technique that makes LLMs answer questions using your own documents. Instead of relying solely on what the model was trained on, RAG works in two steps:

1. **Retrieve** — search a vector database for document chunks that are semantically similar to the user's question
2. **Generate** — pass those chunks as context to the LLM so it answers using that specific information

This prevents hallucination and keeps answers grounded in your data.

---

## About This App

This is **App 2** in the RAG learning series — an advanced production-grade RAG pipeline built with **Spring Boot 3.3**, **Spring AI 1.0.7**, **Google Vertex AI (Gemini + Embeddings)**, and **Qdrant** as the vector store.

It implements three advanced retrieval techniques on top of a basic RAG baseline:

| Technique | What it does |
|---|---|
| **HyDE** (Hypothetical Document Embeddings) | Generates a hypothetical answer to the question first, then embeds *that* instead of the raw question — closing the vocabulary gap between questions and document text |
| **Parent-Child Chunking** | Stores small child chunks (256 tokens) for precise semantic search, but feeds the LLM the large parent chunk (1024 tokens) for richer context |
| **MMR Re-ranking** (Maximal Marginal Relevance) | Scores retrieved chunks by balancing relevance vs. redundancy, so the LLM gets diverse context rather than five near-duplicate paragraphs |

### API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rag/ingest-document` | Ingest a document using parent-child chunking |
| `POST` | `/api/v1/rag/query` | Run the full advanced RAG pipeline |
| `GET` | `/api/v1/rag/pipeline-info` | View active HyDE, MMR, and chunking config |
| `GET` | `/api/v1/rag/health` | Health check |

---

## How App 2 Differs from App 1

| Aspect | App 1 (Basic RAG) | App 2 (Advanced RAG) |
|---|---|---|
| **Chunking** | Flat — TokenTextSplitter once at a single size | Two-level — parent (1024t) split into children (256t), child stored with parent text in metadata |
| **Query embedding** | Raw question embedded directly | HyDE: LLM writes a hypothetical answer, *that* is embedded |
| **Retrieval** | Top-K by cosine similarity | Top-K candidate pool → MMR re-ranking for diversity |
| **LLM context** | Child chunk text only | Parent chunk text (larger, richer context window) |
| **Observability** | Basic response | Response includes `hydeUsed`, `mmrUsed`, `hydeHypothesis`, `chunksBeforeReranking`, `chunksAfterReranking`, `latencyMs` |
| **Per-request tuning** | None | `hydeEnabled`, `mmrEnabled`, `mmrLambda`, `topK` overridable per request |
| **Configuration endpoint** | None | `GET /pipeline-info` exposes all active settings |

---

## Prerequisites

- Java 17+
- Maven 3.8+
- A [Qdrant](https://qdrant.tech) cluster (cloud free tier works)
- A Google Cloud project with Vertex AI API enabled
- Google Cloud credentials configured locally (`gcloud auth application-default login`)

---

## Run Locally

### 1. Clone and set environment variables

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

```
GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id
QDRANT_HOST=your-cluster.region.gcp.cloud.qdrant.io
QDRANT_API_KEY=your-qdrant-api-key
```

### 2. Authenticate with Google Cloud

```bash
gcloud auth application-default login
```

### 3. Build the project

```bash
mvn clean package -DskipTests
```

### 4. Run the app

**Option A — Maven:**
```bash
mvn spring-boot:run
```

**Option B — JAR directly:**
```bash
java -jar target/advanced-rag-1.0.0-SNAPSHOT.jar
```

**Option C — With env vars inline:**
```bash
GOOGLE_CLOUD_PROJECT_ID=my-project \
QDRANT_HOST=my-cluster.qdrant.io \
QDRANT_API_KEY=my-key \
java -jar target/advanced-rag-1.0.0-SNAPSHOT.jar
```

The app starts on **http://localhost:8080**.

### 5. Verify it's running

```bash
curl http://localhost:8080/api/v1/rag/health
curl http://localhost:8080/api/v1/rag/pipeline-info
```

### 6. Ingest a document

```bash
curl -X POST http://localhost:8080/api/v1/rag/ingest-document \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Spring AI is a framework that simplifies building AI-powered applications on the JVM. It provides abstractions for LLMs, embedding models, and vector stores.",
    "sourceName": "spring-ai-intro"
  }'
```

### 7. Query the pipeline

```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What does Spring AI simplify?"
  }'
```

**With per-request overrides:**
```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What does Spring AI simplify?",
    "hydeEnabled": false,
    "mmrEnabled": true,
    "mmrLambda": 0.7,
    "topK": 5
  }'
```

---

## Run on a Server / Cloud VM

### 1. Build a fat JAR locally

```bash
mvn clean package -DskipTests
```

### 2. Transfer to the server

```bash
scp target/advanced-rag-1.0.0-SNAPSHOT.jar user@your-server:/opt/advanced-rag/
```

### 3. Set environment variables on the server

Create `/opt/advanced-rag/.env`:
```
GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id
QDRANT_HOST=your-cluster.region.gcp.cloud.qdrant.io
QDRANT_API_KEY=your-qdrant-api-key
JAVA_OPTS=-Xmx400m -Xms200m
```

For Google credentials on a GCP VM, the default service account is used automatically. For non-GCP servers, copy your `application_default_credentials.json` and set:
```
GOOGLE_APPLICATION_CREDENTIALS=/opt/advanced-rag/credentials.json
```

### 4. Run as a background service

**Using systemd (recommended):**

Create `/etc/systemd/system/advanced-rag.service`:
```ini
[Unit]
Description=Advanced RAG Spring Boot App
After=network.target

[Service]
User=appuser
WorkingDirectory=/opt/advanced-rag
EnvironmentFile=/opt/advanced-rag/.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar advanced-rag-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable advanced-rag
sudo systemctl start advanced-rag
sudo systemctl status advanced-rag
```

**Or run directly in the background:**
```bash
export $(cat .env | xargs)
nohup java $JAVA_OPTS -jar advanced-rag-1.0.0-SNAPSHOT.jar > app.log 2>&1 &
```

### 5. Check logs

```bash
# systemd
sudo journalctl -u advanced-rag -f

# or tail the log file
tail -f app.log
```

---

## Configuration Reference

All RAG settings can be tuned in `src/main/resources/application.yml` under the `rag:` prefix:

| Key | Default | Description |
|---|---|---|
| `rag.chunking.child-chunk-size` | `256` | Token size for child chunks (stored in vector DB) |
| `rag.chunking.parent-chunk-size` | `1024` | Token size for parent chunks (sent to LLM) |
| `rag.retrieval.top-k-results` | `6` | Candidate pool size for MMR |
| `rag.retrieval.similarity-threshold` | `0.65` | Minimum similarity score to include a chunk |
| `rag.hyde.enabled` | `true` | Enable/disable HyDE query expansion |
| `rag.mmr.enabled` | `true` | Enable/disable MMR re-ranking |
| `rag.mmr.lambda` | `0.6` | MMR diversity weight (0=pure diversity, 1=pure relevance) |
| `rag.mmr.final-top-k` | `4` | Final number of chunks sent to LLM after MMR |

---

## Stack

- **Java 17** / **Spring Boot 3.3.0** / **Spring AI 1.0.7**
- **LLM**: Google Vertex AI — `gemini-1.5-flash`
- **Embeddings**: Google Vertex AI — `text-embedding-004` (768 dimensions)
- **Vector Store**: [Qdrant](https://qdrant.tech) (cloud or self-hosted)
- **Build**: Maven
