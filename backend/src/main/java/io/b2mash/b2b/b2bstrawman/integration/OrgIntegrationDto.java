package io.b2mash.b2b.b2bstrawman.integration;

import java.time.Instant;

/** Response record for integration configuration state. */
public record OrgIntegrationDto(
    String domain,
    String providerSlug,
    boolean enabled,
    String keySuffix,
    String configJson,
    Instant updatedAt) {

  /** Maps an {@link OrgIntegration} entity to the response DTO. */
  public static OrgIntegrationDto from(OrgIntegration entity) {
    return new OrgIntegrationDto(
        entity.getDomain().name(),
        entity.getProviderSlug(),
        entity.isEnabled(),
        entity.getKeySuffix(),
        entity.getConfigJson(),
        entity.getUpdatedAt());
  }

  /** Synthesizes a DTO for a domain that has no configuration yet. */
  public static OrgIntegrationDto unconfigured(IntegrationDomain domain) {
    return new OrgIntegrationDto(domain.name(), null, false, null, null, null);
  }
}
