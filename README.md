# RAG Learning Portfolio

A hands-on progression through five Retrieval-Augmented Generation architectures, each app building on the last.
All apps use **Java 17**, **Spring Boot 3.3.0**, **Spring AI 1.0.7**, and **Google Vertex AI** (Gemini 1.5 Flash + text-embedding-004).

---

## The Apps

| # | Folder | Pattern | Port | Key Concept |
|---|--------|---------|------|-------------|
| 1 | `naive-rag` | Naive RAG | 8080 | Baseline: embed question → cosine search → generate |
| 2 | `app2-advanced-rag` | Advanced RAG | 8080 | HyDE query expansion + parent-child chunking + MMR re-ranking |
| 3 | `app3-modular-rag` | Modular RAG | 8081 | Pluggable pipeline steps + swappable retrieval strategy |
| 4 | `app4-graph-rag` | Graph RAG | 8082 | In-memory knowledge graph + BFS traversal + hybrid context |
| 5 | `app5-agentic-rag` | Agentic RAG | 8083 | ReAct agent loop — LLM decides which tool to call and when |

Each app compiles cleanly and ships with **3 focused unit tests** (Mockito only — no Spring context, no LLM calls).

---

## What is RAG?

**Retrieval-Augmented Generation** makes LLMs answer questions using your own documents:

1. **Ingest** — split documents into chunks, embed each chunk into a vector, store in a vector database.
2. **Retrieve** — at query time, embed the question and find the most similar chunks.
3. **Generate** — pass the retrieved chunks as context to the LLM; it answers using that specific information.

RAG prevents hallucination and keeps answers grounded in your data without retraining the model.

---

## Learning Progression

```
naive-rag         Baseline — one flat ingest → cosine search → LLM
    │
    ▼
app2-advanced-rag Adds HyDE (smarter query), parent-child chunking (richer context), MMR (diverse results)
    │
    ▼
app3-modular-rag  Refactors the 9-step monolith into a pluggable pipeline + strategy pattern for retrieval
    │
    ▼
app4-graph-rag    Adds a knowledge graph; entities and relationships extracted from documents feed BFS traversal
    │
    ▼
app5-agentic-rag  The LLM itself drives retrieval — an agent loop decides which tool to call and when to stop
```

---

## Prerequisites (All Apps)

| Tool | Minimum | Purpose |
|------|---------|---------|
| Java | 17 | Compile and runtime |
| Maven | 3.6+ | Build tool |
| Google Cloud SDK (`gcloud`) | latest | Vertex AI authentication |
| GCP project with Vertex AI enabled | — | Hosts Gemini + Embedding APIs |

### Enable Vertex AI on your GCP project

```bash
gcloud services enable aiplatform.googleapis.com --project=YOUR_PROJECT_ID
```

### Authenticate locally

```bash
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

---

## Quick Start — Run Any App

```bash
# 1. Clone
git clone https://github.com/Sathishkumar1805/rag-learning-portfolio.git
cd rag-learning-portfolio

# 2. Set your GCP project
export GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id   # Linux/macOS
# $env:GOOGLE_CLOUD_PROJECT_ID = "your-gcp-project-id"  # PowerShell

# 3. Pick an app and run it
cd app4-graph-rag
mvn spring-boot:run
```

Each app's folder has its own `README.md` with the full curl examples, configuration reference, and deployment guide.

---

## Running All Five Apps Simultaneously

Each app is on a different port — you can run them all at once:

```bash
# Open 5 terminals, one per app
cd naive-rag         && mvn spring-boot:run &  # :8080
cd app2-advanced-rag && mvn spring-boot:run &  # :8080  (same port — only run one at a time)
cd app3-modular-rag  && mvn spring-boot:run &  # :8081
cd app4-graph-rag    && mvn spring-boot:run &  # :8082
cd app5-agentic-rag  && mvn spring-boot:run &  # :8083
```

> **Note:** App 1 and App 2 both use port 8080 — run them separately.

---

## Running Tests

Each app has 3 pure unit tests (no network, no LLM):

```bash
# Single app
cd app4-graph-rag && mvn test

# All apps in sequence
for app in naive-rag app2-advanced-rag app3-modular-rag app4-graph-rag app5-agentic-rag; do
  echo "=== Testing $app ==="
  (cd $app && mvn test -q)
done
```

---

## Environment Variables

All apps share the same two required variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_CLOUD_PROJECT_ID` | Yes | GCP project with Vertex AI enabled |
| `GOOGLE_CLOUD_LOCATION` | No (default: `us-central1`) | Vertex AI region |
| `GOOGLE_APPLICATION_CREDENTIALS` | No* | Path to service account JSON key |

\* Not needed when using `gcloud auth application-default login` locally.

---

## Tech Stack

- **Java 17** — modern records, text blocks, pattern matching
- **Spring Boot 3.3.0** — auto-configuration, validation, actuator
- **Spring AI 1.0.7** — ChatModel, EmbeddingModel, VectorStore abstractions
- **Vertex AI Gemini 1.5 Flash** — LLM for generation and entity extraction
- **Vertex AI text-embedding-004** — 768-dimension embeddings
- **SimpleVectorStore** — in-memory vector store (Apps 3–5); no external infra required
- **Lombok** — boilerplate reduction
- **Mockito + AssertJ** — unit testing without Spring context
