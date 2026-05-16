package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Wire-format DTO for the Anthropic Messages API request body. Internal to the anthropic package —
 * not exposed outside.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record AnthropicMessagesRequest(
    @JsonProperty("model") String model,
    @JsonProperty("max_tokens") int maxTokens,
    @JsonProperty("system") List<SystemBlock> system,
    @JsonProperty("messages") List<Message> messages,
    @JsonProperty("temperature") Double temperature) {

  /** A system content block with optional cache_control directive. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record SystemBlock(
      @JsonProperty("type") String type,
      @JsonProperty("text") String text,
      @JsonProperty("cache_control") Map<String, String> cacheControl) {}

  /** A message in the conversation. */
  record Message(@JsonProperty("role") String role, @JsonProperty("content") Object content) {}

  /** A text content block within a message. */
  record TextContent(@JsonProperty("type") String type, @JsonProperty("text") String text) {
    TextContent(String text) {
      this("text", text);
    }
  }

  /** An image content block for vision requests. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ImageContent(
      @JsonProperty("type") String type, @JsonProperty("source") ImageSource source) {}

  /** Image source with base64-encoded data. */
  record ImageSource(
      @JsonProperty("type") String type,
      @JsonProperty("media_type") String mediaType,
      @JsonProperty("data") String data) {}
}
