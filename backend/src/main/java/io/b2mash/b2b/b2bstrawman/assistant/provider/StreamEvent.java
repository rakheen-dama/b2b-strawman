package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.Map;

/**
 * Sealed interface representing events emitted by an LLM provider during streaming chat. Each
 * variant maps to a distinct SSE event type sent to the frontend.
 */
public sealed interface StreamEvent
    permits StreamEvent.TextDelta,
        StreamEvent.ToolUse,
        StreamEvent.Usage,
        StreamEvent.Done,
        StreamEvent.Error {

  /** Incremental text chunk from the LLM. */
  record TextDelta(String text) implements StreamEvent {}

  /** LLM is invoking a tool. */
  record ToolUse(String toolCallId, String toolName, Map<String, Object> input)
      implements StreamEvent {}

  /** Token usage statistics for a single LLM turn. */
  record Usage(int inputTokens, int outputTokens) implements StreamEvent {}

  /** Stream complete signal. */
  record Done() implements StreamEvent {}

  /** Error occurred during streaming. */
  record Error(String message) implements StreamEvent {}
}
