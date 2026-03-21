package io.b2mash.b2b.b2bstrawman.integration;

/** Shared utility for building integration secret key strings. */
public final class IntegrationKeys {

  private IntegrationKeys() {}

  /**
   * Builds the secret store key for an integration's API key.
   *
   * @param domain the integration domain (e.g. AI, PAYMENT)
   * @param providerSlug the provider slug (e.g. "anthropic", "stripe")
   * @return key in format "{domain}:{slug}:api_key"
   */
  public static String apiKey(IntegrationDomain domain, String providerSlug) {
    return domain.name().toLowerCase() + ":" + providerSlug + ":api_key";
  }

  /**
   * Convenience shorthand for AI domain API keys.
   *
   * @param providerSlug the AI provider slug (e.g. "anthropic")
   * @return key in format "ai:{slug}:api_key"
   */
  public static String aiApiKey(String providerSlug) {
    return apiKey(IntegrationDomain.AI, providerSlug);
  }
}
