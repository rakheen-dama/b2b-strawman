package io.b2mash.b2b.b2bstrawman.integration.ai.provider;

/**
 * Result of a tool execution, sent back to the LLM as context for the next turn. The {@code
 * toolCallId} maps to the Anthropic API's {@code tool_use_id} field.
 */
public record ToolResult(String toolCallId, String content) {}
