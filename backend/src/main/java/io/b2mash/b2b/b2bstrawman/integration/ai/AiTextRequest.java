package io.b2mash.b2b.b2bstrawman.integration.ai;

/** Request for AI text generation. */
public record AiTextRequest(String prompt, int maxTokens, Double temperature) {}
