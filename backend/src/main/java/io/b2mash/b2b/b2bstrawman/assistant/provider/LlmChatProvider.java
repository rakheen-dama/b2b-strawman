package io.b2mash.b2b.b2bstrawman.assistant.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-agnostic interface for streaming multi-turn chat with tool use. Separate from {@link
 * io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider} which handles one-shot text operations.
 *
 * @see <a href="../../adr/ADR-200-llm-chat-provider-interface.md">ADR-200</a>
 */
public interface LlmChatProvider {

  /** Provider identifier (e.g., "anthropic"). Used for registry lookup. */
  String providerId();

  /**
   * Streams a multi-turn chat completion. Blocks the calling thread and pushes {@link StreamEvent}
   * instances to the consumer as they arrive from the LLM API.
   *
   * <p>Uses {@code Consumer<StreamEvent>} instead of {@code Flux<StreamEvent>} to avoid a WebFlux
   * dependency. See ADR-202.
   */
  void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer);

  /** Validates an API key by sending a minimal 1-token completion. */
  boolean validateKey(String apiKey, String model);

  /** Returns the list of models supported by this provider. */
  List<ModelInfo> availableModels();
}
