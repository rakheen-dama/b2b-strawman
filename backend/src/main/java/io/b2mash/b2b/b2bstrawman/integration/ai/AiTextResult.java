package io.b2mash.b2b.b2bstrawman.integration.ai;

/** Result of an AI text operation. */
public record AiTextResult(
    boolean success, String content, String errorMessage, Integer tokensUsed) {}
