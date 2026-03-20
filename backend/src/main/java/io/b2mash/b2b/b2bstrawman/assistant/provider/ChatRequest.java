package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.List;

/** Request record carrying all inputs for a single LLM chat invocation. */
public record ChatRequest(
    String apiKey,
    String model,
    String systemPrompt,
    List<ChatMessage> messages,
    List<ToolDefinition> tools) {}
