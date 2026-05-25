/**
 * RagQueryServiceTest.java
 *
 * Unit tests for App 4 (Graph RAG). Verifies:
 *   1. BFS traversal discovers transitively connected entities — pure unit test, no mocks.
 *   2. EntityExtractionService parses a valid LLM JSON response into entities + relationships.
 *   3. EntityExtractionService returns an empty SubGraph when the LLM returns invalid JSON.
 *
 * Uses Mockito exclusively for (2) and (3). No Spring context loaded, no real LLM calls.
 */
package com.sathish.rag.graph;

import com.sathish.rag.graph.config.GraphRagProperties;
import com.sathish.rag.graph.model.Entity;
import com.sathish.rag.graph.model.Relationship;
import com.sathish.rag.graph.model.SubGraph;
import com.sathish.rag.graph.repository.InMemoryGraphRepository;
import com.sathish.rag.graph.service.EntityExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatModel chatModel;

    private InMemoryGraphRepository graphRepository;
    private GraphRagProperties properties;

    @BeforeEach
    void setUp() {
        graphRepository = new InMemoryGraphRepository();
        properties = new GraphRagProperties();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: BFS traversal — pure unit test, no mocks
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 1: BFS traversal discovers transitively connected entities")
    void testBfsTraversalFindsTransitiveConnections() {
        graphRepository.addEntity(new Entity("Alice", "PERSON", "A researcher"));
        graphRepository.addEntity(new Entity("Bob", "PERSON", "A developer"));
        graphRepository.addEntity(new Entity("Charlie", "PERSON", "A manager"));
        graphRepository.addRelationship(new Relationship("Alice", "Bob", "COLLABORATES_WITH", "Work together"));
        graphRepository.addRelationship(new Relationship("Bob", "Charlie", "REPORTS_TO", "Bob reports to Charlie"));

        SubGraph result = graphRepository.bfsTraversal(Set.of("Alice"), 2);

        assertThat(result.entities()).hasSize(3);
        assertThat(result.relationships()).hasSize(2);
        assertThat(result.entities().stream().map(Entity::name))
                .containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: EntityExtractionService parses valid JSON
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 2: EntityExtractionService parses valid LLM JSON response")
    void testEntityExtractionParsesValidJson() {
        String json = """
                {
                  "entities": [
                    {"name": "Spring AI", "type": "TECHNOLOGY", "description": "AI framework for Spring"},
                    {"name": "Google", "type": "ORGANIZATION", "description": "Tech company"}
                  ],
                  "relationships": [
                    {"fromEntity": "Spring AI", "toEntity": "Google", "relationType": "INTEGRATES_WITH",
                     "description": "Uses Vertex AI"}
                  ]
                }
                """;

        when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText()).thenReturn(json);

        EntityExtractionService service = new EntityExtractionService(chatModel, properties);
        SubGraph result = service.extractFromText("Spring AI integrates with Google Vertex AI.");

        assertThat(result.entities()).hasSize(2);
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.entities().stream().map(Entity::name))
                .containsExactlyInAnyOrder("Spring AI", "Google");
        assertThat(result.relationships().get(0).relationType()).isEqualTo("INTEGRATES_WITH");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: EntityExtractionService returns empty SubGraph on invalid JSON
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Test 3: EntityExtractionService returns empty SubGraph on invalid JSON")
    void testEntityExtractionReturnsEmptyOnInvalidJson() {
        when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText())
                .thenReturn("This is not valid JSON at all.");

        EntityExtractionService service = new EntityExtractionService(chatModel, properties);
        SubGraph result = service.extractFromText("Some input text");

        assertThat(result.entities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }
}
