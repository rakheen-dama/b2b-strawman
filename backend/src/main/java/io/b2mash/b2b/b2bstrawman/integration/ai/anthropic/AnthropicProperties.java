package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Anthropic AI integration.
 *
 * @param apiBaseUrl base URL for the Anthropic Messages API
 * @param apiVersion API version header value (anthropic-version)
 * @param timeoutSeconds HTTP request timeout in seconds
 * @param maxRetries maximum number of retries on 429 rate-limit responses
 */
@ConfigurationProperties(prefix = "kazi.ai.anthropic")
public record AnthropicProperties(
    String apiBaseUrl, String apiVersion, int timeoutSeconds, int maxRetries) {

  public AnthropicProperties {
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
      apiBaseUrl = "https://api.anthropic.com";
    }
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = "2023-06-01";
    }
    if (timeoutSeconds <= 0) {
      timeoutSeconds = 120;
    }
    if (maxRetries <= 0) {
      maxRetries = 3;
    }
  }
}
