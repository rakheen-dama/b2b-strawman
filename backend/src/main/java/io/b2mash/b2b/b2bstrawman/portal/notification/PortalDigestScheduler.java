package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalEmailService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.PortalDigestCadence;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Weekly portal digest scheduler (Epic 498B, Phase 68, ADR-258). Runs every Monday 08:00 local time
 * (cron {@code "0 0 8 ? * MON"}), iterates every tenant schema registered in {@code
 * org_schema_mapping}, and for each tenant with a non-OFF cadence dispatches a digest email to
 * every active portal contact with the {@code digestEnabled} preference on.
 *
 * <p>Per-tenant BIWEEKLY cadence is enforced via a 12-day skip window keyed on {@code
 * org_settings.digest_last_sent_at}. WEEKLY runs every Monday. OFF never runs.
 *
 * <p>Content assembly is delegated to {@link PortalDigestContentAssembler#assemble(java.util.UUID,
 * int)}. A {@code null} bundle signals "nothing worth reporting" and suppresses that contact's send
 * (no empty-digest spam).
 *
 * <p>Following the fire-and-forget convention of other portal email flows, per-tenant exceptions
 * are caught + logged so a single malformed tenant never aborts the whole cron sweep.
 */
@Component
public class PortalDigestScheduler {

  private static final Logger log = LoggerFactory.getLogger(PortalDigestScheduler.class);

  private static final int DIGEST_LOOKBACK_DAYS = 7;

  /** BIWEEKLY skip window: if last send was within 12 days, skip this Monday. */
  private static final Duration BIWEEKLY_SKIP_WINDOW = Duration.ofDays(12);

  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final PortalContactRepository portalContactRepository;
  private final PortalNotificationPreferenceService preferenceService;
  private final PortalDigestContentAssembler contentAssembler;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final TransactionTemplate transactionTemplate;
  private final String portalBaseUrl;
  private final String productName;

  public PortalDigestScheduler(
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      OrgSettingsRepository orgSettingsRepository,
      PortalContactRepository portalContactRepository,
      PortalNotificationPreferenceService preferenceService,
      PortalDigestContentAssembler contentAssembler,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      TransactionTemplate transactionTemplate,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.portalContactRepository = portalContactRepository;
    this.preferenceService = preferenceService;
    this.contentAssembler = contentAssembler;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.transactionTemplate = transactionTemplate;
    this.portalBaseUrl = portalBaseUrl;
    this.productName = productName;
  }

  /**
   * Cron entry point — Monday 08:00 server time. Tests call {@link #runWeeklyDigest()} directly.
   */
  @Scheduled(cron = "0 0 8 ? * MON")
  public void scheduledRun() {
    runWeeklyDigest();
  }

  /**
   * Public entry point for tests + the cron trigger. Sweeps every tenant schema and dispatches
   * digests per the configured cadence + per-contact preference toggles.
   */
  public void runWeeklyDigest() {
    var mappings = orgSchemaMappingRepository.findAll();
    int totalTenantsProcessed = 0;
    int totalDigestsSent = 0;

    for (var mapping : mappings) {
      String schema = mapping.getSchemaName();
      String orgId = mapping.getExternalOrgId();
      try {
        Integer sentForTenant =
            ScopedValue.where(RequestScopes.TENANT_ID, schema)
                .where(RequestScopes.ORG_ID, orgId)
                .call(this::processTenant);
        if (sentForTenant != null) {
          totalDigestsSent += sentForTenant;
        }
        totalTenantsProcessed++;
      } catch (Exception e) {
        log.warn("Portal digest sweep failed for schema {}: {}", schema, e.getMessage(), e);
      }
    }

    log.info(
        "Portal digest sweep complete: {} tenants processed, {} digest emails sent",
        totalTenantsProcessed,
        totalDigestsSent);
  }

  /**
   * Runs within a tenant ScopedValue binding. Returns the count of digests sent for this tenant.
   */
  private int processTenant() {
    var settingsOpt =
        transactionTemplate.execute(tx -> orgSettingsRepository.findForCurrentTenant());
    OrgSettings settings = settingsOpt == null ? null : settingsOpt.orElse(null);
    PortalDigestCadence cadence =
        settings != null ? settings.getEffectivePortalDigestCadence() : PortalDigestCadence.WEEKLY;

    if (cadence == PortalDigestCadence.OFF) {
      log.debug("Skipping tenant {} -- cadence=OFF", RequestScopes.getTenantIdOrNull());
      return 0;
    }

    if (cadence == PortalDigestCadence.BIWEEKLY && settings != null) {
      Instant lastSent = settings.getDigestLastSentAt();
      if (lastSent != null
          && Duration.between(lastSent, Instant.now()).compareTo(BIWEEKLY_SKIP_WINDOW) < 0) {
        log.debug(
            "Skipping tenant {} -- BIWEEKLY skip window active (last sent {})",
            RequestScopes.getTenantIdOrNull(),
            lastSent);
        return 0;
      }
    }

    List<PortalContact> activeContacts =
        transactionTemplate.execute(
            tx ->
                portalContactRepository.findAll().stream()
                    .filter(c -> c.getStatus() == PortalContact.ContactStatus.ACTIVE)
                    .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
                    .toList());

    if (activeContacts == null || activeContacts.isEmpty()) {
      return 0;
    }

    int sent = 0;
    for (PortalContact contact : activeContacts) {
      try {
        if (dispatchDigestForContact(contact)) {
          sent++;
        }
      } catch (Exception e) {
        log.warn(
            "Digest send failed for contact {} in tenant {}: {}",
            contact.getId(),
            RequestScopes.getTenantIdOrNull(),
            e.getMessage());
      }
    }

    if (sent > 0) {
      transactionTemplate.executeWithoutResult(
          tx ->
              orgSettingsRepository
                  .findForCurrentTenant()
                  .ifPresent(
                      s -> {
                        s.markDigestSent(Instant.now());
                        orgSettingsRepository.save(s);
                      }));
    }

    return sent;
  }

  private boolean dispatchDigestForContact(PortalContact contact) {
    // Check per-contact digest preference (defaults to true via getOrCreate).
    var preference =
        transactionTemplate.execute(tx -> preferenceService.getOrCreate(contact.getId()));
    if (preference == null || !preference.isDigestEnabled()) {
      return false;
    }

    Map<String, Object> bundle = contentAssembler.assemble(contact.getId(), DIGEST_LOOKBACK_DAYS);
    if (bundle == null) {
      // Empty 7-day lookback — suppress the email, never send empty digests.
      return false;
    }

    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    context.putAll(bundle);
    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put("subject", orgName + ": Your weekly update");
    context.put("portalBaseUrl", portalBaseUrl);
    context.put("portalHomeUrl", portalBaseUrl + "/home");

    return portalEmailService.sendDigestEmail(contact, context);
  }
}
