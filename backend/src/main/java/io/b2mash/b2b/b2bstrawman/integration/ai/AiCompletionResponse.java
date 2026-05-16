package io.b2mash.b2b.b2bstrawman.integration.ai;

/** Response from a structured AI completion, including token usage and cache metrics. */
public record AiCompletionResponse(
    String content,
    String model,
    int inputTokens,
    int outputTokens,
    int cacheReadInputTokens,
    int cacheCreationInputTokens,
    String stopReason,
    long durationMs) {}
