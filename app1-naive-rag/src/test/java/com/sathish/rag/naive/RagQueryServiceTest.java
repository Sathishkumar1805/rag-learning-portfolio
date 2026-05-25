/**
 * RagQueryServiceTest.java
 *
 * <p><b>RAG Role:</b> Unit tests for the query pipeline service.
 * Mocks out the VectorStore and ChatModel so tests run without hitting
 * the Gemini API — fast, free, and reliable in CI.
 *
 * <p><b>Learning Note:</b>
 * Testing RAG services requires mocking at two points:
 * 1. VectorStore.similaritySearch — control which chunks are "retrieved"
 * 2. ChatModel.call (or ChatClient) — control what the LLM "answers"
 * This lets you test prompt formatting, empty-context handling, and response
 * mapping without any API calls or tokens spent.
 *
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.naive;

import com.sathish.rag.naive.config.RagProperties;
import com.sathish.rag.naive.dto.QueryRequest;
import com.sathish.rag.naive.dto.QueryResponse;
import com.sathish.rag.naive.service.RagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RagQueryService} — no Gemini API calls, fully mocked.
 */
@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    private RagProperties ragProperties;
    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        // Use default values (topK=4, threshold=0.0, standard system prompt)
        ragQueryService = new RagQueryService(vectorStore, chatModel, ragProperties);
    }

    @Test
    @DisplayName("Returns empty-context message when vector store has no documents")
    void query_whenNoChunksRetrieved_returnsEmptyContextMessage() {
        // Given — vector store returns no results
        when(vectorStore.similaritySearch(any())).thenReturn(List.of());

        QueryRequest request = QueryRequest.builder()
                .question("What is Spring Boot?")
                .build();

        // When
        QueryResponse response = ragQueryService.query(request);

        // Then
        assertThat(response.getAnswer()).contains("No relevant documents found");
        assertThat(response.getChunksRetrieved()).isEqualTo(0);
        assertThat(response.getQuestion()).isEqualTo("What is Spring Boot?");
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("Returns populated sourceChunks when includeSourceChunks=true")
    void query_whenIncludeSourceChunksTrue_returnsChunksInResponse() {
        // Given — a retrieved chunk
        Document chunk = new Document("Spring Boot auto-configures beans automatically.",
                Map.of("source", "Spring Docs", "documentId", "doc-1", "chunkIndex", "0"));

        when(vectorStore.similaritySearch(any())).thenReturn(List.of(chunk));

        // Mock ChatModel to return a canned answer
        // NOTE: ChatClient wraps ChatModel internally, so we mock at ChatModel level
        AssistantMessage assistantMessage = new AssistantMessage("Spring Boot is a framework.");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse);

        QueryRequest request = QueryRequest.builder()
                .question("What is Spring Boot?")
                .includeSourceChunks(true)
                .build();

        // When
        QueryResponse response = ragQueryService.query(request);

        // Then
        assertThat(response.getAnswer()).isEqualTo("Spring Boot is a framework.");
        assertThat(response.getChunksRetrieved()).isEqualTo(1);
        assertThat(response.getSourceChunks()).hasSize(1);
        assertThat(response.getSourceChunks().get(0).getSource()).isEqualTo("Spring Docs");
        assertThat(response.getSourceChunks().get(0).getContent()).contains("auto-configures");
    }

    @Test
    @DisplayName("Uses request-level topK override over global default")
    void query_whenTopKOverrideProvided_usesOverrideValue() {
        // Given — vector store returns 2 chunks (simulating topK=2 effective)
        Document chunk1 = new Document("Chunk 1", Map.of("source", "Doc A", "chunkIndex", "0"));
        Document chunk2 = new Document("Chunk 2", Map.of("source", "Doc B", "chunkIndex", "0"));
        when(vectorStore.similaritySearch(any())).thenReturn(List.of(chunk1, chunk2));

        AssistantMessage msg = new AssistantMessage("Answer based on 2 chunks.");
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(msg))));

        QueryRequest request = QueryRequest.builder()
                .question("Test question")
                .topK(2)   // Override global default of 4
                .build();

        // When
        QueryResponse response = ragQueryService.query(request);

        // Then
        assertThat(response.getChunksRetrieved()).isEqualTo(2);
    }

    @Test
    @DisplayName("Response has no sourceChunks when includeSourceChunks=false (default)")
    void query_whenIncludeSourceChunksFalse_returnsEmptySourceChunks() {
        // Given
        Document chunk = new Document("Some content", Map.of("source", "Doc", "chunkIndex", "0"));
        when(vectorStore.similaritySearch(any())).thenReturn(List.of(chunk));

        AssistantMessage msg = new AssistantMessage("Answer.");
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(msg))));

        QueryRequest request = QueryRequest.builder()
                .question("Test")
                .includeSourceChunks(false) // default
                .build();

        // When
        QueryResponse response = ragQueryService.query(request);

        // Then
        assertThat(response.getSourceChunks()).isEmpty();
        assertThat(response.getChunksRetrieved()).isEqualTo(1); // count still reported
    }
}
