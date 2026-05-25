package com.sathish.rag.agentic.service;

import com.sathish.rag.agentic.config.AgenticRagProperties;
import com.sathish.rag.agentic.dto.IngestRequest;
import com.sathish.rag.agentic.dto.IngestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final AgenticRagProperties properties;

    public IngestResponse ingest(IngestRequest request) {
        long start = System.currentTimeMillis();
        log.debug("Ingesting document source='{}'", request.getSource());

        int chunkSize = properties.getChunking().getChunkSize();
        int minChunkSize = properties.getChunking().getMinChunkSize();

        Document fullDoc = new Document(request.getContent(), Map.of("source", request.getSource()));
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, minChunkSize, 5, 10000, true);
        List<Document> chunks = splitter.apply(List.of(fullDoc));

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("source", request.getSource());
            chunks.get(i).getMetadata().put("chunkIndex", i);
        }

        vectorStore.add(chunks);
        long latency = System.currentTimeMillis() - start;
        log.debug("Stored {} chunks latencyMs={}", chunks.size(), latency);

        return IngestResponse.builder()
                .source(request.getSource())
                .chunksStored(chunks.size())
                .latencyMs(latency)
                .build();
    }
}
