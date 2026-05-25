package com.sathish.rag.graph.model;

import java.util.List;

public record SubGraph(List<Entity> entities, List<Relationship> relationships) {
    public boolean isEmpty() {
        return entities.isEmpty() && relationships.isEmpty();
    }
}
