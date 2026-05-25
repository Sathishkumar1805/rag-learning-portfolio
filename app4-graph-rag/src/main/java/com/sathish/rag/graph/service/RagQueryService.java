package com.sathish.rag.graph.service;

import com.sathish.rag.graph.config.GraphRagProperties;
import com.sathish.rag.graph.dto.QueryRequest;
import com.sathish.rag.graph.dto.QueryResponse;
import com.sathish.rag.graph.model.Entity;
import com.sathish.rag.graph.model.Relationship;
import com.sathish.rag.graph.model.SubGraph;
import com.sathish.rag.graph.repository.InMemoryGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    private final VectorStore vectorStore;
    private final InMemoryGraphRepository graphRepository;
    private final EntityExtractionService entityExtractionService;
    private final ChatModel chatModel;
    private final GraphRagProperties properties;

    public QueryResponse query(QueryRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getQuestion();
        log.debug("RAG query question='{}'", question);

        // 1. Vector search
        int topK = properties.getQuery().getTopK();
        List<Document> vectorDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        log.debug("Vector search returned {} docs", vectorDocs.size());

        // 2. Entity detection → BFS traversal
        Set<String> mentionedEntities = graphRepository.findEntityNamesMentionedIn(question);
        int maxHops = properties.getGraph().getMaxBfsHops();
        SubGraph subGraph = graphRepository.bfsTraversal(mentionedEntities, maxHops);
        log.debug("Graph traversal: seeds={} entities={} relationships={}",
                mentionedEntities.size(), subGraph.entities().size(), subGraph.relationships().size());

        // 3. Build context
        String context = buildContext(vectorDocs, subGraph);

        // 4. Generate answer
        String systemPrompt = properties.getQuery().getSystemPrompt();
        String userContent = "Context:\n" + context + "\n\nQuestion: " + question;
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userContent)));
        String answer = chatModel.call(prompt).getResult().getOutput().getText();

        long latency = System.currentTimeMillis() - start;
        return QueryResponse.builder()
                .question(question)
                .answer(answer)
                .vectorHits(vectorDocs.size())
                .graphEntityHits(subGraph.entities().size())
                .graphRelationshipHits(subGraph.relationships().size())
                .latencyMs(latency)
                .build();
    }

    private String buildContext(List<Document> vectorDocs, SubGraph subGraph) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== VECTOR CONTEXT ===\n");
        if (vectorDocs.isEmpty()) {
            sb.append("(no vector results)\n");
        } else {
            for (int i = 0; i < vectorDocs.size(); i++) {
                Document doc = vectorDocs.get(i);
                Object source = doc.getMetadata().get("source");
                sb.append("[").append(i + 1).append("]");
                if (source != null) sb.append(" (").append(source).append(")");
                sb.append("\n").append(doc.getText()).append("\n\n");
            }
        }

        sb.append("=== GRAPH CONTEXT ===\n");
        if (subGraph.isEmpty()) {
            sb.append("(no graph results)\n");
        } else {
            sb.append("Entities:\n");
            for (Entity e : subGraph.entities()) {
                sb.append("- ").append(e.name()).append(" [").append(e.type()).append("]: ")
                        .append(e.description()).append("\n");
            }
            sb.append("Relationships:\n");
            for (Relationship r : subGraph.relationships()) {
                sb.append("- ").append(r.fromEntity()).append(" --[").append(r.relationType()).append("]--> ")
                        .append(r.toEntity()).append(": ").append(r.description()).append("\n");
            }
        }

        return sb.toString();
    }
}
