package io.b2mash.b2b.b2bstrawman.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
}
