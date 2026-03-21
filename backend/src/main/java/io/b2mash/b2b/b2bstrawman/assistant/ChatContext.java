package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import java.util.List;

/**
 * Request DTO for the assistant chat endpoint. Carries the user's new message, prior conversation
 * history, and the current browser path for context-aware system prompt assembly.
 */
public record ChatContext(String message, List<ChatMessage> history, String currentPage) {

  /** Normalizes null {@code history} to an empty list. */
  public ChatContext {
    history = history != null ? history : List.of();
  }
}
