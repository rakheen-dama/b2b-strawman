package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicAiProviderTest {

  @Mock private AnthropicApiClient apiClient;
  @Mock private SecretStore secretStore;

  private AnthropicAiProvider provider;

  @BeforeEach
  void setUp() {
    provider = new AnthropicAiProvider(apiClient, secretStore);
  }

  @Test
  void providerId_returnsAnthropic() {
    assertThat(provider.providerId()).isEqualTo("anthropic");
  }

  @Test
  void complete_resolvesApiKeyAndDelegatesToClient() {
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-tenant-key");
    var expectedResponse =
        new AiCompletionResponse(
            "Result text", "claude-sonnet-4-20250514", 50, 20, 30, 10, "end_turn", 150L);
    when(apiClient.sendCompletion(eq("sk-tenant-key"), any(AiCompletionRequest.class)))
        .thenReturn(expectedResponse);

    var request =
        new AiCompletionRequest(
            "You are helpful.",
            "Summarize this.",
            "claude-sonnet-4-20250514",
            4096,
            0.3,
            Map.of("skillId", "test"));

    var response = provider.complete(request);

    assertThat(response.content()).isEqualTo("Result text");
    assertThat(response.model()).isEqualTo("claude-sonnet-4-20250514");
    assertThat(response.inputTokens()).isEqualTo(50);
    assertThat(response.outputTokens()).isEqualTo(20);
    assertThat(response.cacheReadInputTokens()).isEqualTo(30);
    assertThat(response.cacheCreationInputTokens()).isEqualTo(10);

    verify(secretStore).retrieve("ai:anthropic:api_key");
    verify(apiClient).sendCompletion(eq("sk-tenant-key"), any(AiCompletionRequest.class));
  }

  @Test
  void completeWithVision_resolvesApiKeyAndDelegatesToClient() {
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-tenant-key");
    var expectedResponse =
        new AiCompletionResponse(
            "Document text", "claude-sonnet-4-20250514", 200, 30, 0, 0, "end_turn", 500L);
    when(apiClient.sendVisionCompletion(eq("sk-tenant-key"), any(AiVisionRequest.class)))
        .thenReturn(expectedResponse);

    var request =
        new AiVisionRequest(
            "You are a document reader.",
            "Extract text.",
            "claude-sonnet-4-20250514",
            4096,
            0.3,
            Map.of(),
            List.of(new AiImageInput("image/png", "base64data")));

    var response = provider.completeWithVision(request);

    assertThat(response.content()).isEqualTo("Document text");
    verify(apiClient).sendVisionCompletion(eq("sk-tenant-key"), any(AiVisionRequest.class));
  }

  @Test
  void generateText_delegatesToComplete() {
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-key");
    var expectedResponse =
        new AiCompletionResponse(
            "Generated text", "claude-sonnet-4-20250514", 10, 15, 0, 0, "end_turn", 100L);
    when(apiClient.sendCompletion(eq("sk-key"), any(AiCompletionRequest.class)))
        .thenReturn(expectedResponse);

    var result = provider.generateText(new AiTextRequest("Write something", 500, 0.7));

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("Generated text");
    assertThat(result.tokensUsed()).isEqualTo(15);
  }

  @Test
  void summarize_delegatesToComplete() {
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-key");
    var expectedResponse =
        new AiCompletionResponse(
            "Short summary", "claude-sonnet-4-20250514", 100, 10, 0, 0, "end_turn", 80L);
    when(apiClient.sendCompletion(eq("sk-key"), any(AiCompletionRequest.class)))
        .thenReturn(expectedResponse);

    var result = provider.summarize("Long content that needs summarizing...", 100);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("Short summary");
  }

  @Test
  void phase52Wrappers_useCanonicalSonnetModel() {
    // The default model for generateText/summarize/suggestCategories must stay aligned with the
    // canonical "claude-sonnet-4-6" used everywhere else (pricing config, chat, specialists). A
    // dated/off-version id silently runs a different model and misses the AiPricingProperties
    // table.
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-key");
    var stubResponse =
        new AiCompletionResponse("ok", "claude-sonnet-4-6", 10, 5, 0, 0, "end_turn", 50L);
    when(apiClient.sendCompletion(eq("sk-key"), any(AiCompletionRequest.class)))
        .thenReturn(stubResponse);
    var captor = ArgumentCaptor.forClass(AiCompletionRequest.class);

    provider.generateText(new AiTextRequest("Write something", 500, 0.7));
    provider.summarize("Long content to summarize...", 100);
    provider.suggestCategories("Some content", List.of("Legal", "Finance"));

    verify(apiClient, times(3)).sendCompletion(eq("sk-key"), captor.capture());
    assertThat(captor.getAllValues())
        .extracting(AiCompletionRequest::model)
        .containsOnly("claude-sonnet-4-6");
  }

  @Test
  void testConnection_delegatesToApiClient() {
    when(secretStore.retrieve("ai:anthropic:api_key")).thenReturn("sk-key");
    when(apiClient.testConnection("sk-key"))
        .thenReturn(new ConnectionTestResult(true, "anthropic", null));

    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("anthropic");
  }

  @Test
  void testConnection_returnsFailureWhenKeyMissing() {
    when(secretStore.retrieve("ai:anthropic:api_key"))
        .thenThrow(new RuntimeException("Secret not found: ai:anthropic:api_key"));

    var result = provider.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("anthropic");
    assertThat(result.errorMessage()).contains("Secret not found");
  }
}
