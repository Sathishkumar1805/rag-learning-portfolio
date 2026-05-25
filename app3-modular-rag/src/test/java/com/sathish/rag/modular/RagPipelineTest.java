/**
 * RagPipelineTest.java
 *
 * Unit tests for the modular RagPipeline. Verifies that:
 *   1. All steps execute in the configured order.
 *   2. A step that throws does not crash the pipeline — subsequent steps still run.
 *   3. When no candidates are retrieved the answer is the GenerationStep fallback.
 *
 * Uses Mockito exclusively — no Spring context loaded, no LLM or vector store calls.
 * Each test is independent; the pipeline is re-created fresh in setUp().
 */
package com.sathish.rag.modular;

import com.sathish.rag.modular.dto.QueryRequest;
import com.sathish.rag.modular.dto.QueryResponse;
import com.sathish.rag.modular.pipeline.PipelineContext;
import com.sathish.rag.modular.pipeline.RagPipeline;
import com.sathish.rag.modular.pipeline.RagPipelineStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagPipelineTest {

    @Mock
    private RagPipelineStep step1;

    @Mock
    private RagPipelineStep step2;

    @Mock
    private RagPipelineStep step3;

    private RagPipeline ragPipeline;

    @BeforeEach
    void setUp() {
        when(step1.getStepName()).thenReturn("Step1");
        when(step2.getStepName()).thenReturn("Step2");
        when(step3.getStepName()).thenReturn("Step3");
        ragPipeline = new RagPipeline(List.of(step1, step2, step3));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: Steps execute in order and the answer assembled by the last step
    // is returned in the QueryResponse.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 1: All steps execute in configured order and answer is returned")
    void testStepsExecuteInOrder() {
        // Arrange: step1 populates candidates; step3 sets the answer
        doAnswer(inv -> {
            PipelineContext ctx = inv.getArgument(0);
            ctx.setCandidates(List.of(new Document("Some retrieved content")));
            return null;
        }).when(step1).execute(any(PipelineContext.class));

        doAnswer(inv -> {
            PipelineContext ctx = inv.getArgument(0);
            ctx.setAnswer("The pipeline answer");
            return null;
        }).when(step3).execute(any(PipelineContext.class));

        QueryRequest request = QueryRequest.builder().question("What is RAG?").build();

        // Act
        QueryResponse response = ragPipeline.execute(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isEqualTo("The pipeline answer");
        assertThat(response.getQuestion()).isEqualTo("What is RAG?");
        assertThat(response.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getPipelineTrace()).hasSize(3);

        // Verify strict order: step1 → step2 → step3
        InOrder order = inOrder(step1, step2, step3);
        order.verify(step1).execute(any(PipelineContext.class));
        order.verify(step2).execute(any(PipelineContext.class));
        order.verify(step3).execute(any(PipelineContext.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: A step that throws does NOT crash the pipeline — the remaining
    // steps still execute and the pipeline still produces a response.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 2: Step exception is swallowed and pipeline continues")
    void testStepExceptionIsTolerated() {
        // Arrange: step2 blows up; step3 still sets the answer
        doThrow(new RuntimeException("Simulated step failure"))
                .when(step2).execute(any(PipelineContext.class));

        doAnswer(inv -> {
            PipelineContext ctx = inv.getArgument(0);
            ctx.setAnswer("Answer despite step2 failure");
            return null;
        }).when(step3).execute(any(PipelineContext.class));

        QueryRequest request = QueryRequest.builder().question("Trigger failure?").build();

        // Act
        QueryResponse response = ragPipeline.execute(request);

        // Assert: pipeline completed and step3's answer is present
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isEqualTo("Answer despite step2 failure");
        assertThat(response.getPipelineTrace()).hasSize(3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: When no candidates are set, the answer field in the context is
    // null (no generation step set it), which the pipeline passes through as-is.
    // This validates the pipeline doesn't add its own fallback — that's the
    // GenerationStep's responsibility.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 3: Pipeline returns null answer when no step sets it")
    void testNullAnswerWhenNoStepGenerates() {
        // Arrange: no step sets ctx.setAnswer(...)
        QueryRequest request = QueryRequest.builder().question("Unknown query").build();

        // Act
        QueryResponse response = ragPipeline.execute(request);

        // Assert: answer is null (pipeline doesn't add a fallback — that's GenerationStep's job)
        assertThat(response).isNotNull();
        assertThat(response.getAnswer()).isNull();
        assertThat(response.getQuestion()).isEqualTo("Unknown query");
    }
}
