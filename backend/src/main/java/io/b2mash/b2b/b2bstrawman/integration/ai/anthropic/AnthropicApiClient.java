package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the Anthropic Messages API. Uses Spring RestClient with prompt caching
 * (cache_control), rate-limit retry (429 with exponential backoff), and configurable timeout.
 */
@Service
public class AnthropicApiClient {

  private static final Logger log = LoggerFactory.getLogger(AnthropicApiClient.class);
  private static final Map<String, String> CACHE_CONTROL_EPHEMERAL = Map.of("type", "ephemeral");

  private final RestClient restClient;
  private final AnthropicProperties properties;

  @Autowired
  public AnthropicApiClient(AnthropicProperties properties) {
    this.properties = properties;

    var httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

    this.restClient =
        RestClient.builder()
            .baseUrl(properties.apiBaseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  /** Package-private constructor for testing with a pre-built RestClient. */
  AnthropicApiClient(RestClient restClient, AnthropicProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  /**
   * Sends a text completion request to the Anthropic Messages API.
   *
   * @param apiKey the tenant's Anthropic API key
   * @param request the completion request
   * @return the parsed completion response with token metrics
   */
  public AiCompletionResponse sendCompletion(String apiKey, AiCompletionRequest request) {
    var messagesRequest = buildTextRequest(request);
    return executeWithRetry(apiKey, messagesRequest);
  }

  /**
   * Sends a vision completion request (text + images) to the Anthropic Messages API.
   *
   * @param apiKey the tenant's Anthropic API key
   * @param request the vision request with image inputs
   * @return the parsed completion response with token metrics
   */
  public AiCompletionResponse sendVisionCompletion(String apiKey, AiVisionRequest request) {
    var messagesRequest = buildVisionRequest(request);
    return executeWithRetry(apiKey, messagesRequest);
  }

  /**
   * Tests connectivity by sending a minimal request to verify the API key.
   *
   * @param apiKey the API key to validate
   * @return connection test result
   */
  public ConnectionTestResult testConnection(String apiKey) {
    try {
      var minimalRequest =
          new AnthropicMessagesRequest(
              "claude-haiku-4-5",
              1,
              List.of(new AnthropicMessagesRequest.SystemBlock("text", "Respond with 'ok'.", null)),
              List.of(new AnthropicMessagesRequest.Message("user", "ping")),
              null);

      restClient
          .post()
          .uri("/v1/messages")
          .header("x-api-key", apiKey)
          .header("anthropic-version", properties.apiVersion())
          .contentType(MediaType.APPLICATION_JSON)
          .body(minimalRequest)
          .retrieve()
          .onStatus(
              HttpStatusCode::isError,
              (req, resp) -> {
                throw new AnthropicApiException(
                    "Connection test failed: HTTP " + resp.getStatusCode().value());
              })
          .toBodilessEntity();

      return new ConnectionTestResult(true, "anthropic", null);
    } catch (Exception e) {
      log.debug("Anthropic connection test failed", e);
      return new ConnectionTestResult(false, "anthropic", e.getMessage());
    }
  }

  private AnthropicMessagesRequest buildTextRequest(AiCompletionRequest request) {
    var systemBlocks = new ArrayList<AnthropicMessagesRequest.SystemBlock>();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      systemBlocks.add(
          new AnthropicMessagesRequest.SystemBlock(
              "text", request.systemPrompt(), CACHE_CONTROL_EPHEMERAL));
    }

    var messages = List.of(new AnthropicMessagesRequest.Message("user", request.userPrompt()));

    Double temperature = request.temperature() > 0 ? request.temperature() : null;

    return new AnthropicMessagesRequest(
        request.model(), request.maxTokens(), systemBlocks, messages, temperature);
  }

  private AnthropicMessagesRequest buildVisionRequest(AiVisionRequest request) {
    var systemBlocks = new ArrayList<AnthropicMessagesRequest.SystemBlock>();
    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      systemBlocks.add(
          new AnthropicMessagesRequest.SystemBlock(
              "text", request.systemPrompt(), CACHE_CONTROL_EPHEMERAL));
    }

    // Build user message content blocks: text + images
    var contentBlocks = new ArrayList<Object>();
    if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
      contentBlocks.add(new AnthropicMessagesRequest.TextContent(request.userPrompt()));
    }
    for (AiImageInput image : request.images()) {
      contentBlocks.add(
          new AnthropicMessagesRequest.ImageContent(
              "image",
              new AnthropicMessagesRequest.ImageSource(
                  "base64", image.mediaType(), image.base64Data())));
    }

    var messages = List.of(new AnthropicMessagesRequest.Message("user", contentBlocks));

    Double temperature = request.temperature() > 0 ? request.temperature() : null;

    return new AnthropicMessagesRequest(
        request.model(), request.maxTokens(), systemBlocks, messages, temperature);
  }

  private AiCompletionResponse executeWithRetry(
      String apiKey, AnthropicMessagesRequest messagesRequest) {
    long startTime = System.currentTimeMillis();
    int maxAttempts = properties.maxRetries();

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        var response =
            restClient
                .post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", properties.apiVersion())
                .contentType(MediaType.APPLICATION_JSON)
                .body(messagesRequest)
                .retrieve()
                .onStatus(
                    status -> status.value() == 429,
                    (req, resp) -> {
                      throw new AnthropicRateLimitException("Rate limited (HTTP 429)");
                    })
                .onStatus(
                    HttpStatusCode::isError,
                    (req, resp) -> {
                      throw new AnthropicApiException(
                          "Anthropic API error: HTTP " + resp.getStatusCode().value());
                    })
                .body(AnthropicMessagesResponse.class);

        long durationMs = System.currentTimeMillis() - startTime;
        return mapResponse(response, durationMs);

      } catch (AnthropicRateLimitException e) {
        if (attempt >= maxAttempts) {
          throw new AnthropicApiException(
              "Rate limit exceeded after " + maxAttempts + " attempts", e);
        }
        long backoffMs = (long) Math.pow(2, attempt) * 1000L;
        log.warn(
            "Anthropic rate limited (attempt {}/{}), backing off {}ms",
            attempt,
            maxAttempts,
            backoffMs);
        sleep(backoffMs);
      }
    }

    // Should never reach here due to throw in the catch block
    throw new AnthropicApiException("Exhausted retry attempts");
  }

  private AiCompletionResponse mapResponse(AnthropicMessagesResponse response, long durationMs) {
    String content =
        response.content() != null && !response.content().isEmpty()
            ? response.content().getFirst().text()
            : "";

    var usage = response.usage();
    return new AiCompletionResponse(
        content,
        response.model(),
        usage != null ? usage.inputTokens() : 0,
        usage != null ? usage.outputTokens() : 0,
        usage != null ? usage.cacheReadInputTokens() : 0,
        usage != null ? usage.cacheCreationInputTokens() : 0,
        response.stopReason(),
        durationMs);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AnthropicApiException("Interrupted during backoff", e);
    }
  }

  /** Internal exception for rate limit responses — triggers retry logic. */
  static class AnthropicRateLimitException extends RuntimeException {
    AnthropicRateLimitException(String message) {
      super(message);
    }
  }

  /** Exception for unrecoverable Anthropic API errors. */
  static class AnthropicApiException extends RuntimeException {
    AnthropicApiException(String message) {
      super(message);
    }

    AnthropicApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
