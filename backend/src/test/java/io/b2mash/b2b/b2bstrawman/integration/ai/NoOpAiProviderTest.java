package io.b2mash.b2b.b2bstrawman.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoOpAiProviderTest {

  private final NoOpAiProvider provider = new NoOpAiProvider();

  @Test
  void providerId_returns_noop() {
    assertThat(provider.providerId()).isEqualTo("noop");
  }

  @Test
  void generateText_returns_success_with_empty_content() {
    var request = new AiTextRequest("Summarize this document", 500, 0.7);

    var result = provider.generateText(request);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEmpty();
    assertThat(result.errorMessage()).isNull();
    assertThat(result.tokensUsed()).isZero();
  }

  @Test
  void summarize_returns_success_with_empty_content() {
    var result = provider.summarize("Long content to summarize...", 100);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEmpty();
    assertThat(result.errorMessage()).isNull();
    assertThat(result.tokensUsed()).isZero();
  }

  @Test
  void suggestCategories_returns_empty_list() {
    var result =
        provider.suggestCategories("Some document content", List.of("Legal", "Financial", "HR"));

    assertThat(result).isEmpty();
  }

  @Test
  void testConnection_returns_success() {
    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void complete_returns_error_response_with_noop_model() {
    var request =
        new AiCompletionRequest(
            "You are a helpful assistant.",
            "Summarize this contract.",
            "claude-sonnet-4-20250514",
            4096,
            0.3,
            Map.of("skillId", "fica-verification"));

    var result = provider.complete(request);

    assertThat(result.content()).contains("AI not configured");
    assertThat(result.model()).isEqualTo("noop");
    assertThat(result.inputTokens()).isZero();
    assertThat(result.outputTokens()).isZero();
    assertThat(result.cacheReadInputTokens()).isZero();
    assertThat(result.cacheCreationInputTokens()).isZero();
    assertThat(result.stopReason()).isEqualTo("end_turn");
    assertThat(result.durationMs()).isZero();
  }

  @Test
  void completeWithVision_returns_error_response_with_noop_model() {
    var request =
        new AiVisionRequest(
            "You are a document analyzer.",
            "Extract text from this image.",
            "claude-sonnet-4-20250514",
            4096,
            0.3,
            Map.of(),
            List.of(new AiImageInput("image/png", "iVBORw0KGgoAAAANSUhEUg==")));

    var result = provider.completeWithVision(request);

    assertThat(result.content()).contains("AI not configured");
    assertThat(result.model()).isEqualTo("noop");
    assertThat(result.inputTokens()).isZero();
    assertThat(result.outputTokens()).isZero();
    assertThat(result.cacheReadInputTokens()).isZero();
    assertThat(result.cacheCreationInputTokens()).isZero();
    assertThat(result.stopReason()).isEqualTo("end_turn");
    assertThat(result.durationMs()).isZero();
  }
}
