/**
 * RagQueryServiceTest.java
 * <p><b>RAG Role:</b> Unit tests for the RagQueryService pipeline. Tests cover the
 * HyDE-enabled happy path, the empty retrieval fallback, and parent context resolution.
 * Uses Mockito exclusively — no Spring context is loaded, so tests run in milliseconds.
 * <p><b>Learning Note:</b> Pure Mockito tests (no @SpringBootTest) are the right choice
 * for service-layer logic — they run fast, don't need a running LLM or vector store,
 * and test exactly one unit of behaviour at a time. Reserve @SpringBootTest for
 * integration tests that verify the full wired-up application.
 * <p><b>LEARN:</b> Compare with App 3+ where integration tests with Testcontainers
 * will verify the actual Qdrant interactions. App 2 tests focus on pipeline logic only.
 * @author Sathishkumar Krishnan
 * @version 1.0.0
 */
package com.sathish.rag.advanced;

import com.sathish.rag.advanced.config.RagProperties;
import com.sathish.rag.advanced.dto.QueryRequest;
import com.sathish.rag.advanced.dto.QueryResponse;
import com.sathish.rag.advanced.service.HydeQueryExpansionService;
import com.sathish.rag.advanced.service.HydeQueryExpansionService.HydeResult;
import com.sathish.rag.advanced.service.MmrRerankerService;
import com.sathish.rag.advanced.service.MmrRerankerService.MmrResult;
import com.sathish.rag.advanced.service.RagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagQueryServiceTest {

    @Mock
    private HydeQueryExpansionService hydeService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private MmrRerankerService mmrService;

    @Mock
    private RagProperties ragProperties;

    @InjectMocks
    private RagQueryService ragQueryService;

    // Nested property mocks
    @Mock
    private RagProperties.RetrievalProperties retrievalProperties;

    @Mock
    private RagProperties.MmrProperties mmrProperties;

    @Mock
    private RagProperties.GenerationProperties generationProperties;

    @BeforeEach
    void setUp() {
        // Wire up nested property mocks
        when(ragProperties.getRetrieval()).thenReturn(retrievalProperties);
        when(ragProperties.getMmr()).thenReturn(mmrProperties);
        when(ragProperties.getGeneration()).thenReturn(generationProperties);

        // Default retrieval settings
        when(retrievalProperties.getTopKResults()).thenReturn(6);
        when(retrievalProperties.getSimilarityThreshold()).thenReturn(0.65);

        // Default MMR settings
        when(mmrProperties.getCandidatePoolSize()).thenReturn(20);
        when(mmrProperties.getLambda()).thenReturn(0.6);
        when(mmrProperties.isEnabled()).thenReturn(true);
        when(mmrProperties.getFinalTopK()).thenReturn(4);

        // Default generation settings
        when(generationProperties.getSystemPrompt()).thenReturn(
                "You are a helpful assistant. Answer using ONLY the provided context.");
        when(generationProperties.isIncludeParentContext()).thenReturn(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: HyDE enabled path — verifies that when HyDE is enabled, the
    // hypothesis embedding is used and the response reflects hydeUsed=true.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 1: HyDE enabled path returns hydeUsed=true and non-blank answer")
    void testHydeEnabledPath() {
        // Arrange
        String question = "What is RAG?";
        List<Double> hydeEmbedding = List.of(0.1, 0.2, 0.3);
        HydeResult hydeResult = HydeResult.of(
                "RAG stands for Retrieval-Augmented Generation, a technique that...",
                hydeEmbedding
        );

        Document testDoc = new Document("RAG is a powerful retrieval technique.");

        MmrResult mmrResult = new MmrResult(
                List.of(testDoc),
                List.of(testDoc),
                0.6,
                true
        );

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Test answer about RAG")))
        );

        when(hydeService.expand(question, null)).thenReturn(hydeResult);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(testDoc));
        when(mmrService.rerank(any(), any(), any(), any(), any())).thenReturn(mmrResult);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        QueryRequest request = QueryRequest.builder()
                .question(question)
                .build();

        // Act
        QueryResponse response = ragQueryService.query(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isHydeUsed()).isTrue();
        assertThat(response.getAnswer()).isNotBlank();
        assertThat(response.getAnswer()).isEqualTo("Test answer about RAG");
        assertThat(response.getQuestion()).isEqualTo(question);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Empty retrieval — verifies that when the vector store returns
    // no candidates, the pipeline returns a "could not find" response and
    // never calls the LLM (saving cost and avoiding hallucinations).
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 2: Empty retrieval returns 'could not find' and never calls LLM")
    void testEmptyRetrievalFallback() {
        // Arrange
        String question = "What is a completely unknown topic?";
        HydeResult hydeDisabled = HydeResult.disabled();

        when(hydeService.expand(question, null)).thenReturn(hydeDisabled);
        when(hydeService.embedQuestion(question)).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        QueryRequest request = QueryRequest.builder()
                .question(question)
                .build();

        // Act
        QueryResponse response = ragQueryService.query(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).containsIgnoringCase("could not find");
        assertThat(response.getChunksBeforeReranking()).isEqualTo(0);

        // Verify LLM was NEVER called
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Parent context resolution — verifies that when includeParentContext=true,
    // the parent text from metadata is injected into the LLM prompt instead of the
    // small child chunk text.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 3: Parent context text is included in the LLM prompt when enabled")
    void testParentContextResolution() {
        // Arrange
        String question = "Explain parent-child chunking";
        String childText = "Small child chunk text.";
        String parentText = "parent context here — this is the full parent paragraph with much more context.";

        Document docWithParent = new Document(childText, Map.of(
                "parentText", parentText,
                "source", "test-doc",
                "parentId", "parent-uuid-001"
        ));

        HydeResult hydeDisabled = HydeResult.disabled();
        MmrResult mmrResult = new MmrResult(
                List.of(docWithParent),
                List.of(docWithParent),
                0.6,
                false
        );

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Parent context answer")))
        );

        when(ragProperties.getGeneration().isIncludeParentContext()).thenReturn(true);
        when(hydeService.expand(question, null)).thenReturn(hydeDisabled);
        when(hydeService.embedQuestion(question)).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithParent));
        when(mmrService.rerank(any(), any(), any(), any(), any())).thenReturn(mmrResult);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        QueryRequest request = QueryRequest.builder()
                .question(question)
                .build();

        // Capture the Prompt passed to chatModel
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        QueryResponse response = ragQueryService.query(request);

        // Assert: Verify chatModel was called and capture the prompt
        verify(chatModel).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();

        // Find the UserMessage content and verify it contains parent context
        String promptContent = capturedPrompt.getContents();
        assertThat(promptContent).contains("parent context here");
        assertThat(response.getAnswer()).isEqualTo("Parent context answer");
    }
}
