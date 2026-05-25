# App 4 — Graph RAG

**Spring Boot · Spring AI 1.0.7 · Gemini 1.5 Flash · SimpleVectorStore · In-Memory Knowledge Graph**

App 4 augments vector search with a **knowledge graph**. Documents are not only chunked and embedded
— they are also parsed by an LLM to extract named entities and the relationships between them.
At query time, retrieval comes from two sources: the vector store (semantic) and a BFS traversal of the
graph starting from entities mentioned in the question (relational). The LLM receives both sections of
context, producing answers that understand not just similarity but connections.

---

## Core Concepts

### What is Graph RAG?

Standard RAG retrieves document chunks that are *similar* to the question. It cannot answer questions
like "Who does Alice report to?" or "Which technologies are related to Spring AI?" — because that
knowledge lives in relationships, not in individual chunk texts.

Graph RAG solves this by extracting a **knowledge graph** during ingestion:

```
"Alice works at Acme Corp. She reports to Bob, the VP of Engineering."

Entities:   Alice (PERSON), Acme Corp (ORGANIZATION), Bob (PERSON)
Relations:  Alice --[WORKS_AT]--> Acme Corp
            Alice --[REPORTS_TO]--> Bob
            Bob   --[ROLE_AT]--> Acme Corp
```

At query time, entity names from the question seed a **BFS (Breadth-First Search) traversal** that fans
out across the graph to find connected entities and relationships up to N hops away. This graph context
is merged with vector results and sent to the LLM together.

### BFS Traversal

Given seed entities `{Alice}` and `maxHops=2`:

```
Hop 0:  Alice
Hop 1:  Alice --[WORKS_AT]--> Acme Corp
        Alice --[REPORTS_TO]--> Bob
Hop 2:  Bob --[MANAGES]--> Engineering Team
        ...
```

Each hop discovers entities reachable via any relationship edge (traversed bidirectionally).
The result is a `SubGraph` containing all entities and relationships found within the hop limit.

### Entity Extraction via LLM

For each document chunk, the app calls Gemini with a structured prompt:

```
Input:  "Spring AI is a framework developed by Broadcom that integrates with Vertex AI."

Output: {
  "entities": [
    {"name": "Spring AI",  "type": "TECHNOLOGY",    "description": "AI framework for Spring"},
    {"name": "Broadcom",   "type": "ORGANIZATION",  "description": "Software company"},
    {"name": "Vertex AI",  "type": "TECHNOLOGY",    "description": "Google AI platform"}
  ],
  "relationships": [
    {"fromEntity": "Spring AI", "toEntity": "Broadcom",   "relationType": "DEVELOPED_BY"},
    {"fromEntity": "Spring AI", "toEntity": "Vertex AI",  "relationType": "INTEGRATES_WITH"}
  ]
}
```

The LLM response is stripped of markdown fences and parsed with Jackson.
On parse failure the service returns an empty SubGraph (graceful degradation — vector retrieval still works).

### Hybrid Context Block

The final prompt to the LLM contains two clearly labelled sections:

```
=== VECTOR CONTEXT ===
[1] (source: spring-ai-docs.txt)
Spring AI provides abstractions for Chat Models, Embedding Models...

=== GRAPH CONTEXT ===
Entities:
- Spring AI [TECHNOLOGY]: AI framework for the JVM
- Broadcom [ORGANIZATION]: Software company

Relationships:
- Spring AI --[DEVELOPED_BY]--> Broadcom
- Spring AI --[INTEGRATES_WITH]--> Vertex AI
```

---

## How App 4 Differs from Apps 1–3

| Dimension | Apps 1–3 | App 4 |
|-----------|----------|-------|
| Retrieval type | Vector only (semantic similarity) | Vector + Graph (semantic + relational) |
| Ingestion | Chunk → embed → store | Chunk → embed → store **+ LLM entity extraction → graph** |
| Knowledge representation | Flat document chunks | Chunks + entity/relationship graph |
| Query approach | Embed question → cosine search | Vector search + entity detection → BFS traversal |
| Context to LLM | Vector chunks only | Vector section + graph entities/relationships section |
| New endpoints | — | `GET /graph/entities`, `GET /graph/stats` |
| Persistence | In-memory (lost on restart) | In-memory (lost on restart) |

---

## Package Structure

```
com.sathish.rag.graph
├── config/
│   ├── GraphRagProperties.java     — @ConfigurationProperties for all rag.* keys
│   └── VectorStoreConfig.java      — SimpleVectorStore bean
├── model/
│   ├── Entity.java                 — record(name, type, description)
│   ├── Relationship.java           — record(fromEntity, toEntity, relationType, description)
│   └── SubGraph.java               — record(entities, relationships) + isEmpty()
├── repository/
│   └── InMemoryGraphRepository.java — ConcurrentHashMap store + BFS traversal
├── service/
│   ├── EntityExtractionService.java — LLM call → JSON parse → SubGraph
│   ├── GraphIngestionService.java   — chunk + vectorStore.add() + extract entities
│   └── RagQueryService.java         — vector search + entity BFS + merge context + LLM
├── controller/
│   └── RagController.java           — REST endpoints
└── dto/
    ├── IngestRequest/Response.java
    └── QueryRequest/Response.java   — includes vectorHits, graphEntityHits, graphRelationshipHits
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/rag/ingest` | Ingest document: chunk + embed + extract entities/relationships |
| `POST` | `/api/v1/rag/query` | Hybrid query: vector + graph context → LLM answer |
| `GET` | `/api/v1/rag/graph/entities` | List all entities currently in the graph |
| `GET` | `/api/v1/rag/graph/stats` | Entity count, relationship count, BFS config |
| `GET` | `/api/v1/rag/health` | Liveness check |

### Ingest Request

```json
{
  "content": "Spring AI is developed by Broadcom. It integrates with Google Vertex AI.",
  "source": "spring-ai-overview"
}
```

### Query Request / Response

```json
// Request
{ "question": "What is the relationship between Spring AI and Google?" }

// Response
{
  "question": "What is the relationship between Spring AI and Google?",
  "answer": "Spring AI integrates with Google Vertex AI for LLM and embedding access...",
  "vectorHits": 3,
  "graphEntityHits": 4,
  "graphRelationshipHits": 3,
  "latencyMs": 983
}
```

---

## Configuration Reference

```yaml
rag:
  chunking:
    chunk-size: 500       # tokens per chunk fed to entity extraction
    min-chunk-size: 100
  graph:
    max-bfs-hops: 2       # depth of BFS traversal from seed entities
  query:
    top-k: 5              # number of vector search results
```

---

## Prerequisites

- Java 17+
- Maven 3.6+
- Google Cloud project with Vertex AI enabled (`gcloud services enable aiplatform.googleapis.com`)
- `gcloud auth application-default login` completed

---

## Run Locally

### 1. Authenticate

```bash
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

### 2. Set environment variable

```bash
# Linux / macOS
export GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id

# Windows PowerShell
$env:GOOGLE_CLOUD_PROJECT_ID = "your-gcp-project-id"
```

### 3. Run

```bash
cd app4-graph-rag
mvn spring-boot:run
# or: mvn clean package -DskipTests && java -jar target/graph-rag-1.0.0-SNAPSHOT.jar
```

App starts on **http://localhost:8082**.

### 4. Try it out

```bash
# Health check
curl http://localhost:8082/api/v1/rag/health

# Ingest a document
curl -X POST http://localhost:8082/api/v1/rag/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Spring AI is an open-source framework developed by Broadcom. It integrates with Google Vertex AI for embeddings and chat completions. Pivotal engineers contribute to Spring AI.",
    "source": "spring-ai-overview"
  }'

# Query (graph traversal finds Broadcom and Vertex AI as related entities)
curl -X POST http://localhost:8082/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Who is behind Spring AI and what does it use?"}'

# Inspect the graph
curl http://localhost:8082/api/v1/rag/graph/entities
curl http://localhost:8082/api/v1/rag/graph/stats
```

### 5. Run tests

```bash
mvn test
# Tests run in ~5 seconds with no network calls (Mockito only)
```

---

## Deploy to a Server

App 4 has no external infrastructure dependencies.
The only external service is Google Vertex AI.

> **Persistence note:** SimpleVectorStore and InMemoryGraphRepository are both in-memory.
> All ingested data is lost on restart. For production, replace with Qdrant + Neo4j.

---

### Option A — Docker

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/graph-rag-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

```bash
mvn clean package -DskipTests
docker build -t graph-rag:latest .
docker run -p 8082:8082 \
  -e GOOGLE_CLOUD_PROJECT_ID=your-project-id \
  -v "$HOME/.config/gcloud:/root/.config/gcloud:ro" \
  graph-rag:latest
```

---

### Option B — Google Cloud Run

```bash
PROJECT_ID=your-project-id
REGION=us-central1
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/rag-apps/graph-rag:latest

mvn clean package -DskipTests
docker build -t $IMAGE .
gcloud auth configure-docker $REGION-docker.pkg.dev
docker push $IMAGE

# Service account with Vertex AI access
gcloud iam service-accounts create graph-rag-sa
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:graph-rag-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# Deploy
gcloud run deploy graph-rag \
  --image=$IMAGE --region=$REGION \
  --allow-unauthenticated --port=8082 \
  --memory=1Gi --cpu=1 \
  --service-account=graph-rag-sa@$PROJECT_ID.iam.gserviceaccount.com \
  --set-env-vars="GOOGLE_CLOUD_PROJECT_ID=$PROJECT_ID,GOOGLE_CLOUD_LOCATION=$REGION"
```

---

### Option C — Linux VM (systemd)

```bash
# Install Java, copy JAR, then create /etc/systemd/system/graph-rag.service:
```

```ini
[Unit]
Description=Graph RAG Spring Boot App
After=network.target

[Service]
User=appuser
WorkingDirectory=/opt/graph-rag
Environment=GOOGLE_CLOUD_PROJECT_ID=your-project-id
Environment=GOOGLE_CLOUD_LOCATION=us-central1
Environment=GOOGLE_APPLICATION_CREDENTIALS=/opt/graph-rag/sa-key.json
ExecStart=/usr/bin/java -Xmx512m -jar /opt/graph-rag/graph-rag-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now graph-rag
sudo journalctl -u graph-rag -f
```

---

## Learning Progression

```
App 1 — naive-rag          Baseline: flat chunk → embed → cosine search → generate
App 2 — advanced-rag       HyDE + parent-child chunking + MMR
App 3 — modular-rag        Pluggable pipeline + strategy pattern
App 4 — graph-rag    ◄     Adds knowledge graph extraction + BFS hybrid retrieval
App 5 — agentic-rag        Autonomous agent decides when and what to retrieve
```
