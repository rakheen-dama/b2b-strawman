package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AnthropicApiClientTest {

  private static WireMockServer wireMock;
  private static String originalTransformerFactory;
  private AnthropicApiClient apiClient;

  @BeforeAll
  static void startWireMock() {
    originalTransformerFactory = System.getProperty("javax.xml.transform.TransformerFactory");
    System.setProperty(
        "javax.xml.transform.TransformerFactory",
        "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
    if (originalTransformerFactory != null) {
      System.setProperty("javax.xml.transform.TransformerFactory", originalTransformerFactory);
    } else {
      System.clearProperty("javax.xml.transform.TransformerFactory");
    }
  }

  @BeforeEach
  void setUp() {
    wireMock.resetAll();

    var properties =
        new AnthropicProperties("http://localhost:" + wireMock.port(), "2023-06-01", 10, 3);

    var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);

    var restClient =
        RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .requestFactory(requestFactory)
            .build();

    apiClient = new AnthropicApiClient(restClient, properties);
  }

  @Test
  void sendCompletion_buildsCorrectRequestBody() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(successResponse("Hello from Claude!", "claude-sonnet-4-20250514"))));

    var request =
        new AiCompletionRequest(
            "You are a helpful assistant.",
            "Hello, who are you?",
            "claude-sonnet-4-20250514",
            4096,
            0.7,
            Map.of());

    var response = apiClient.sendCompletion("sk-test-key", request);

    assertThat(response.content()).isEqualTo("Hello from Claude!");
    assertThat(response.model()).isEqualTo("claude-sonnet-4-20250514");

    // Verify request headers
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("sk-test-key"))
            .withHeader("anthropic-version", equalTo("2023-06-01"))
            .withHeader("Content-Type", containing("application/json")));

    // Verify request body has cache_control on system block
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withRequestBody(
                matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral")))
            .withRequestBody(
                matchingJsonPath("$.system[0].text", equalTo("You are a helpful assistant.")))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-sonnet-4-20250514")))
            .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("4096"))));
  }

  @Test
  void sendVisionCompletion_includesImageContentBlocks() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(successResponse("I see a document.", "claude-sonnet-4-20250514"))));

    var request =
        new AiVisionRequest(
            "You are a document analyzer.",
            "Describe this image.",
            "claude-sonnet-4-20250514",
            4096,
            0.3,
            Map.of(),
            List.of(new AiImageInput("image/png", "iVBORw0KGgoAAAANSUhEUg==")));

    var response = apiClient.sendVisionCompletion("sk-test-key", request);

    assertThat(response.content()).isEqualTo("I see a document.");

    // Verify image content block in request body
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withRequestBody(matchingJsonPath("$.messages[0].content[1].type", equalTo("image")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content[1].source.type", equalTo("base64")))
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[0].content[1].source.media_type", equalTo("image/png")))
            .withRequestBody(
                matchingJsonPath(
                    "$.messages[0].content[1].source.data", equalTo("iVBORw0KGgoAAAANSUhEUg=="))));
  }

  @Test
  void sendCompletion_parsesAllTokenCountFields() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "msg_01XFDUDYJgAACzvnptvVoYEL",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-20250514",
                          "content": [{"type": "text", "text": "Result"}],
                          "stop_reason": "end_turn",
                          "usage": {
                            "input_tokens": 150,
                            "output_tokens": 42,
                            "cache_read_input_tokens": 100,
                            "cache_creation_input_tokens": 50
                          }
                        }
                        """)));

    var request =
        new AiCompletionRequest(
            "System.", "User.", "claude-sonnet-4-20250514", 1000, 0.5, Map.of());

    var response = apiClient.sendCompletion("sk-test-key", request);

    assertThat(response.inputTokens()).isEqualTo(150);
    assertThat(response.outputTokens()).isEqualTo(42);
    assertThat(response.cacheReadInputTokens()).isEqualTo(100);
    assertThat(response.cacheCreationInputTokens()).isEqualTo(50);
    assertThat(response.stopReason()).isEqualTo("end_turn");
    assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void sendCompletion_cacheControlDirectivePresentOnSystemBlock() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(successResponse("OK", "claude-sonnet-4-20250514"))));

    var request =
        new AiCompletionRequest(
            "Important system prompt for caching.",
            "user input",
            "claude-sonnet-4-20250514",
            100,
            0.5,
            Map.of());

    apiClient.sendCompletion("sk-test-key", request);

    // Verify cache_control is on the system block
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/messages"))
            .withRequestBody(matchingJsonPath("$.system[0].type", equalTo("text")))
            .withRequestBody(
                matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral"))));
  }

  @Test
  void sendCompletion_retriesOn429ThenSucceeds() {
    // First call returns 429, second returns 200
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}"))
            .willSetStateTo("retried"));

    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .inScenario("retry")
            .whenScenarioStateIs("retried")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(successResponse("Success after retry", "claude-sonnet-4-20250514"))));

    var request =
        new AiCompletionRequest("sys", "user", "claude-sonnet-4-20250514", 100, 0.5, Map.of());

    var response = apiClient.sendCompletion("sk-test-key", request);

    assertThat(response.content()).isEqualTo("Success after retry");
    wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/messages")));
  }

  @Test
  void sendCompletion_throwsAfterExhaustingRetries() {
    // All calls return 429
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}")));

    var request =
        new AiCompletionRequest("sys", "user", "claude-sonnet-4-20250514", 100, 0.5, Map.of());

    // Use properties with maxRetries=2 for faster test
    var fastProperties =
        new AnthropicProperties("http://localhost:" + wireMock.port(), "2023-06-01", 10, 2);
    var fastHttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    var fastRestClient =
        RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .requestFactory(new JdkClientHttpRequestFactory(fastHttpClient))
            .build();
    var fastClient = new AnthropicApiClient(fastRestClient, fastProperties);

    assertThatThrownBy(() -> fastClient.sendCompletion("sk-test-key", request))
        .isInstanceOf(AnthropicApiClient.AnthropicApiException.class)
        .hasMessageContaining("Rate limit exceeded after 3 attempts");
  }

  @Test
  void testConnection_returnsSuccessOnValidKey() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(successResponse("ok", "claude-haiku-4-5"))));

    var result = apiClient.testConnection("sk-valid-key");

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("anthropic");
    assertThat(result.errorMessage()).isNull();
  }

  private String successResponse(String text, String model) {
    return """
        {
          "id": "msg_01XFDUDYJgAACzvnptvVoYEL",
          "type": "message",
          "role": "assistant",
          "model": "%s",
          "content": [{"type": "text", "text": "%s"}],
          "stop_reason": "end_turn",
          "usage": {
            "input_tokens": 10,
            "output_tokens": 5,
            "cache_read_input_tokens": 0,
            "cache_creation_input_tokens": 0
          }
        }
        """
        .formatted(model, text);
  }
}
