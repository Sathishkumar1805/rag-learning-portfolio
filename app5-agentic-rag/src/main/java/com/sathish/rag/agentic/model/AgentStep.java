package com.sathish.rag.agentic.model;

public record AgentStep(String toolName, String toolInput, String observation) {}
