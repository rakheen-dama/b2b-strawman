package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Wire-format DTO for the Anthropic Messages API response body. Internal to the anthropic package —
 * not exposed outside.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AnthropicMessagesResponse(
    @JsonProperty("id") String id,
    @JsonProperty("model") String model,
    @JsonProperty("content") List<ContentBlock> content,
    @JsonProperty("stop_reason") String stopReason,
    @JsonProperty("usage") Usage usage) {

  /** A content block in the response (typically text). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ContentBlock(@JsonProperty("type") String type, @JsonProperty("text") String text) {}

  /** Token usage metrics including prompt caching fields. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Usage(
      @JsonProperty("input_tokens") int inputTokens,
      @JsonProperty("output_tokens") int outputTokens,
      @JsonProperty("cache_read_input_tokens") int cacheReadInputTokens,
      @JsonProperty("cache_creation_input_tokens") int cacheCreationInputTokens) {}
}
