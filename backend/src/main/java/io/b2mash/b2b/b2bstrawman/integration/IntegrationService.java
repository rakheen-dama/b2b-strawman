package io.b2mash.b2b.b2bstrawman.integration;

import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentGateway;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.integration.signing.DocumentSigningProvider;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates integration configuration, secrets management, and connection testing. */
@Service
public class IntegrationService {

  private final OrgIntegrationRepository orgIntegrationRepository;
  private final IntegrationRegistry integrationRegistry;
  private final SecretStore secretStore;
  private final AuditService auditService;
  private final LlmChatProviderRegistry llmChatProviderRegistry;

  public IntegrationService(
      OrgIntegrationRepository orgIntegrationRepository,
      IntegrationRegistry integrationRegistry,
      SecretStore secretStore,
      AuditService auditService,
      LlmChatProviderRegistry llmChatProviderRegistry) {
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.integrationRegistry = integrationRegistry;
    this.secretStore = secretStore;
    this.auditService = auditService;
    this.llmChatProviderRegistry = llmChatProviderRegistry;
  }

  /**
   * Creates or updates an integration configuration for the given domain. If an integration already
   * exists for the domain, updates its provider and config. Otherwise creates a new one.
   */
  @Transactional
  public OrgIntegrationDto upsertIntegration(
      IntegrationDomain domain, String providerSlug, String configJson) {
    var existing = orgIntegrationRepository.findByDomain(domain);
    OrgIntegration integration;
    String previousProvider = null;

    if (existing.isPresent()) {
      integration = existing.get();
      previousProvider = integration.getProviderSlug();
      integration.updateProvider(providerSlug, configJson);
    } else {
      integration = new OrgIntegration(domain, providerSlug);
      integration.updateProvider(providerSlug, configJson);
    }

    integration = orgIntegrationRepository.save(integration);
    evictCache(domain);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.configured")
            .entityType("org_integration")
            .entityId(integration.getId())
            .details(
                previousProvider != null
                    ? Map.of(
                        "domain", domain.name(),
                        "providerSlug", providerSlug,
                        "previousProvider", previousProvider)
                    : Map.of("domain", domain.name(), "providerSlug", providerSlug))
            .build());

    return OrgIntegrationDto.from(integration);
  }

  /**
   * Toggles an existing integration on or off. Throws if the domain has not been configured yet.
   */
  @Transactional
  public OrgIntegrationDto toggleIntegration(IntegrationDomain domain, boolean enabled) {
    var integration = findByDomainOrThrow(domain);

    if (enabled) {
      integration.enable();
    } else {
      integration.disable();
    }

    integration = orgIntegrationRepository.save(integration);
    evictCache(domain);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType(enabled ? "integration.enabled" : "integration.disabled")
            .entityType("org_integration")
            .entityId(integration.getId())
            .details(Map.of("domain", domain.name(), "providerSlug", integration.getProviderSlug()))
            .build());

    return OrgIntegrationDto.from(integration);
  }

  /**
   * Stores an API key for the configured integration. The full key is persisted in the secret
   * store; only the last 6 characters are saved on the entity for display purposes.
   */
  @Transactional
  public void setApiKey(IntegrationDomain domain, String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new InvalidStateException("API key required", "API key must not be blank");
    }
    var integration = findByDomainOrThrow(domain);
    var secretKey = buildSecretKey(domain, integration.getProviderSlug());

    secretStore.store(secretKey, apiKey);

    var suffix = apiKey.length() > 6 ? apiKey.substring(apiKey.length() - 6) : apiKey;
    integration.setKeySuffix(suffix);
    orgIntegrationRepository.save(integration);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.key_set")
            .entityType("org_integration")
            .entityId(integration.getId())
            .details(Map.of("domain", domain.name()))
            .build());
  }

  /** Removes the API key for the configured integration from both the secret store and entity. */
  @Transactional
  public void deleteApiKey(IntegrationDomain domain) {
    var integration = findByDomainOrThrow(domain);
    var secretKey = buildSecretKey(domain, integration.getProviderSlug());

    secretStore.delete(secretKey);
    integration.clearKeySuffix();
    orgIntegrationRepository.save(integration);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.key_removed")
            .entityType("org_integration")
            .entityId(integration.getId())
            .details(Map.of("domain", domain.name()))
            .build());
  }

  /**
   * Tests connectivity to the provider configured for the given domain. Resolves the adapter via
   * the registry and delegates to its {@code testConnection()} method.
   */
  @Transactional
  public ConnectionTestResult testConnection(IntegrationDomain domain) {
    var result =
        switch (domain) {
          case ACCOUNTING ->
              integrationRegistry.resolve(domain, AccountingProvider.class).testConnection();
          case AI -> testAiConnection(domain);
          case DOCUMENT_SIGNING ->
              integrationRegistry.resolve(domain, DocumentSigningProvider.class).testConnection();
          case EMAIL -> integrationRegistry.resolve(domain, EmailProvider.class).testConnection();
          case PAYMENT ->
              integrationRegistry.resolve(domain, PaymentGateway.class).testConnection();
        };

    var integration = orgIntegrationRepository.findByDomain(domain);
    integration.ifPresent(
        i ->
            auditService.log(
                AuditEventBuilder.builder()
                    .eventType("integration.connection_tested")
                    .entityType("org_integration")
                    .entityId(i.getId())
                    .details(
                        result.errorMessage() != null
                            ? Map.of(
                                "domain", domain.name(),
                                "providerSlug", i.getProviderSlug(),
                                "success", result.success(),
                                "errorMessage", result.errorMessage())
                            : Map.of(
                                "domain", domain.name(),
                                "providerSlug", i.getProviderSlug(),
                                "success", result.success()))
                    .build()));

    return result;
  }

  /**
   * Lists integration status for every domain. Domains without configuration are returned as
   * unconfigured DTOs.
   */
  @Transactional(readOnly = true)
  public List<OrgIntegrationDto> listAllIntegrations() {
    return Arrays.stream(IntegrationDomain.values())
        .map(
            domain ->
                orgIntegrationRepository
                    .findByDomain(domain)
                    .map(OrgIntegrationDto::from)
                    .orElseGet(() -> OrgIntegrationDto.unconfigured(domain)))
        .toList();
  }

  /**
   * Returns available provider slugs grouped by domain name. Includes all registered adapters
   * (including noop).
   */
  public Map<String, List<String>> availableProviders() {
    var result = new LinkedHashMap<String, List<String>>();
    for (var domain : IntegrationDomain.values()) {
      result.put(domain.name(), integrationRegistry.availableProviders(domain));
    }
    return result;
  }

  private OrgIntegration findByDomainOrThrow(IntegrationDomain domain) {
    return orgIntegrationRepository
        .findByDomain(domain)
        .orElseThrow(() -> new ResourceNotFoundException("OrgIntegration", domain.name()));
  }

  private void evictCache(IntegrationDomain domain) {
    var tenantSchema = RequestScopes.TENANT_ID.get();
    integrationRegistry.evict(tenantSchema, domain);
  }

  private ConnectionTestResult testAiConnection(IntegrationDomain domain) {
    var integration = orgIntegrationRepository.findByDomain(domain);
    if (integration.isEmpty() || "noop".equals(integration.get().getProviderSlug())) {
      return integrationRegistry.resolve(domain, AiProvider.class).testConnection();
    }
    var slug = integration.get().getProviderSlug();
    try {
      var provider = llmChatProviderRegistry.get(slug);
      var secretKey = "ai:" + slug + ":api_key";
      if (!secretStore.exists(secretKey)) {
        return new ConnectionTestResult(false, slug, "No API key configured");
      }
      var apiKey = secretStore.retrieve(secretKey);
      var model = parseModel(integration.get().getConfigJson());
      var ok = provider.validateKey(apiKey, model);
      return new ConnectionTestResult(ok, slug, ok ? null : "API key validation failed");
    } catch (IllegalArgumentException e) {
      return new ConnectionTestResult(false, slug, "Provider not found: " + slug);
    }
  }

  private static String parseModel(String configJson) {
    if (configJson == null || configJson.isBlank()) return "claude-sonnet-4-6";
    try {
      var idx = configJson.indexOf("\"model\"");
      if (idx < 0) return "claude-sonnet-4-6";
      var afterColon = configJson.indexOf("\"", idx + 8);
      var end = configJson.indexOf("\"", afterColon + 1);
      return configJson.substring(afterColon + 1, end);
    } catch (Exception e) {
      return "claude-sonnet-4-6";
    }
  }

  private static String buildSecretKey(IntegrationDomain domain, String providerSlug) {
    return domain.name().toLowerCase() + ":" + providerSlug + ":api_key";
  }
}
