package com.sathish.rag.graph.repository;

import com.sathish.rag.graph.model.Entity;
import com.sathish.rag.graph.model.Relationship;
import com.sathish.rag.graph.model.SubGraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
@Slf4j
public class InMemoryGraphRepository {

    private final Map<String, Entity> entities = new ConcurrentHashMap<>();
    private final List<Relationship> relationships = new CopyOnWriteArrayList<>();

    public void addEntity(Entity entity) {
        entities.put(entity.name(), entity);
        log.debug("Added entity name='{}' type='{}'", entity.name(), entity.type());
    }

    public void addRelationship(Relationship relationship) {
        relationships.add(relationship);
        log.debug("Added relationship '{}'→'{}' type='{}'",
                relationship.fromEntity(), relationship.toEntity(), relationship.relationType());
    }

    public Set<String> findEntityNamesMentionedIn(String text) {
        String lower = text.toLowerCase();
        return entities.keySet().stream()
                .filter(name -> lower.contains(name.toLowerCase()))
                .collect(Collectors.toSet());
    }

    public SubGraph bfsTraversal(Set<String> seedEntityNames, int maxHops) {
        if (seedEntityNames.isEmpty()) {
            return new SubGraph(List.of(), List.of());
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>(seedEntityNames);
        Set<Entity> resultEntities = new LinkedHashSet<>();
        Set<Relationship> resultRelationships = new LinkedHashSet<>();

        int hop = 0;
        while (!queue.isEmpty() && hop < maxHops) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                if (current == null || visited.contains(current)) continue;
                visited.add(current);

                Entity entity = entities.get(current);
                if (entity != null) resultEntities.add(entity);

                for (Relationship rel : relationships) {
                    if (rel.fromEntity().equals(current)) {
                        resultRelationships.add(rel);
                        if (!visited.contains(rel.toEntity())) {
                            queue.add(rel.toEntity());
                            Entity toEntity = entities.get(rel.toEntity());
                            if (toEntity != null) resultEntities.add(toEntity);
                        }
                    } else if (rel.toEntity().equals(current)) {
                        resultRelationships.add(rel);
                        if (!visited.contains(rel.fromEntity())) {
                            queue.add(rel.fromEntity());
                            Entity fromEntity = entities.get(rel.fromEntity());
                            if (fromEntity != null) resultEntities.add(fromEntity);
                        }
                    }
                }
            }
            hop++;
        }

        return new SubGraph(new ArrayList<>(resultEntities), new ArrayList<>(resultRelationships));
    }

    public List<Entity> getAllEntities() {
        return new ArrayList<>(entities.values());
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("entityCount", entities.size());
        stats.put("relationshipCount", relationships.size());
        return stats;
    }

    public void clear() {
        entities.clear();
        relationships.clear();
    }
}
