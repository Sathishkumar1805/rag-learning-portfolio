package com.sathish.rag.graph.service;

import com.sathish.rag.graph.config.GraphRagProperties;
import com.sathish.rag.graph.dto.IngestRequest;
import com.sathish.rag.graph.dto.IngestResponse;
import com.sathish.rag.graph.model.SubGraph;
import com.sathish.rag.graph.repository.InMemoryGraphRepository;
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
public class GraphIngestionService {

    private final VectorStore vectorStore;
    private final EntityExtractionService entityExtractionService;
    private final InMemoryGraphRepository graphRepository;
    private final GraphRagProperties properties;

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
        log.debug("Stored {} chunks in vector store", chunks.size());

        int totalEntities = 0;
        int totalRelationships = 0;

        for (Document chunk : chunks) {
            SubGraph subGraph = entityExtractionService.extractFromText(chunk.getText());
            subGraph.entities().forEach(graphRepository::addEntity);
            subGraph.relationships().forEach(graphRepository::addRelationship);
            totalEntities += subGraph.entities().size();
            totalRelationships += subGraph.relationships().size();
        }

        long latency = System.currentTimeMillis() - start;
        log.debug("Ingestion complete chunks={} entities={} relationships={} latencyMs={}",
                chunks.size(), totalEntities, totalRelationships, latency);

        return IngestResponse.builder()
                .source(request.getSource())
                .chunksStored(chunks.size())
                .entitiesExtracted(totalEntities)
                .relationshipsExtracted(totalRelationships)
                .latencyMs(latency)
                .build();
    }
}
