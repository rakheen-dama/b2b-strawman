package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.List;

/** A single message in a multi-turn conversation. */
public record ChatMessage(String role, String content, List<ToolResult> toolResults) {}
