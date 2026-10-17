package io.b2mash.b2b.b2bstrawman.integration.ai.provider;

import java.util.Map;

/** Definition of a tool the LLM can invoke, including its JSON Schema for input validation. */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {}
