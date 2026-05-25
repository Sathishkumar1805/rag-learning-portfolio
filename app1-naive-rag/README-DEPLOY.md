# Naive RAG — Render.com Deployment Guide
**Free Tier Deployment | $0/month**

---

## Render.com Free Tier Facts

| Resource | Limit | Impact on Naive RAG |
|----------|-------|---------------------|
| Compute hours | 750 hrs/month | Enough for 1 always-on service |
| RAM | 512 MB | ~200 chunks max in SimpleVectorStore |
| CPU | Shared | Cold start ~30s after 15min inactivity |
| Filesystem | **Ephemeral** | Snapshot lost on every redeploy |
| Bandwidth | 100 GB/month | More than enough |
| Custom domains | 1 free | yourapp.onrender.com |

**Key constraint:** Ephemeral filesystem means the vector store snapshot (JSON file)
is destroyed on every Render redeploy. You must re-ingest documents after each deploy.
Advanced RAG (App #2) solves this with Qdrant Cloud (free tier, persistent).

---

## Pre-Deployment Checklist

- [ ] App builds locally: `mvn clean package -DskipTests`
- [ ] Health check passes: `curl localhost:8080/api/v1/rag/health`
- [ ] Google Cloud project has Vertex AI API enabled
- [ ] Service Account created with `Vertex AI User` role

---

## Google Cloud Service Account Setup

Render.com cannot use `gcloud auth application-default login`.
You need a Service Account JSON key:

```bash
# Create service account
gcloud iam service-accounts create naive-rag-sa \
  --display-name="Naive RAG Service Account"

# Grant Vertex AI access
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:naive-rag-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"

# Create and download key (keep this file secret!)
gcloud iam service-accounts keys create ./service-account-key.json \
  --iam-account=naive-rag-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com

# Base64-encode the key for Render env var
base64 -i service-account-key.json
# Copy the output — paste as GOOGLE_APPLICATION_CREDENTIALS_JSON in Render
```

**IMPORTANT:** Add `service-account-key.json` to `.gitignore` immediately.

---

## Render.com Deployment Steps

### Step 1: Push to GitHub
```bash
git init
echo "service-account-key.json" >> .gitignore
echo "vector-store-snapshot.json" >> .gitignore
echo ".env" >> .gitignore
git add .
git commit -m "feat: naive rag app 1 of 10"
git remote add origin https://github.com/YOUR_USERNAME/naive-rag.git
git push -u origin main
```

### Step 2: Create Render Web Service
1. Go to https://render.com → New → Web Service
2. Connect GitHub repository
3. Configure:

| Field | Value |
|-------|-------|
| Name | `naive-rag` |
| Region | Oregon (US West) — closest to Vertex AI us-central1 |
| Branch | `main` |
| Runtime | **Java** |
| Build Command | `mvn clean package -DskipTests` |
| Start Command | `java -jar target/naive-rag-1.0.0-SNAPSHOT.jar` |
| Instance Type | **Free** |
| Health Check Path | `/api/v1/rag/health` |

### Step 3: Set Environment Variables

In Render dashboard → Environment → Add the following:

| Variable | Value | Notes |
|----------|-------|-------|
| `GOOGLE_CLOUD_PROJECT_ID` | `your-project-id` | From GCP console |
| `GOOGLE_CLOUD_LOCATION` | `us-central1` | Nearest Gemini region |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | `<base64 key>` | From Step above |
| `RAG_SNAPSHOT_PATH` | `/tmp/vector-store-snapshot.json` | /tmp survives restarts (not redeploys) |
| `PORT` | `8080` | Render sets this automatically but explicit is safer |
| `JAVA_OPTS` | `-Xmx400m -Xms128m` | Stay within 512MB RAM limit |

### Step 4: Configure ADC for Render

Since Render can't run `gcloud auth`, add a startup script to load the SA key:

Create `src/main/resources/render-startup.sh`:
```bash
#!/bin/bash
# Decode the base64 service account key and set GOOGLE_APPLICATION_CREDENTIALS
if [ -n "$GOOGLE_APPLICATION_CREDENTIALS_JSON" ]; then
  echo "$GOOGLE_APPLICATION_CREDENTIALS_JSON" | base64 --decode > /tmp/sa-key.json
  export GOOGLE_APPLICATION_CREDENTIALS=/tmp/sa-key.json
fi
exec java $JAVA_OPTS -jar target/naive-rag-1.0.0-SNAPSHOT.jar
```

Update Start Command to: `bash src/main/resources/render-startup.sh`

---

## Cost Breakdown

| Service | Tier | Cost/Month |
|---------|------|-----------|
| Render.com Web Service | Free (750 hrs) | **$0** |
| Gemini 1.5 Flash | Free (15 RPM, 1500/day) | **$0** |
| text-embedding-004 | Free (1500 RPM) | **$0** |
| Google Cloud Vertex AI API | Free tier + $300 credit | **$0** |
| **Total** | | **$0** |

**When you'd start paying:**
- More than 750 Render compute-hours/month → $7/month for Starter
- More than 1,500 Gemini requests/day → ~$0.075 per 1M tokens (Flash is very cheap)

---

## Post-Deploy Testing

After deployment, replace `localhost:8080` with your Render URL:

```bash
RENDER_URL=https://naive-rag.onrender.com

# Health check
curl $RENDER_URL/api/v1/rag/health

# Ingest (must do after every redeploy due to ephemeral FS)
curl -X POST $RENDER_URL/api/v1/rag/ingest-document \
  -H "Content-Type: application/json" \
  -d '{"source":"Test Doc","content":"Spring Boot is an opinionated framework for building Spring applications quickly."}'

# Query
curl -X POST $RENDER_URL/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question":"What is Spring Boot?"}'
```

---

## Render Free Tier Gotchas

1. **Cold starts:** Free tier spins down after 15 minutes of inactivity.
   First request after sleep takes ~30 seconds. Expected behavior, not a bug.

2. **Ephemeral filesystem:** The vector store snapshot in `/tmp` survives restarts
   but NOT redeploys. Build a `/api/v1/rag/ingest-batch` endpoint for re-seeding
   from a bundled JSON file in `src/main/resources/` if needed.

3. **512MB RAM:** SimpleVectorStore stores all vectors in heap.
   Each float[768] vector (Gemini embedding-004 dimension) = 3KB.
   500 chunks × 3KB = 1.5MB — well within limits. 10,000 chunks = 30MB — still fine.
   The JVM itself uses ~200MB, leaving ~300MB for vectors.

4. **Build timeout:** Render free tier builds time out at 15 minutes.
   Maven first-run downloads ~200MB of dependencies. Subsequent builds use cache.

---

## Upgrading to Next RAG App

When you're ready for **App #2: Advanced RAG**, the key infrastructure changes are:
- SimpleVectorStore → Qdrant Cloud (free tier, 1GB storage, persistent)
- Render free tier can stay the same
- Gemini stays the same, but we add HyDE query expansion (2 LLM calls per query)
- Total cost: still **$0**
