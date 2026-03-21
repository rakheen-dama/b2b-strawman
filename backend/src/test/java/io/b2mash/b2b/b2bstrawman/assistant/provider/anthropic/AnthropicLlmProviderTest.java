package io.b2mash.b2b.b2bstrawman.assistant.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicLlmProviderTest {

  private static WireMockServer wireMock;
  private static String originalTransformerFactory;
  private AnthropicLlmProvider provider;

  @BeforeAll
  static void startWireMock() {
    // Force JDK default TransformerFactory to avoid docx4j's impl which doesn't support
    // indent-number (causes WireMock's FormatXmlHelper initialization to fail)
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
    provider = new AnthropicLlmProvider("http://localhost:" + wireMock.port());
  }

  private ChatRequest simpleChatRequest() {
    return new ChatRequest(
        "test-key",
        "claude-sonnet-4-6",
        "You are helpful.",
        List.of(new ChatMessage("user", "Hello", null)),
        List.of());
  }

  // --- Test 1: Text delta parsing ---

  @Test
  void chat_parsesTextDelta_fromContentBlockDeltaEvent() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        buildSseBody(
                            """
                            event: message_start
                            data: {"type":"message_start","message":{"usage":{"input_tokens":10,"output_tokens":0}}}

                            event: content_block_start
                            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":0}

                            event: message_delta
                            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}

                            event: message_stop
                            data: {"type":"message_stop"}

                            """))));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events)
        .anySatisfy(
            e ->
                assertThat(e)
                    .isInstanceOf(StreamEvent.TextDelta.class)
                    .extracting("text")
                    .isEqualTo("Hello"));
    assertThat(events)
        .anySatisfy(
            e ->
                assertThat(e)
                    .isInstanceOf(StreamEvent.TextDelta.class)
                    .extracting("text")
                    .isEqualTo(" world"));
    assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOf(StreamEvent.Usage.class));
    assertThat(events).last().isInstanceOf(StreamEvent.Done.class);
  }

  // --- Test 2: Tool use parsing ---

  @Test
  void chat_parsesToolUse_fromContentBlockStartWithToolUse() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        buildSseBody(
                            """
                            event: message_start
                            data: {"type":"message_start","message":{"usage":{"input_tokens":15,"output_tokens":0}}}

                            event: content_block_start
                            data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_01abc","name":"list_projects"}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"status\\":"}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\\"active\\"}"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":0}

                            event: message_delta
                            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":8}}

                            event: message_stop
                            data: {"type":"message_stop"}

                            """))));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events)
        .anySatisfy(
            e -> {
              assertThat(e).isInstanceOf(StreamEvent.ToolUse.class);
              var toolUse = (StreamEvent.ToolUse) e;
              assertThat(toolUse.toolCallId()).isEqualTo("toolu_01abc");
              assertThat(toolUse.toolName()).isEqualTo("list_projects");
              assertThat(toolUse.input()).containsEntry("status", "active");
            });
    assertThat(events).last().isInstanceOf(StreamEvent.Done.class);
  }

  // --- Test 3: Usage and Done parsing ---

  @Test
  void chat_parsesUsageAndDone_fromMessageDeltaAndMessageStop() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        buildSseBody(
                            """
                            event: message_start
                            data: {"type":"message_start","message":{"usage":{"input_tokens":20,"output_tokens":0}}}

                            event: message_delta
                            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":10}}

                            event: message_stop
                            data: {"type":"message_stop"}

                            """))));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events)
        .anySatisfy(
            e -> {
              assertThat(e).isInstanceOf(StreamEvent.Usage.class);
              var usage = (StreamEvent.Usage) e;
              assertThat(usage.inputTokens()).isEqualTo(20);
              assertThat(usage.outputTokens()).isEqualTo(10);
            });
    assertThat(events).last().isInstanceOf(StreamEvent.Done.class);
  }

  // --- Test 4: Full multi-turn (text + tool_use + text) ---

  @Test
  void chat_fullMultiTurn_textThenToolUseThenText_emitsEventsInOrder() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        buildSseBody(
                            """
                            event: message_start
                            data: {"type":"message_start","message":{"usage":{"input_tokens":25,"output_tokens":0}}}

                            event: content_block_start
                            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"I'll check"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":0}

                            event: content_block_start
                            data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_02xyz","name":"get_project"}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"projectId\\":"}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\\"abc-123\\"}"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":1}

                            event: content_block_start
                            data: {"type":"content_block_start","index":2,"content_block":{"type":"text","text":""}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"Here's the result"}}

                            event: content_block_stop
                            data: {"type":"content_block_stop","index":2}

                            event: message_delta
                            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}

                            event: message_stop
                            data: {"type":"message_stop"}

                            """))));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    // Filter to only the meaningful event types (text, tool_use, usage, done)
    var meaningful =
        events.stream()
            .filter(
                e ->
                    e instanceof StreamEvent.TextDelta
                        || e instanceof StreamEvent.ToolUse
                        || e instanceof StreamEvent.Usage
                        || e instanceof StreamEvent.Done)
            .toList();

    assertThat(meaningful).hasSize(5);
    assertThat(meaningful.get(0)).isInstanceOf(StreamEvent.TextDelta.class);
    assertThat(((StreamEvent.TextDelta) meaningful.get(0)).text()).isEqualTo("I'll check");
    assertThat(meaningful.get(1)).isInstanceOf(StreamEvent.ToolUse.class);
    var toolUse = (StreamEvent.ToolUse) meaningful.get(1);
    assertThat(toolUse.toolCallId()).isEqualTo("toolu_02xyz");
    assertThat(toolUse.toolName()).isEqualTo("get_project");
    assertThat(toolUse.input()).containsEntry("projectId", "abc-123");
    assertThat(meaningful.get(2)).isInstanceOf(StreamEvent.TextDelta.class);
    assertThat(((StreamEvent.TextDelta) meaningful.get(2)).text()).isEqualTo("Here's the result");
    assertThat(meaningful.get(3)).isInstanceOf(StreamEvent.Usage.class);
    assertThat(meaningful.get(4)).isInstanceOf(StreamEvent.Done.class);
  }

  // --- Test 5: Error on 401 ---

  @Test
  void chat_emitsError_on401Response() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}")));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StreamEvent.Error.class);
    assertThat(((StreamEvent.Error) events.getFirst()).message()).contains("Invalid API key");
  }

  // --- Test 6: Error on 429 ---

  @Test
  void chat_emitsError_on429Response() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"rate limit exceeded\"}}")));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StreamEvent.Error.class);
    assertThat(((StreamEvent.Error) events.getFirst()).message()).contains("Rate limit exceeded");
  }

  // --- Test 7a: validateKey returns true on 200 ---

  @Test
  void validateKey_returnsTrue_on200() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\":\"msg_1\",\"type\":\"message\"}")));

    assertThat(provider.validateKey("test-key", "claude-sonnet-4-6")).isTrue();
  }

  // --- Test 7b: validateKey returns false on 401 ---

  @Test
  void validateKey_returnsFalse_on401() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}")));

    assertThat(provider.validateKey("bad-key", "claude-sonnet-4-6")).isFalse();
  }

  // --- Test 8: Error on 5xx server error ---

  @Test
  void chat_emitsError_on500Response() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"Internal server error\"}}")));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StreamEvent.Error.class);
    assertThat(((StreamEvent.Error) events.getFirst()).message()).contains("Provider unavailable");
  }

  // --- Test 9: SSE-level error event mid-stream ---

  @Test
  void chat_emitsError_onSseLevelErrorEvent() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        buildSseBody(
                            """
                            event: message_start
                            data: {"type":"message_start","message":{"usage":{"input_tokens":10,"output_tokens":0}}}

                            event: content_block_start
                            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                            event: content_block_delta
                            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

                            event: error
                            data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}

                            """))));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events)
        .anySatisfy(
            e ->
                assertThat(e)
                    .isInstanceOf(StreamEvent.TextDelta.class)
                    .extracting("text")
                    .isEqualTo("Hello"));
    assertThat(events)
        .anySatisfy(
            e -> {
              assertThat(e).isInstanceOf(StreamEvent.Error.class);
              assertThat(((StreamEvent.Error) e).message()).isEqualTo("Overloaded");
            });
  }

  // --- Test 10: Error on other 4xx (e.g. 400 Bad Request) ---

  @Test
  void chat_emitsError_on400Response() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"max_tokens: must be positive\"}}")));

    List<StreamEvent> events = new ArrayList<>();
    provider.chat(simpleChatRequest(), events::add);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StreamEvent.Error.class);
    assertThat(((StreamEvent.Error) events.getFirst()).message()).contains("Request error");
    assertThat(((StreamEvent.Error) events.getFirst()).message()).contains("400");
  }

  /** WireMock expects \n line endings in SSE body. */
  private String buildSseBody(String raw) {
    return raw.replace("\r\n", "\n");
  }
}
