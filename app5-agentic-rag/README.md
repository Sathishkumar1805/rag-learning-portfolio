# App 5 — Agentic RAG

**Spring Boot · Spring AI 1.0.7 · Gemini 1.5 Flash · SimpleVectorStore · Manual ReAct Agent Loop**

App 5 inverts the control flow of all previous apps. Rather than the application driving retrieval
with a fixed sequence of steps, the **LLM itself decides** what to retrieve, when to retrieve it,
and when it has gathered enough information to answer. This is implemented as a manual ReAct
(Reasoning + Acting) loop — no LangChain4j, no framework magic.

---

## Core Concepts

### What is Agentic RAG?

In Apps 1–4, the retrieval strategy is fixed at the application level:

```
App 4 flow (fixed):
  question → vector search → entity BFS → merge context → LLM → answer
```

In App 5, the LLM drives the loop:

```
App 5 flow (agent-driven):
  question → LLM thinks → decides to call searchDocuments → gets results
           → LLM thinks → decides to call searchDocumentsBySource → gets results
           → LLM decides it has enough → FINAL_ANSWER: ...
```

The application provides tools. The LLM decides which tool to use, with what arguments, and
when to stop. This enables multi-hop reasoning — e.g., first decompose a complex question, then
search for each sub-question separately, then synthesise a final answer.

### The ReAct Pattern

**ReAct** (Reasoning + Acting) is a prompting pattern where the model alternates between
**thinking** (reasoning about what to do) and **acting** (calling a tool):

```
Thought: I need to find what Spring AI is first.
Action:  TOOL_CALL: searchDocuments({"query": "Spring AI framework overview", "topK": 3})
Observation: [1] Spring AI is an open-source framework by Broadcom...

Thought: Now I need to find who developed it specifically.
Action:  TOOL_CALL: searchDocuments({"query": "Spring AI Broadcom developer", "topK": 2})
Observation: [1] Broadcom engineers maintain Spring AI...

Thought: I have enough information to answer.
Action:  FINAL_ANSWER: Spring AI is an open-source framework developed and maintained by Broadcom...
```

### The Agent Loop (AgentQueryService)

The loop runs for up to `max-iterations` turns:

```
1. Build messages:  [SystemMessage(tool descriptions + directives), UserMessage(question)]
2. Loop:
   a. Call LLM with current messages
   b. Parse response:
      - Contains "FINAL_ANSWER:" → extract answer, return QueryResponse
      - Contains "TOOL_CALL: name({...})" → dispatch tool, get observation
      - Neither → append response, ask LLM to continue
   c. Append AssistantMessage(response) + UserMessage("Tool result: " + observation)
   d. Increment iteration counter
3. If max iterations reached → return "Unable to answer within N iterations"
```

### Tool Dispatch

The system prompt tells the LLM to respond with exactly:

```
TOOL_CALL: toolName({"param": "value", "param2": value})
```

`AgentQueryService` parses this with a simple string search (no regex engine or reflection).
Arguments are parsed with Jackson `ObjectMapper`. The dispatch is a plain Java `switch`:

```java
switch (toolCall.toolName()) {
    case "searchDocuments"         -> ragTools.searchDocuments(query, topK);
    case "searchDocumentsBySource" -> ragTools.searchDocumentsBySource(source, topK);
    case "listAvailableDocuments"  -> ragTools.listAvailableDocuments();
    case "decomposeQuestion"       -> ragTools.decomposeQuestion(question);
    default -> "Unknown tool: " + toolCall.toolName();
}
```

### Available Tools

| Tool | Arguments | What it does |
|------|-----------|--------------|
| `searchDocuments` | `query` (string), `topK` (int) | Vector similarity search across all ingested documents |
| `searchDocumentsBySource` | `source` (string), `topK` (int) | Searches then filters by `source` metadata |
| `listAvailableDocuments` | — | Returns distinct source names currently in the vector store |
| `decomposeQuestion` | `question` (string) | Splits a compound question into simpler sub-questions |

---

## How App 5 Differs from Apps 1–4

| Dimension | Apps 1–4 | App 5 |
|-----------|----------|-------|
| Who drives retrieval | Application code | The LLM agent |
| Retrieval strategy | Fixed at startup | Dynamic — agent chooses which tool and when |
| Number of retrieval rounds | 1 | 1 to `max-iterations` (default 5) |
| Multi-hop reasoning | No | Yes — agent can search multiple times |
| Tool calls | N/A | `searchDocuments`, `searchDocumentsBySource`, `listAvailableDocuments`, `decomposeQuestion` |
| Response includes | answer + hits count | answer + `steps[]` (each tool call + observation) + `iterationsUsed` |
| Failure mode | Returns best-effort answer | Returns "Unable to answer" after max iterations |

---

## Package Structure

```
com.sathish.rag.agentic
├── config/
│   ├── AgenticRagProperties.java   — @ConfigurationProperties for rag.agent.* and rag.chunking.*
│   └── VectorStoreConfig.java      — SimpleVectorStore bean
├── model/
│   ├── AgentStep.java              — record(toolName, toolInput, observation)
│   └── ToolCall.java               — record(toolName, argsJson) — parsed from LLM response
├── service/
│   ├── RagTools.java               — the 4 retrieval tools the agent can call
│   ├── AgentQueryService.java      — ReAct loop: call LLM → parse → dispatch → repeat
│   └── DocumentIngestionService.java — chunk + embed + store
├── controller/
│   └── RagController.java          — REST endpoints
└── dto/
    ├── IngestRequest/Response.java
    └── QueryRequest/Response.java  — includes steps[], iterationsUsed, latencyMs
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/rag/ingest` | Ingest document: chunk and store in vector store |
| `POST` | `/api/v1/rag/query` | Run agent loop: LLM decides tools, returns answer + step trace |
| `GET` | `/api/v1/rag/agent-info` | Active config, tool list, improvements vs previous apps |
| `GET` | `/api/v1/rag/health` | Liveness check |

### Ingest Request

```json
{
  "content": "Spring AI is developed by Broadcom. Gemini 1.5 Flash is Google's fast LLM.",
  "source": "llm-overview"
}
```

### Query Request

```json
{
  "question": "Who makes Spring AI and which LLM model does it use in this project?"
}
```

### Query Response

```json
{
  "question": "Who makes Spring AI and which LLM model does it use in this project?",
  "answer": "Spring AI is developed by Broadcom. This project uses Gemini 1.5 Flash from Google.",
  "steps": [
    {
      "toolName": "searchDocuments",
      "toolInput": "{\"query\": \"Spring AI developer\", \"topK\": 3}",
      "observation": "[1] Source: llm-overview\nSpring AI is developed by Broadcom..."
    },
    {
      "toolName": "searchDocuments",
      "toolInput": "{\"query\": \"LLM model Gemini\", \"topK\": 3}",
      "observation": "[1] Source: llm-overview\nGemini 1.5 Flash is Google fast LLM..."
    }
  ],
  "iterationsUsed": 2,
  "latencyMs": 3240
}
```

---

## Configuration Reference

```yaml
rag:
  agent:
    max-iterations: 5      # max tool call rounds before giving up
    search-top-k: 5        # default topK if agent does not specify
  chunking:
    chunk-size: 500
    min-chunk-size: 100
```

---

## Prerequisites

- Java 17+
- Maven 3.6+
- Google Cloud project with Vertex AI enabled
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
cd app5-agentic-rag
mvn spring-boot:run
# or: mvn clean package -DskipTests && java -jar target/agentic-rag-1.0.0-SNAPSHOT.jar
```

App starts on **http://localhost:8083**.

### 4. Try it out

```bash
# Health check
curl http://localhost:8083/api/v1/rag/health

# See agent configuration
curl http://localhost:8083/api/v1/rag/agent-info

# Ingest documents
curl -X POST http://localhost:8083/api/v1/rag/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Spring AI is an open-source Java framework developed by Broadcom. It provides abstractions for working with LLMs, embedding models, and vector stores. This project uses Gemini 1.5 Flash for text generation.",
    "source": "project-overview"
  }'

# Ask a question that requires multiple tool calls
curl -X POST http://localhost:8083/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What framework is used and who made it?"}'
```

The response `steps[]` array shows every tool the agent called and what it got back.

### 5. Run tests

```bash
mvn test
# Tests run in ~5 seconds with no network calls (Mockito only)
```

---

## Deploy to a Server

App 5 has no external infrastructure dependencies beyond Google Vertex AI.

> **Persistence note:** SimpleVectorStore is in-memory. All ingested documents are lost on restart.
> For production, replace `VectorStoreConfig` with a persistent vector store (Qdrant, Weaviate, etc.).

---

### Option A — Docker

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/agentic-rag-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

```bash
mvn clean package -DskipTests
docker build -t agentic-rag:latest .
docker run -p 8083:8083 \
  -e GOOGLE_CLOUD_PROJECT_ID=your-project-id \
  -v "$HOME/.config/gcloud:/root/.config/gcloud:ro" \
  agentic-rag:latest
```

---

### Option B — Google Cloud Run

```bash
PROJECT_ID=your-project-id
REGION=us-central1
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/rag-apps/agentic-rag:latest

mvn clean package -DskipTests
docker build -t $IMAGE .
gcloud auth configure-docker $REGION-docker.pkg.dev
docker push $IMAGE

# Service account
gcloud iam service-accounts create agentic-rag-sa
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:agentic-rag-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# Deploy
gcloud run deploy agentic-rag \
  --image=$IMAGE --region=$REGION \
  --allow-unauthenticated --port=8083 \
  --memory=1Gi --cpu=1 \
  --timeout=300 \
  --service-account=agentic-rag-sa@$PROJECT_ID.iam.gserviceaccount.com \
  --set-env-vars="GOOGLE_CLOUD_PROJECT_ID=$PROJECT_ID,GOOGLE_CLOUD_LOCATION=$REGION"
```

> Set `--timeout=300` because agent queries can take several seconds per iteration.

---

### Option C — Linux VM (systemd)

```bash
# Install Java, copy JAR, create /etc/systemd/system/agentic-rag.service:
```

```ini
[Unit]
Description=Agentic RAG Spring Boot App
After=network.target

[Service]
User=appuser
WorkingDirectory=/opt/agentic-rag
Environment=GOOGLE_CLOUD_PROJECT_ID=your-project-id
Environment=GOOGLE_CLOUD_LOCATION=us-central1
Environment=GOOGLE_APPLICATION_CREDENTIALS=/opt/agentic-rag/sa-key.json
ExecStart=/usr/bin/java -Xmx512m -jar /opt/agentic-rag/agentic-rag-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now agentic-rag
sudo journalctl -u agentic-rag -f
```

---

## Tuning Guide

| Goal | Change |
|------|--------|
| Allow more tool calls per query | Increase `rag.agent.max-iterations` |
| Faster responses (fewer LLM round-trips) | Decrease `max-iterations` (agent stops sooner) |
| More documents per tool call | Increase `rag.agent.search-top-k` |
| Change agent behaviour entirely | Edit `rag.agent.system-prompt` in `application.yml` |

---

## Learning Progression

```
App 1 — naive-rag          Baseline: flat chunk → embed → cosine search → generate
App 2 — advanced-rag       HyDE + parent-child chunking + MMR
App 3 — modular-rag        Pluggable pipeline + strategy pattern
App 4 — graph-rag          Knowledge graph + BFS hybrid retrieval
App 5 — agentic-rag  ◄     Agent loop — LLM drives retrieval with multi-hop reasoning
```
