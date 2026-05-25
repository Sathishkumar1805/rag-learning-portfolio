/**
 * RagQueryServiceTest.java
 *
 * Unit tests for App 5 (Agentic RAG). Verifies:
 *   1. RagTools.searchDocuments returns formatted text from mocked VectorStore results.
 *   2. RagTools.searchDocumentsBySource filters results by source metadata.
 *   3. AgentQueryService returns FINAL_ANSWER when the LLM responds directly without tool calls.
 *
 * Uses Mockito exclusively — no Spring context loaded, no real LLM or vector store calls.
 */
package com.sathish.rag.agentic;

import com.sathish.rag.agentic.config.AgenticRagProperties;
import com.sathish.rag.agentic.dto.QueryRequest;
import com.sathish.rag.agentic.dto.QueryResponse;
import com.sathish.rag.agentic.service.AgentQueryService;
import com.sathish.rag.agentic.service.RagTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatModel chatModel;

    private AgenticRagProperties properties;
    private RagTools ragTools;

    @BeforeEach
    void setUp() {
        properties = new AgenticRagProperties();
        ragTools = new RagTools(vectorStore, properties);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: searchDocuments returns formatted result containing document text
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 1: searchDocuments returns formatted results from VectorStore")
    void testSearchDocumentsReturnsFormattedResults() {
        Document doc1 = new Document("RAG combines retrieval with generation.",
                Map.of("source", "rag-intro.txt"));
        Document doc2 = new Document("Vector stores enable semantic search.",
                Map.of("source", "vector-stores.txt"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        String result = ragTools.searchDocuments("What is RAG?", 5);

        assertThat(result).contains("RAG combines retrieval with generation.");
        assertThat(result).contains("Vector stores enable semantic search.");
        assertThat(result).contains("rag-intro.txt");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: searchDocumentsBySource filters results by source metadata value
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 2: searchDocumentsBySource filters by source metadata")
    void testSearchDocumentsBySourceFiltersCorrectly() {
        Document matchDoc = new Document("RAG paper main content.", Map.of("source", "rag-paper.txt"));
        Document otherDoc = new Document("Unrelated document.", Map.of("source", "other.txt"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(matchDoc, otherDoc));

        String result = ragTools.searchDocumentsBySource("rag-paper.txt", 5);

        assertThat(result).contains("RAG paper main content.");
        assertThat(result).doesNotContain("Unrelated document.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: AgentQueryService extracts FINAL_ANSWER when LLM responds directly
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 3: AgentQueryService returns answer when LLM gives FINAL_ANSWER immediately")
    void testAgentReturnsFinalAnswerWithoutToolCalls() {
        when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText())
                .thenReturn("FINAL_ANSWER: RAG stands for Retrieval-Augmented Generation.");

        AgentQueryService agentService = new AgentQueryService(chatModel, ragTools, properties);
        QueryResponse response = agentService.query(
                QueryRequest.builder().question("What is RAG?").build());

        assertThat(response.getAnswer()).isEqualTo("RAG stands for Retrieval-Augmented Generation.");
        assertThat(response.getQuestion()).isEqualTo("What is RAG?");
        assertThat(response.getSteps()).isEmpty();
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}
