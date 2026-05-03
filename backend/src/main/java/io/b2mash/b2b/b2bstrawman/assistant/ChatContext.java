package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import java.util.List;

/**
 * Request DTO for the assistant chat endpoint. Carries the user's new message, prior conversation
 * history, the current browser path for context-aware system prompt assembly, and an optional Phase
 * 70 specialist id for specialist-mode chats.
 */
public record ChatContext(
    String message, List<ChatMessage> history, String currentPage, String specialistId) {

  /** Normalizes null {@code history} to an empty list. */
  public ChatContext {
    history = history != null ? history : List.of();
  }

  /** Backwards-compatible constructor for callers/tests that pre-date Phase 70. */
  public ChatContext(String message, List<ChatMessage> history, String currentPage) {
    this(message, history, currentPage, null);
  }
}
