package io.b2mash.b2b.b2bstrawman.integration.ai;

import java.util.Map;

/** Request for structured AI completion (one-shot, text-in/text-out). */
public record AiCompletionRequest(
    String systemPrompt,
    String userPrompt,
    String model,
    int maxTokens,
    double temperature,
    Map<String, String> metadata) {}
