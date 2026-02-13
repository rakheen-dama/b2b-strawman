package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgSettingsService {

  private static final Logger log = LoggerFactory.getLogger(OrgSettingsService.class);
  private static final String DEFAULT_CURRENCY = "USD";

  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;

  public OrgSettingsService(
      OrgSettingsRepository orgSettingsRepository, AuditService auditService) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
  }

  /**
   * Returns the org settings for the current tenant. Never returns null — if no row exists, returns
   * a default response with "USD" as the currency without persisting.
   */
  @Transactional(readOnly = true)
  public OrgSettingsResponse getSettings() {
    return orgSettingsRepository.findAll().stream()
        .findFirst()
        .map(s -> new OrgSettingsResponse(s.getDefaultCurrency()))
        .orElse(new OrgSettingsResponse(DEFAULT_CURRENCY));
  }

  /**
   * Creates or updates the org settings for the current tenant. Only admin/owner can invoke.
   *
   * @param defaultCurrency the new default currency code (3-char ISO 4217)
   * @param memberId the requesting member's UUID
   * @param orgRole the requesting member's org role
   * @return the updated settings response
   */
  @Transactional
  public OrgSettingsResponse updateSettings(String defaultCurrency, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    var existing = orgSettingsRepository.findAll().stream().findFirst();

    OrgSettings settings;
    String oldCurrency;

    if (existing.isPresent()) {
      settings = existing.get();
      oldCurrency = settings.getDefaultCurrency();
      settings.updateCurrency(defaultCurrency);
      settings = orgSettingsRepository.save(settings);
      log.info("Updated org settings: currency {} -> {}", oldCurrency, defaultCurrency);
    } else {
      oldCurrency = DEFAULT_CURRENCY;
      settings = new OrgSettings(defaultCurrency);
      settings = orgSettingsRepository.save(settings);
      log.info("Created org settings with currency {}", defaultCurrency);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("default_currency", Map.of("from", oldCurrency, "to", defaultCurrency)))
            .build());

    return new OrgSettingsResponse(settings.getDefaultCurrency());
  }

  /**
   * Returns the stored default currency for the current tenant, or "USD" if no settings row exists.
   * Useful for other services that need the org default currency.
   */
  @Transactional(readOnly = true)
  public String getDefaultCurrency() {
    return orgSettingsRepository.findAll().stream()
        .findFirst()
        .map(OrgSettings::getDefaultCurrency)
        .orElse(DEFAULT_CURRENCY);
  }

  private void requireAdminOrOwner(String orgRole) {
    if (!Roles.ORG_ADMIN.equals(orgRole) && !Roles.ORG_OWNER.equals(orgRole)) {
      throw new ForbiddenException(
          "Insufficient permissions", "Only admins and owners can update org settings");
    }
  }

  /** Response DTO for org settings. */
  public record OrgSettingsResponse(String defaultCurrency) {}
}
