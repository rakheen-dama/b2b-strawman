package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.LlmJsonParser;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections.CashDigestOutput;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.integration.email.SendResult;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import io.b2mash.b2b.b2bstrawman.reporting.AgingBuckets;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.UnbilledTimeSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Weekly cash digest (Phase 83, ADR-328, §3.5). Assembles the deterministic {@link CashDigestData}
 * from queries, narrates it with the {@code cash-digest} AI skill when AI is available, and
 * delivers an in-app {@code CASH_DIGEST} notification + one email per owner/admin through the
 * standard pipeline. Runs per tenant from {@link CashDigestHandler} with {@code TENANT_ID}
 * pre-bound.
 *
 * <p><strong>Table always wins.</strong> Every figure is query-derived and rendered by the template
 * regardless of the narration; the AI owns only prose (narrative + ranked risks). When AI is
 * disabled/unconfigured, or narration fails for any reason, the digest degrades to a numbers-only
 * email (one Thymeleaf conditional) — the job never crashes on AI unavailability (§6.4). The digest
 * is the owner's lockup report and ships ungated (no {@code collectionsEnabled} gate — design note
 * 5). A member's notification preference mutes only the in-app bell (via {@code createIfEnabled});
 * the email still goes to every owner/admin — the mute governs the bell, not the email (§8.4).
 *
 * <p><strong>No outer transaction.</strong> {@link #processTenant()} is deliberately NOT
 * {@code @Transactional} (mirroring {@code PortalDigestScheduler}): a single DB transaction must
 * never be held open across the LLM round-trip or the per-recipient SMTP sends. Assembly runs in
 * its own short read-only transaction; each delivery side effect (bell, delivery-log, audit)
 * commits in its own transaction, so an already-sent email can never be rolled back by a later
 * failure.
 */
@Service
public class CashDigestService {

  private static final Logger log = LoggerFactory.getLogger(CashDigestService.class);

  static final String SKILL_ID = "cash-digest";
  static final String TEMPLATE_NAME = "cash-digest";
  static final String REFERENCE_TYPE = "CASH_DIGEST";
  static final String NOTIFICATION_TYPE = "CASH_DIGEST";
  static final String AUDIT_EVENT_TYPE = "collections.digest.sent";
  static final String AUDIT_ENTITY_TYPE = "cash_digest";
  static final String NOOP_PROVIDER = "noop";

  private static final int TRAILING_DAYS = 7;
  private static final int STALE_WIP_DAYS = 30;
  private static final int TOP_RISKS = 5;
  private static final int MAX_AI_RISKS = 3;

  private static final String AGE_EXPR = "CURRENT_DATE - i.due_date";

  private static final String TOTALS_SQL =
      """
      SELECT
          COALESCE(SUM(i.total), 0) AS outstanding_total,
          COALESCE(SUM(i.total) FILTER (WHERE %1$s), 0) AS bucket_current,
          COALESCE(SUM(i.total) FILTER (WHERE %2$s), 0) AS bucket_d30,
          COALESCE(SUM(i.total) FILTER (WHERE %3$s), 0) AS bucket_d60,
          COALESCE(SUM(i.total) FILTER (WHERE %4$s), 0) AS bucket_d90plus
      FROM invoices i
      WHERE i.status = 'SENT'
        AND i.due_date IS NOT NULL
      """
          .formatted(
              AgingBuckets.currentPredicate(AGE_EXPR),
              AgingBuckets.d30Predicate(AGE_EXPR),
              AgingBuckets.d60Predicate(AGE_EXPR),
              AgingBuckets.d90PlusPredicate(AGE_EXPR));

  // billed = issued-and-sent in the trailing window (issue_date, status SENT|PAID); collected =
  // paid in the trailing window (paid_at). paid_at is set by all three payment routes (manual /
  // webhook / Xero poll), so it captures collections that PaymentEvent (PSP-only) would miss.
  private static final String BILLED_COLLECTED_SQL =
      """
      SELECT
          COALESCE(SUM(i.total) FILTER (
              WHERE i.issue_date >= :billedSince AND i.status IN ('SENT', 'PAID')), 0) AS billed,
          COALESCE(SUM(i.total) FILTER (
              WHERE i.status = 'PAID' AND i.paid_at >= :collectedSince), 0) AS collected
      FROM invoices i
      """;

  private final EntityManager entityManager;
  private final TimeEntryRepository timeEntryRepository;
  private final CollectionActivityRepository activityRepository;
  private final CollectionsReadService collectionsReadService;
  private final AiSkillExecutionService skillExecutionService;
  private final AiFirmProfileRepository firmProfileRepository;
  private final IntegrationRegistry integrationRegistry;
  private final LlmJsonParser llmJsonParser;
  private final ObjectMapper objectMapper;
  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final NotificationService notificationService;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final AuditService auditService;

  /**
   * Short read-only transaction for the deterministic assembly pass. Explicit (not method-level
   * {@code @Transactional}) so it applies on every call path — including {@code processTenant()}'s
   * self-invocation of {@link #assembleData()}, where an annotation would be bypassed by Spring's
   * proxy AOP — without holding a transaction across narration/delivery.
   */
  private final TransactionTemplate readTransactionTemplate;

  public CashDigestService(
      EntityManager entityManager,
      TimeEntryRepository timeEntryRepository,
      CollectionActivityRepository activityRepository,
      CollectionsReadService collectionsReadService,
      AiSkillExecutionService skillExecutionService,
      AiFirmProfileRepository firmProfileRepository,
      IntegrationRegistry integrationRegistry,
      LlmJsonParser llmJsonParser,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      NotificationService notificationService,
      EmailContextBuilder emailContextBuilder,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      AuditService auditService,
      PlatformTransactionManager transactionManager) {
    this.entityManager = entityManager;
    this.timeEntryRepository = timeEntryRepository;
    this.activityRepository = activityRepository;
    this.collectionsReadService = collectionsReadService;
    this.skillExecutionService = skillExecutionService;
    this.firmProfileRepository = firmProfileRepository;
    this.integrationRegistry = integrationRegistry;
    this.llmJsonParser = llmJsonParser;
    this.objectMapper = objectMapper;
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.notificationService = notificationService;
    this.emailContextBuilder = emailContextBuilder;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.auditService = auditService;
    this.readTransactionTemplate = new TransactionTemplate(transactionManager);
    this.readTransactionTemplate.setReadOnly(true);
  }

  /**
   * Assemble → narrate → deliver for the current tenant. Deliberately NOT {@code @Transactional} —
   * assembly gets its own short read-only transaction, narration runs transaction-free (the LLM
   * call must not pin a DB connection), and each delivery side effect commits in its own
   * transaction so a failure mid-recipient-loop can never roll back emails that were already sent.
   */
  public void processTenant() {
    CashDigestData data = assembleData();
    CashDigestOutput narration = narrate(data);
    deliver(data, narration);
  }

  /**
   * Pure, deterministic assembly of the digest numbers (593A.2) — testable without AI. Total
   * outstanding + four-way aging buckets, billed vs collected for the trailing 7 days, stale
   * unbilled WIP older than 30 days, reminder-activity counts by status, and the top debtor risks
   * (reusing the 591A/592A debtor book, which already carries the triage signals).
   *
   * <p>Runs inside a short read-only transaction via {@link #readTransactionTemplate} — a real
   * transaction on every call path (proxy or self-invocation from {@link #processTenant()}) that is
   * committed before narration and delivery begin, so the DB connection is not held across the LLM
   * round-trip.
   */
  public CashDigestData assembleData() {
    return readTransactionTemplate.execute(status -> assembleDataInTransaction());
  }

  private CashDigestData assembleDataInTransaction() {
    Tuple totals =
        (Tuple) entityManager.createNativeQuery(TOTALS_SQL, Tuple.class).getSingleResult();
    BigDecimal outstandingTotal = toBigDecimal(totals.get("outstanding_total"));
    var buckets =
        new CashDigestData.Buckets(
            toBigDecimal(totals.get("bucket_current")),
            toBigDecimal(totals.get("bucket_d30")),
            toBigDecimal(totals.get("bucket_d60")),
            toBigDecimal(totals.get("bucket_d90plus")));

    var billedCollectedQuery = entityManager.createNativeQuery(BILLED_COLLECTED_SQL, Tuple.class);
    billedCollectedQuery.setParameter("billedSince", LocalDate.now().minusDays(TRAILING_DAYS));
    billedCollectedQuery.setParameter(
        "collectedSince", Timestamp.from(Instant.now().minus(Duration.ofDays(TRAILING_DAYS))));
    Tuple billedCollected = (Tuple) billedCollectedQuery.getSingleResult();
    BigDecimal billed = toBigDecimal(billedCollected.get("billed"));
    BigDecimal collected = toBigDecimal(billedCollected.get("collected"));

    UnbilledTimeSummaryProjection wip =
        timeEntryRepository.countUnbilledOlderThan(LocalDate.now().minusDays(STALE_WIP_DAYS));
    long staleWipEntryCount = wip != null ? wip.getEntryCount() : 0L;
    double staleWipHours = wip != null ? wip.getTotalHours() : 0.0;

    Instant activitySince = Instant.now().minus(Duration.ofDays(TRAILING_DAYS));
    Map<String, Long> activityCounts = new LinkedHashMap<>();
    for (CollectionActivityStatusCount row : activityRepository.countByStatusSince(activitySince)) {
      activityCounts.put(row.getStatus().name(), row.getCount());
    }

    List<CashDigestData.TopRisk> topRisks =
        collectionsReadService.getDebtors(PageRequest.of(0, TOP_RISKS)).getContent().stream()
            .map(
                d ->
                    new CashDigestData.TopRisk(
                        d.customerName(),
                        d.outstandingTotal(),
                        d.currency(),
                        d.signals(),
                        d.signalDetails()))
            .toList();

    return new CashDigestData(
        outstandingTotal,
        buckets,
        billed,
        collected,
        staleWipEntryCount,
        staleWipHours,
        activityCounts,
        topRisks);
  }

  /**
   * Narrate the digest with the {@code cash-digest} skill, or return {@code null} to fall back to
   * numbers-only. Mirrors {@code AiReminderComposer}'s two pre-flights (provider not {@code noop};
   * a firm-profile row exists — checked WITHOUT creating one, since job context has no MEMBER_ID),
   * then treats any non-COMPLETED status or thrown exception as "AI unavailable". The job never
   * crashes on AI failure (§6.4).
   */
  private CashDigestOutput narrate(CashDigestData data) {
    AiProvider provider = integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class);
    if (NOOP_PROVIDER.equals(provider.providerId())) {
      return null;
    }
    if (firmProfileRepository.count() == 0) {
      return null;
    }
    UUID entityId =
        orgSettingsRepository.findForCurrentTenant().map(OrgSettings::getId).orElse(null);
    if (entityId == null) {
      return null;
    }
    try {
      String digestJson = objectMapper.writeValueAsString(data);
      var context =
          new SkillContext(
              entityId,
              AUDIT_ENTITY_TYPE,
              "Weekly cash digest narration",
              Map.of("digest_data", digestJson));
      SkillExecutionResult result =
          skillExecutionService.executeSkill(SKILL_ID, context, null, List.of());
      if (!"COMPLETED".equals(result.execution().getStatus())) {
        log.warn(
            "Cash digest narration status {} — falling back to numbers-only",
            result.execution().getStatus());
        return null;
      }
      CashDigestOutput output =
          llmJsonParser.parse(
              objectMapper, result.execution().getOutputContent(), CashDigestOutput.class);
      // Prompt-injection / hallucination guard: drop any AI risk whose customer isn't in the
      // assembled debtor book. The template's numbers already win (ADR-328 A1); this keeps a
      // hallucinated debtor name out of the prose too. Grounding happens before truncation.
      output = dropUngroundedRisks(output, data);
      // Defensive truncation — the prompt asks for at most three risks; enforce it in code too.
      if (output.topRisks() != null && output.topRisks().size() > MAX_AI_RISKS) {
        output =
            new CashDigestOutput(output.narrative(), output.topRisks().subList(0, MAX_AI_RISKS));
      }
      return output;
    } catch (RuntimeException e) {
      log.warn("Cash digest narration failed — falling back to numbers-only: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Drops any AI-ranked risk whose {@code customerName} does not match a debtor in the assembled
   * {@link CashDigestData#topRisks()} book (case-insensitive, trimmed). The template already prints
   * only the authoritative deterministic figures (ADR-328 A1), so this is defence-in-depth against
   * a hallucinated or injected debtor name reaching the owner's inbox in prose.
   */
  private CashDigestOutput dropUngroundedRisks(CashDigestOutput output, CashDigestData data) {
    if (output.topRisks() == null || output.topRisks().isEmpty()) {
      return output;
    }
    Set<String> knownDebtors =
        data.topRisks().stream()
            .map(r -> normalizeName(r.customerName()))
            .filter(n -> !n.isEmpty())
            .collect(Collectors.toSet());
    List<CashDigestOutput.TopRisk> grounded =
        output.topRisks().stream()
            .filter(r -> knownDebtors.contains(normalizeName(r.customerName())))
            .toList();
    if (grounded.size() == output.topRisks().size()) {
      return output;
    }
    log.warn(
        "Cash digest: dropped {} AI risk(s) whose customer is not in the debtor book",
        output.topRisks().size() - grounded.size());
    return new CashDigestOutput(output.narrative(), grounded);
  }

  private static String normalizeName(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Deliver the digest to every owner/admin: an in-app {@code CASH_DIGEST} notification (via {@code
   * createIfEnabled}, so members can mute it) plus one email through the standard pipeline. A
   * failed send for one recipient never aborts the rest. One {@code collections.digest.sent} audit
   * per run.
   */
  private void deliver(CashDigestData data, CashDigestOutput narration) {
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping cash digest — no tenant context bound");
      return;
    }
    UUID orgSettingsId =
        orgSettingsRepository.findForCurrentTenant().map(OrgSettings::getId).orElse(null);
    if (orgSettingsId == null) {
      log.warn("Skipping cash digest — no OrgSettings row for tenant");
      return;
    }
    String tenantSchema = RequestScopes.TENANT_ID.get();

    List<Member> recipients = memberRepository.findByRoleSlugsIn(List.of("admin", "owner"));
    EmailProvider provider =
        integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

    int emailsSent = 0;
    for (Member member : recipients) {
      // Bell — preference-respecting, so members can mute it (returns null when muted).
      notificationService.createIfEnabled(
          member.getId(),
          NOTIFICATION_TYPE,
          "Your weekly cash digest is ready",
          null,
          REFERENCE_TYPE,
          orgSettingsId,
          null);

      String email = member.getEmail();
      if (email == null || email.isBlank()) {
        log.warn("Cash digest: owner/admin {} has no email — skipping email leg", member.getId());
        continue;
      }

      Map<String, Object> context = buildEmailContext(member.getName(), data, narration);
      var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Cash digest rate-limited for {} — recording rate-limited delivery", email);
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, orgSettingsId, TEMPLATE_NAME, email, provider.providerId());
        continue;
      }

      var message =
          EmailMessage.withTracking(
              email,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              orgSettingsId.toString(),
              tenantSchema);

      SendResult result;
      try {
        result = provider.sendEmail(message);
      } catch (RuntimeException e) {
        log.error("Cash digest send threw for {}: {}", email, e.getMessage());
        result = new SendResult(false, null, e.getMessage());
      }

      deliveryLogService.record(
          REFERENCE_TYPE, orgSettingsId, TEMPLATE_NAME, email, provider.providerId(), result);
      if (result.success()) {
        emailsSent++;
      }
    }

    // Ids/counts only in details — no client PII (POPIA). All values are Strings.
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(AUDIT_EVENT_TYPE)
            .entityType(AUDIT_ENTITY_TYPE)
            .entityId(orgSettingsId)
            .actorType("SYSTEM")
            .source("SCHEDULER")
            .details(
                Map.of(
                    "recipients", String.valueOf(recipients.size()),
                    "emails_sent", String.valueOf(emailsSent),
                    "ai_narrated", String.valueOf(narration != null),
                    "outstanding_total", data.outstandingTotal().toPlainString()))
            .build());

    log.info(
        "Cash digest delivered — recipients={}, emails_sent={}, ai_narrated={}",
        recipients.size(),
        emailsSent,
        narration != null);
  }

  /**
   * Builds the email context. The {@link CashDigestData} figures are ALWAYS present (the template's
   * authoritative tables); the narrative + AI risks are added ONLY when narration succeeded — the
   * template wraps that whole section in one {@code th:if="${narrative != null}"} conditional
   * (ADR-328 B1). Deterministic triage signals ride on {@code topRisks} and render regardless.
   */
  private Map<String, Object> buildEmailContext(
      String recipientName, CashDigestData data, CashDigestOutput narration) {
    Map<String, Object> context = emailContextBuilder.buildBaseContext(recipientName, null);
    context.put("subject", "Weekly cash digest");
    context.put("outstandingTotal", data.outstandingTotal().toPlainString());
    context.put("bucketCurrent", data.buckets().current().toPlainString());
    context.put("bucketD30", data.buckets().d30().toPlainString());
    context.put("bucketD60", data.buckets().d60().toPlainString());
    context.put("bucketD90Plus", data.buckets().d90plus().toPlainString());
    context.put("billed", data.billed().toPlainString());
    context.put("collected", data.collected().toPlainString());
    context.put("staleWipEntryCount", data.staleWipEntryCount());
    context.put("staleWipHours", String.format(Locale.ROOT, "%.1f", data.staleWipHours()));
    context.put("activityCounts", data.activityCountsByStatus());
    context.put("topRisks", data.topRisks());

    if (narration != null) {
      context.put("narrative", narration.narrative());
      context.put("aiRisks", narration.topRisks());
    }
    return context;
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    return new BigDecimal(value.toString());
  }
}
