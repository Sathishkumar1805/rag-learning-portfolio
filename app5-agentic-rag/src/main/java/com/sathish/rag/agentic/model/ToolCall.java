package com.sathish.rag.agentic.model;

public record ToolCall(String toolName, String argsJson) {}
