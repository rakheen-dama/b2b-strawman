package io.b2mash.b2b.b2bstrawman.integration.ai.cost;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AI model pricing and exchange rates.
 *
 * @param pricing per-model pricing (USD per million tokens)
 * @param exchangeRate currency exchange rates
 * @param timeoutSeconds AI invocation timeout
 * @param maxDocumentSizeBytes maximum size for a single document input
 * @param maxTotalDocumentSizeBytes maximum total size for all document inputs
 */
@ConfigurationProperties(prefix = "kazi.ai")
public record AiPricingProperties(
    Map<String, ModelPricing> pricing,
    ExchangeRate exchangeRate,
    int timeoutSeconds,
    long maxDocumentSizeBytes,
    long maxTotalDocumentSizeBytes) {

  public record ModelPricing(
      double inputPerMToken,
      double outputPerMToken,
      double cacheReadPerMToken,
      double cacheCreationPerMToken) {}

  public record ExchangeRate(double usdToZar) {}
}
