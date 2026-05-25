package com.sathish.rag.graph.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sathish.rag.graph.config.GraphRagProperties;
import com.sathish.rag.graph.model.Entity;
import com.sathish.rag.graph.model.Relationship;
import com.sathish.rag.graph.model.SubGraph;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class EntityExtractionService {

    private final ChatModel chatModel;
    private final GraphRagProperties properties;
    private final ObjectMapper objectMapper;

    public EntityExtractionService(ChatModel chatModel, GraphRagProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public SubGraph extractFromText(String text) {
        try {
            String systemPrompt = properties.getGraph().getExtractionSystemPrompt();
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(text)
            ));

            String response = chatModel.call(prompt).getResult().getOutput().getText();
            String cleaned = stripMarkdown(response);

            ExtractionResult data = objectMapper.readValue(cleaned, ExtractionResult.class);

            List<Entity> entityList = new ArrayList<>();
            if (data.getEntities() != null) {
                for (EntityDto e : data.getEntities()) {
                    entityList.add(new Entity(e.getName(), e.getType(), e.getDescription()));
                }
            }

            List<Relationship> relList = new ArrayList<>();
            if (data.getRelationships() != null) {
                for (RelationshipDto r : data.getRelationships()) {
                    relList.add(new Relationship(r.getFromEntity(), r.getToEntity(),
                            r.getRelationType(), r.getDescription()));
                }
            }

            log.debug("Extracted {} entities, {} relationships", entityList.size(), relList.size());
            return new SubGraph(entityList, relList);

        } catch (Exception e) {
            log.warn("Entity extraction failed (returning empty SubGraph): {}", e.getMessage());
            return new SubGraph(List.of(), List.of());
        }
    }

    private String stripMarkdown(String text) {
        String t = text.strip();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.strip();
    }

    @Data
    private static class ExtractionResult {
        private List<EntityDto> entities;
        private List<RelationshipDto> relationships;
    }

    @Data
    private static class EntityDto {
        private String name;
        private String type;
        private String description;
    }

    @Data
    private static class RelationshipDto {
        private String fromEntity;
        private String toEntity;
        private String relationType;
        private String description;
    }
}
