package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.List;

/** A single message in a multi-turn conversation. */
public record ChatMessage(String role, String content, List<ToolResult> toolResults) {

  /** Normalizes null {@code toolResults} to an empty list. */
  public ChatMessage {
    toolResults = toolResults != null ? toolResults : List.of();
  }
}
