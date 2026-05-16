package io.b2mash.b2b.b2bstrawman.integration.ai;

import java.util.List;
import java.util.Map;

/** Request for AI completion with image inputs (vision). */
public record AiVisionRequest(
    String systemPrompt,
    String userPrompt,
    String model,
    int maxTokens,
    double temperature,
    Map<String, String> metadata,
    List<AiImageInput> images) {}
