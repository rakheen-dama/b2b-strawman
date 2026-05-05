package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.List;

/** A single message in a multi-turn conversation. */
public record ChatMessage(
    String role,
    String content,
    List<ToolResult> toolResults,
    List<VisionContentBlock> visionBlocks) {

  /** Normalizes null collections to empty lists. */
  public ChatMessage {
    toolResults = toolResults != null ? toolResults : List.of();
    visionBlocks = visionBlocks != null ? visionBlocks : List.of();
  }

  /** Convenience constructor without vision blocks (backward compatible). */
  public ChatMessage(String role, String content, List<ToolResult> toolResults) {
    this(role, content, toolResults, List.of());
  }
}
