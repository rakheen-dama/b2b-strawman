package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import java.util.List;

/**
 * Request DTO for the assistant chat endpoint. Carries the user's new message, prior conversation
 * history, the current browser path for context-aware system prompt assembly, and an optional
 * specialist id (Phase 70 Epic 511A) — when set, the chat is routed through the specialist's
 * curated tool subset and system prompt instead of the generalist's.
 */
public record ChatContext(
    String message, List<ChatMessage> history, String currentPage, String specialistId) {

  /** Normalizes null {@code history} to an empty list. */
  public ChatContext {
    history = history != null ? history : List.of();
  }

  /** Backward-compatible constructor for callers that don't pass {@code specialistId}. */
  public ChatContext(String message, List<ChatMessage> history, String currentPage) {
    this(message, history, currentPage, null);
  }
}
