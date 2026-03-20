package io.b2mash.b2b.b2bstrawman.assistant.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class LlmChatProviderRegistryTest {

  // --- Test-only provider stubs ---

  static class TestAnthropicProvider implements LlmChatProvider {
    @Override
    public String providerId() {
      return "anthropic";
    }

    @Override
    public void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer) {
      // no-op for test
    }

    @Override
    public boolean validateKey(String apiKey, String model) {
      return true;
    }

    @Override
    public List<ModelInfo> availableModels() {
      return List.of(new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", true));
    }
  }

  static class TestOpenAiProvider implements LlmChatProvider {
    @Override
    public String providerId() {
      return "openai";
    }

    @Override
    public void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer) {
      // no-op for test
    }

    @Override
    public boolean validateKey(String apiKey, String model) {
      return true;
    }

    @Override
    public List<ModelInfo> availableModels() {
      return List.of(new ModelInfo("gpt-4o", "GPT-4o", true));
    }
  }

  static class DuplicateAnthropicProvider implements LlmChatProvider {
    @Override
    public String providerId() {
      return "anthropic"; // duplicate!
    }

    @Override
    public void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer) {
      // no-op for test
    }

    @Override
    public boolean validateKey(String apiKey, String model) {
      return false;
    }

    @Override
    public List<ModelInfo> availableModels() {
      return List.of();
    }
  }

  // --- Tests ---

  @Test
  void discoversProviderByProviderId() {
    var anthropic = new TestAnthropicProvider();
    var registry = new LlmChatProviderRegistry(List.of(anthropic));

    var result = registry.get("anthropic");

    assertThat(result).isSameAs(anthropic);
  }

  @Test
  void getReturnsCorrectProvider() {
    var anthropic = new TestAnthropicProvider();
    var openai = new TestOpenAiProvider();
    var registry = new LlmChatProviderRegistry(List.of(anthropic, openai));

    assertThat(registry.get("anthropic")).isSameAs(anthropic);
    assertThat(registry.get("openai")).isSameAs(openai);
  }

  @Test
  void getThrowsForUnknownProvider() {
    var anthropic = new TestAnthropicProvider();
    var registry = new LlmChatProviderRegistry(List.of(anthropic));

    assertThatThrownBy(() -> registry.get("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void getAllReturnsAllRegisteredProviders() {
    var anthropic = new TestAnthropicProvider();
    var openai = new TestOpenAiProvider();
    var registry = new LlmChatProviderRegistry(List.of(anthropic, openai));

    var all = registry.getAll();

    assertThat(all).hasSize(2);
    assertThat(all).containsExactlyInAnyOrder(anthropic, openai);
  }

  @Test
  void duplicateProviderIdThrowsIllegalStateException() {
    var anthropic = new TestAnthropicProvider();
    var duplicate = new DuplicateAnthropicProvider();

    assertThatThrownBy(() -> new LlmChatProviderRegistry(List.of(anthropic, duplicate)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate")
        .hasMessageContaining("anthropic");
  }
}
