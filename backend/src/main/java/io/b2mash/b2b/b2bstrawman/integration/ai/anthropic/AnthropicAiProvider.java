package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Anthropic AI provider implementing the full {@link AiProvider} interface. Resolves tenant API key
 * from {@link SecretStore} and delegates HTTP calls to {@link AnthropicApiClient}.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.AI, slug = "anthropic")
public class AnthropicAiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(AnthropicAiProvider.class);
  private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

  private final AnthropicApiClient apiClient;
  private final SecretStore secretStore;

  public AnthropicAiProvider(AnthropicApiClient apiClient, SecretStore secretStore) {
    this.apiClient = apiClient;
    this.secretStore = secretStore;
  }

  @Override
  public String providerId() {
    return "anthropic";
  }

  @Override
  public AiTextResult generateText(AiTextRequest request) {
    try {
      var completionRequest =
          new AiCompletionRequest(
              null,
              request.prompt(),
              DEFAULT_MODEL,
              request.maxTokens(),
              request.temperature() != null ? request.temperature() : 0.7,
              Map.of());

      var response = complete(completionRequest);
      return new AiTextResult(true, response.content(), null, response.outputTokens());
    } catch (Exception e) {
      log.warn("Anthropic generateText failed", e);
      return new AiTextResult(false, null, e.getMessage(), 0);
    }
  }

  @Override
  public AiTextResult summarize(String content, int maxLength) {
    try {
      var completionRequest =
          new AiCompletionRequest(
              "You are a precise summarizer. Summarize the following content concisely.",
              content,
              DEFAULT_MODEL,
              maxLength,
              0.3,
              Map.of());

      var response = complete(completionRequest);
      return new AiTextResult(true, response.content(), null, response.outputTokens());
    } catch (Exception e) {
      log.warn("Anthropic summarize failed", e);
      return new AiTextResult(false, null, e.getMessage(), 0);
    }
  }

  @Override
  public List<String> suggestCategories(String content, List<String> existingCategories) {
    try {
      var systemPrompt =
          "You are a document categorizer. Given the content and existing categories, "
              + "suggest the most relevant categories. Return ONLY a comma-separated list of "
              + "category names, nothing else.";
      var userPrompt =
          "Content: "
              + content
              + "\n\nExisting categories: "
              + String.join(", ", existingCategories)
              + "\n\nSuggested categories:";

      var completionRequest =
          new AiCompletionRequest(systemPrompt, userPrompt, DEFAULT_MODEL, 200, 0.3, Map.of());

      var response = complete(completionRequest);
      if (response.content() == null || response.content().isBlank()) {
        return List.of();
      }
      return List.of(response.content().split(",")).stream().map(String::trim).toList();
    } catch (Exception e) {
      log.warn("Anthropic suggestCategories failed", e);
      return List.of();
    }
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      String apiKey = resolveApiKey();
      return apiClient.testConnection(apiKey);
    } catch (Exception e) {
      return new ConnectionTestResult(false, "anthropic", e.getMessage());
    }
  }

  @Override
  public AiCompletionResponse complete(AiCompletionRequest request) {
    String apiKey = resolveApiKey();
    return apiClient.sendCompletion(apiKey, request);
  }

  @Override
  public AiCompletionResponse completeWithVision(AiVisionRequest request) {
    String apiKey = resolveApiKey();
    return apiClient.sendVisionCompletion(apiKey, request);
  }

  private String resolveApiKey() {
    return secretStore.retrieve(IntegrationKeys.aiApiKey("anthropic"));
  }
}
