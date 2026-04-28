package io.b2mash.b2b.b2bstrawman.portal.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>The {@link #runWeeklyDigest(RunOptions)} overload exists to support manual triggering from the
 * {@code POST /internal/portal/digest/run-weekly} endpoint (GAP-L-99). The cron path delegates to
 * {@link #runWeeklyDigest()} which calls the overload with {@link RunOptions#full()}, keeping the
 * scheduled tick byte-identical to its previous behaviour.
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
   * Public entry point preserved for the cron tick + the existing {@code
   * PortalDigestSchedulerIntegrationTest}. Delegates to {@link #runWeeklyDigest(RunOptions)} with
   * {@link RunOptions#full()} (full-tenant sweep, no email filter, real send) and discards the
   * result so the observable side effects remain identical to the previous implementation.
   */
  public void runWeeklyDigest() {
    runWeeklyDigest(RunOptions.full());
  }

  /**
   * Manual entry point. Sweeps tenant schemas (every tenant when {@code options.orgIdOrNull} is
   * null, otherwise the single matching tenant) and dispatches digests subject to the configured
   * cadence + per-contact preference toggles. When {@code options.targetEmailOrNull} is non-null,
   * filters the per-tenant active-contact list to the matching email (case-insensitive). When
   * {@code options.dryRun} is true, performs all selection / content assembly but skips the actual
   * SMTP send; the result still reports the would-have-sent count.
   *
   * <p>The cron path uses {@link RunOptions#full()} so it remains byte-identical to the prior
   * implementation: full-tenant sweep, no recipient filter, real send.
   *
   * @throws io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException when {@code
   *     options.orgIdOrNull} is non-null and no matching {@code org_schema_mapping} row exists.
   */
  public RunResult runWeeklyDigest(RunOptions options) {
    Objects.requireNonNull(options, "options");

    List<OrgSchemaMapping> mappings;
    if (options.orgIdOrNull() != null) {
      OrgSchemaMapping mapping =
          orgSchemaMappingRepository
              .findByClerkOrgId(options.orgIdOrNull())
              .orElseThrow(
                  () ->
                      io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException.withDetail(
                          "Organization not found",
                          "No tenant provisioned for orgId: " + options.orgIdOrNull()));
      mappings = List.of(mapping);
    } else {
      mappings = orgSchemaMappingRepository.findAll();
    }

    int totalTenantsProcessed = 0;
    int totalDigestsSent = 0;
    int totalSkipped = 0;
    List<RunResult.Error> errors = new ArrayList<>();

    for (var mapping : mappings) {
      String schema = mapping.getSchemaName();
      String orgId = mapping.getExternalOrgId();
      try {
        TenantResult tenantResult =
            ScopedValue.where(RequestScopes.TENANT_ID, schema)
                .where(RequestScopes.ORG_ID, orgId)
                .call(() -> processTenant(options));
        if (tenantResult != null) {
          totalDigestsSent += tenantResult.sent();
          totalSkipped += tenantResult.skipped();
          errors.addAll(tenantResult.errors());
        }
        totalTenantsProcessed++;
      } catch (Exception e) {
        log.warn("Portal digest sweep failed for schema {}: {}", schema, e.getMessage(), e);
        errors.add(new RunResult.Error(schema, null, e.getMessage()));
      }
    }

    log.info(
        "Portal digest sweep complete: {} tenants processed, {} digest emails {}, {} skipped, {}"
            + " errors",
        totalTenantsProcessed,
        totalDigestsSent,
        options.dryRun() ? "would-have-sent (dryRun)" : "sent",
        totalSkipped,
        errors.size());

    return new RunResult(
        totalTenantsProcessed, totalDigestsSent, totalSkipped, options.dryRun(), errors);
  }

  /**
   * Runs within a tenant ScopedValue binding. Returns a {@link TenantResult} with per-tenant
   * counts; the parent loop aggregates across tenants. Per-contact exceptions are caught + appended
   * to the returned errors list (mirroring the prior {@code log.warn} convention).
   */
  private TenantResult processTenant(RunOptions options) {
    var settingsOpt =
        transactionTemplate.execute(tx -> orgSettingsRepository.findForCurrentTenant());
    OrgSettings settings = settingsOpt == null ? null : settingsOpt.orElse(null);
    PortalDigestCadence cadence =
        settings != null ? settings.getEffectivePortalDigestCadence() : PortalDigestCadence.WEEKLY;

    if (cadence == PortalDigestCadence.OFF) {
      log.debug("Skipping tenant {} -- cadence=OFF", RequestScopes.getTenantIdOrNull());
      return TenantResult.empty();
    }

    if (cadence == PortalDigestCadence.BIWEEKLY && settings != null) {
      Instant lastSent = settings.getDigestLastSentAt();
      if (lastSent != null
          && Duration.between(lastSent, Instant.now()).compareTo(BIWEEKLY_SKIP_WINDOW) < 0) {
        log.debug(
            "Skipping tenant {} -- BIWEEKLY skip window active (last sent {})",
            RequestScopes.getTenantIdOrNull(),
            lastSent);
        return TenantResult.empty();
      }
    }

    String targetEmailLower =
        options.targetEmailOrNull() == null
            ? null
            : options.targetEmailOrNull().trim().toLowerCase(java.util.Locale.ROOT);

    List<PortalContact> activeContacts =
        transactionTemplate.execute(
            tx ->
                portalContactRepository.findAll().stream()
                    .filter(c -> c.getStatus() == PortalContact.ContactStatus.ACTIVE)
                    .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
                    .filter(
                        c ->
                            targetEmailLower == null
                                || c.getEmail()
                                    .trim()
                                    .toLowerCase(java.util.Locale.ROOT)
                                    .equals(targetEmailLower))
                    .toList());

    if (activeContacts == null || activeContacts.isEmpty()) {
      return TenantResult.empty();
    }

    int sent = 0;
    int skipped = 0;
    List<RunResult.Error> errors = new ArrayList<>();
    String schema = RequestScopes.getTenantIdOrNull();

    for (PortalContact contact : activeContacts) {
      try {
        DispatchOutcome outcome = dispatchDigestForContact(contact, options.dryRun());
        switch (outcome) {
          case SENT -> sent++;
          case SKIPPED -> skipped++;
        }
      } catch (Exception e) {
        log.warn(
            "Digest send failed for contact {} in tenant {}: {}",
            contact.getId(),
            schema,
            e.getMessage());
        errors.add(new RunResult.Error(schema, String.valueOf(contact.getId()), e.getMessage()));
      }
    }

    if (sent > 0 && !options.dryRun()) {
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

    return new TenantResult(sent, skipped, errors);
  }

  private DispatchOutcome dispatchDigestForContact(PortalContact contact, boolean dryRun) {
    // Check per-contact digest preference (defaults to true via getOrCreate).
    var preference =
        transactionTemplate.execute(tx -> preferenceService.getOrCreate(contact.getId()));
    if (preference == null || !preference.isDigestEnabled()) {
      return DispatchOutcome.SKIPPED;
    }

    Map<String, Object> bundle = contentAssembler.assemble(contact.getId(), DIGEST_LOOKBACK_DAYS);
    if (bundle == null) {
      // Empty 7-day lookback — suppress the email, never send empty digests.
      return DispatchOutcome.SKIPPED;
    }

    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(contact.getDisplayName(), null);
    context.putAll(bundle);
    String orgName = context.getOrDefault("orgName", productName).toString();
    context.put("subject", orgName + ": Your weekly update");
    context.put("portalBaseUrl", portalBaseUrl);
    context.put("portalHomeUrl", portalBaseUrl + "/home");

    if (dryRun) {
      // Selection + content assembly succeeded; would-have-sent. Skip SMTP delivery and
      // digestLastSentAt stamping (handled in processTenant via the dryRun flag).
      return DispatchOutcome.SENT;
    }

    return portalEmailService.sendDigestEmail(contact, context)
        ? DispatchOutcome.SENT
        : DispatchOutcome.SKIPPED;
  }

  /** Outcome of a single contact's dispatch attempt. */
  private enum DispatchOutcome {
    SENT,
    SKIPPED
  }

  /** Internal per-tenant aggregation; not exposed on the public API. */
  private record TenantResult(int sent, int skipped, List<RunResult.Error> errors) {
    static TenantResult empty() {
      return new TenantResult(0, 0, List.of());
    }
  }

  /**
   * Options for {@link #runWeeklyDigest(RunOptions)}. All fields are nullable / default-false; the
   * static {@link #full()} factory mirrors the no-arg cron behaviour.
   *
   * @param orgIdOrNull when non-null, restrict the sweep to this single tenant (resolved via {@code
   *     org_schema_mapping}). When null, sweep every tenant.
   * @param targetEmailOrNull when non-null, after the per-tenant ACTIVE-contact filter,
   *     additionally keep only contacts whose email matches case-insensitively. Useful for QA
   *     single-recipient sends.
   * @param dryRun when true, run selection + content assembly but skip the SMTP delivery; counts
   *     "would-have-sent" contacts as {@code digestsSent}. Does not stamp {@code digestLastSentAt}.
   */
  public record RunOptions(String orgIdOrNull, String targetEmailOrNull, boolean dryRun) {
    /** Cron-equivalent options: full-tenant sweep, no recipient filter, real send. */
    public static RunOptions full() {
      return new RunOptions(null, null, false);
    }
  }

  /**
   * Result of a {@link #runWeeklyDigest(RunOptions)} call. Returned to the manual-trigger
   * controller; the cron path discards it.
   *
   * @param tenantsProcessed count of tenants the sweep visited (excluding any that threw before
   *     {@code processTenant} returned).
   * @param digestsSent count of digests actually sent — or, when {@code dryRun=true}, the
   *     would-have-sent count.
   * @param skipped count of contacts that were considered but skipped (preference off, empty
   *     content bundle, or {@link PortalEmailService#sendDigestEmail} returning false).
   * @param dryRun echoes the inbound option for caller convenience.
   * @param errors per-tenant or per-contact failures captured during the sweep. The presence of
   *     errors does not abort the sweep — the controller still returns 200 and the caller decides
   *     how to react.
   */
  public record RunResult(
      int tenantsProcessed, int digestsSent, int skipped, boolean dryRun, List<Error> errors) {

    /** Per-failure entry. {@code contactId} is null when the failure was at tenant-scope. */
    public record Error(String schema, String contactId, String message) {}
  }
}
