package io.b2mash.b2b.b2bstrawman.assistant.provider.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProvider;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ModelInfo;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolDefinition;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@IntegrationAdapter(domain = IntegrationDomain.AI, slug = "anthropic")
public class AnthropicLlmProvider implements LlmChatProvider {

  private static final Logger log = LoggerFactory.getLogger(AnthropicLlmProvider.class);
  private static final String API_VERSION = "2023-06-01";
  private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final HttpClient httpClient;
  private final String baseUrl;

  public AnthropicLlmProvider() {
    this(DEFAULT_BASE_URL);
  }

  /** Package-private constructor for testing with a custom base URL (e.g. WireMock). */
  AnthropicLlmProvider(String baseUrl) {
    this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    this.baseUrl = baseUrl;
  }

  @Override
  public String providerId() {
    return "anthropic";
  }

  @Override
  public List<ModelInfo> availableModels() {
    return List.of(
        new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", true),
        new ModelInfo("claude-opus-4-6", "Claude Opus 4.6", false),
        new ModelInfo("claude-haiku-4-5", "Claude Haiku 4.5", false));
  }

  @Override
  public void chat(ChatRequest request, Consumer<StreamEvent> eventConsumer) {
    try {
      var body = buildRequestBody(request, true);
      var jsonBody = OBJECT_MAPPER.writeValueAsString(body);

      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/v1/messages"))
              .header("x-api-key", request.apiKey())
              .header("anthropic-version", API_VERSION)
              .header("content-type", "application/json")
              .header("accept", "text/event-stream")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

      int status = response.statusCode();
      if (status == 401 || status == 403) {
        log.warn("AnthropicLlmProvider: authentication error (HTTP {})", status);
        eventConsumer.accept(
            new StreamEvent.Error(
                "Invalid API key. Please check your Anthropic API key in Settings."));
        return;
      }
      if (status == 429) {
        log.warn("AnthropicLlmProvider: rate limit exceeded (HTTP 429)");
        eventConsumer.accept(
            new StreamEvent.Error("Rate limit exceeded. Please wait a moment and try again."));
        return;
      }
      if (status >= 500) {
        log.warn("AnthropicLlmProvider: server error (HTTP {})", status);
        eventConsumer.accept(new StreamEvent.Error("Provider unavailable. Please try again."));
        return;
      }

      parseSseStream(response.body(), eventConsumer);

    } catch (Exception e) {
      log.warn("AnthropicLlmProvider: unexpected error during chat", e);
      eventConsumer.accept(
          new StreamEvent.Error("Unable to reach the AI provider. Please try again."));
    }
  }

  @Override
  public boolean validateKey(String apiKey, String model) {
    try {
      var body = new LinkedHashMap<String, Object>();
      body.put("model", model);
      body.put("max_tokens", 1);
      body.put("messages", List.of(Map.of("role", "user", "content", "Hi")));
      body.put("stream", false);

      var jsonBody = OBJECT_MAPPER.writeValueAsString(body);

      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/v1/messages"))
              .header("x-api-key", apiKey)
              .header("anthropic-version", API_VERSION)
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      log.debug("AnthropicLlmProvider: validateKey failed", e);
      return false;
    }
  }

  private void parseSseStream(InputStream stream, Consumer<StreamEvent> eventConsumer) {
    try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      int inputTokens = 0;
      String activeToolId = null;
      String activeToolName = null;
      var toolInput = new StringBuilder();
      boolean inToolBlock = false;

      SseChunk chunk;
      while ((chunk = parseSseChunk(reader)) != null) {
        if (chunk.data() == null || chunk.data().isBlank()) continue;

        try {
          JsonNode root = OBJECT_MAPPER.readTree(chunk.data());
          String type = root.path("type").asText("");

          switch (type) {
            case "message_start" -> {
              inputTokens = root.path("message").path("usage").path("input_tokens").asInt(0);
            }
            case "content_block_start" -> {
              JsonNode block = root.path("content_block");
              if ("tool_use".equals(block.path("type").asText())) {
                activeToolId = block.path("id").asText();
                activeToolName = block.path("name").asText();
                toolInput.setLength(0);
                inToolBlock = true;
              } else {
                inToolBlock = false;
              }
            }
            case "content_block_delta" -> {
              JsonNode delta = root.path("delta");
              String deltaType = delta.path("type").asText("");
              if ("text_delta".equals(deltaType)) {
                eventConsumer.accept(new StreamEvent.TextDelta(delta.path("text").asText()));
              } else if ("input_json_delta".equals(deltaType)) {
                toolInput.append(delta.path("partial_json").asText(""));
              }
            }
            case "content_block_stop" -> {
              if (inToolBlock && activeToolId != null) {
                Map<String, Object> input =
                    toolInput.length() > 0
                        ? OBJECT_MAPPER.readValue(toolInput.toString(), new TypeReference<>() {})
                        : Map.of();
                eventConsumer.accept(new StreamEvent.ToolUse(activeToolId, activeToolName, input));
                activeToolId = null;
                activeToolName = null;
                toolInput.setLength(0);
                inToolBlock = false;
              }
            }
            case "message_delta" -> {
              int outputTokens = root.path("usage").path("output_tokens").asInt(0);
              eventConsumer.accept(new StreamEvent.Usage(inputTokens, outputTokens));
            }
            case "message_stop" -> {
              eventConsumer.accept(new StreamEvent.Done());
            }
            case "error" -> {
              String message = root.path("error").path("message").asText("Unknown error");
              log.warn("AnthropicLlmProvider: API error event: {}", message);
              eventConsumer.accept(new StreamEvent.Error(message));
            }
            default -> log.debug("AnthropicLlmProvider: ignoring SSE event type: {}", type);
          }
        } catch (Exception e) {
          log.warn("AnthropicLlmProvider: failed to parse SSE chunk: {}", chunk.data(), e);
        }
      }
    } catch (Exception e) {
      log.warn("AnthropicLlmProvider: error reading SSE stream", e);
      eventConsumer.accept(
          new StreamEvent.Error("Unable to reach the AI provider. Please try again."));
    }
  }

  private SseChunk parseSseChunk(BufferedReader reader) throws IOException {
    String eventType = null;
    var data = new StringBuilder();
    String line;

    while ((line = reader.readLine()) != null) {
      if (line.isBlank()) {
        // Blank line = end of SSE event block
        if (eventType != null || !data.isEmpty()) {
          return new SseChunk(eventType, data.toString());
        }
        // Leading blank line - skip and continue
      } else if (line.startsWith("event:")) {
        eventType = line.substring("event:".length()).trim();
      } else if (line.startsWith("data:")) {
        String dataLine = line.substring("data:".length()).trim();
        if (!data.isEmpty()) data.append("\n");
        data.append(dataLine);
      }
      // Ignore id:, retry:, comment lines starting with ':'
    }

    // EOF - return remaining chunk if any content
    if (eventType != null || !data.isEmpty()) {
      return new SseChunk(eventType, data.toString());
    }
    return null;
  }

  private record SseChunk(String event, String data) {}

  private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
    var body = new LinkedHashMap<String, Object>();
    body.put("model", request.model());
    body.put("max_tokens", 4096);
    body.put("stream", stream);

    if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
      body.put("system", request.systemPrompt());
    }

    body.put("messages", buildMessages(request.messages()));

    if (request.tools() != null && !request.tools().isEmpty()) {
      body.put("tools", buildTools(request.tools()));
    }

    return body;
  }

  private List<Map<String, Object>> buildMessages(List<ChatMessage> messages) {
    var result = new ArrayList<Map<String, Object>>();
    for (var msg : messages) {
      var m = new LinkedHashMap<String, Object>();
      m.put("role", msg.role());

      if (!msg.toolResults().isEmpty()) {
        // Tool result message - content is an array of tool_result blocks
        var contentBlocks = new ArrayList<Map<String, Object>>();
        for (ToolResult tr : msg.toolResults()) {
          var block = new LinkedHashMap<String, Object>();
          block.put("type", "tool_result");
          block.put("tool_use_id", tr.toolCallId());
          block.put("content", tr.content());
          contentBlocks.add(block);
        }
        m.put("content", contentBlocks);
      } else {
        m.put("content", msg.content());
      }
      result.add(m);
    }
    return result;
  }

  private List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
    var result = new ArrayList<Map<String, Object>>();
    for (var tool : tools) {
      var t = new LinkedHashMap<String, Object>();
      t.put("name", tool.name());
      t.put("description", tool.description());
      t.put("input_schema", tool.inputSchema());
      result.add(t);
    }
    return result;
  }
}
