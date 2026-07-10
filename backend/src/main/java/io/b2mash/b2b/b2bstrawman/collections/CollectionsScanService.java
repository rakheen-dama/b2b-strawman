package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.settings.CollectionsSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The deterministic dunning engine (Phase 83, ADR-325). One {@link #scanForTenant()} call derives
 * overdue candidates at query time, selects the <em>highest eligible stage per invoice per
 * scan</em> (recording lower un-actioned stages as {@code SKIPPED(superseded_by_higher_stage)} so
 * the {@code (invoice, stage)} ledger stays complete), flags {@code ESCALATION} deterministically
 * (row + {@code COLLECTION_ESCALATED} notification + audit — no gate, no AI, no email), and
 * delegates drafting to the {@link ReminderComposer} seam.
 *
 * <p>Fully integration-testable without any AI machinery: with the 589 default {@link
 * NoOpReminderComposer}, every due reminder lands as {@code SKIPPED(draft_unavailable)} —
 * retryable, so each activity is automatically re-proposed once slice 590A's real composer deploys.
 */
@Service
public class CollectionsScanService {

  private static final Logger log = LoggerFactory.getLogger(CollectionsScanService.class);

  static final String REASON_SUPERSEDED = "superseded_by_higher_stage";
  static final String REASON_NO_RECIPIENT = "no_recipient";
  static final String REASON_DRAFT_UNAVAILABLE = "draft_unavailable";
  static final String REASON_DRAFT_FAILED = "draft_failed";
  static final String REASON_AI_UNAVAILABLE = "ai_unavailable";

  private static final List<CollectionStage> REMINDER_STAGES =
      List.of(CollectionStage.STAGE_1, CollectionStage.STAGE_2, CollectionStage.STAGE_3);

  private static final String CANDIDATE_SQL =
      """
      SELECT i.id             AS invoice_id,
             i.customer_id     AS customer_id,
             i.customer_email  AS customer_email,
             (CURRENT_DATE - i.due_date) AS days_overdue
      FROM   invoices i
      JOIN   customers c ON c.id = i.customer_id
      WHERE  i.status = 'SENT'
        AND  i.due_date < CURRENT_DATE
        AND  c.collections_exempt = false
      ORDER  BY i.due_date
      """;

  private final EntityManager entityManager;
  private final OrgSettingsRepository orgSettingsRepository;
  private final CollectionActivityRepository activityRepository;
  private final InvoiceRepository invoiceRepository;
  private final CustomerRepository customerRepository;
  private final ReminderComposer reminderComposer;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final AiExecutionGateService gateService;

  /**
   * REQUIRES_NEW template that isolates each candidate in its own physical transaction so one bad
   * row (a flush-time constraint race, a throwing {@code expirePendingGate}/audit write, or a
   * composer that participates in the tx) cannot mark the shared scan transaction rollback-only and
   * poison the whole tenant scan (§3.1). Mirrors the per-item isolation of {@code
   * AutomationActionExecutor} / {@code BillingRunGenerationService}.
   *
   * <p>The escalation notification ({@code notifyAdminsAndOwners}, itself REQUIRES_NEW) is
   * deliberately fired <em>outside</em> this template — from {@link #scanForTenant()} only after
   * the candidate tx commits — so it can never commit ahead of the ESCALATION row and produce a
   * duplicate notification when a later failure rolls the candidate row back.
   */
  private final TransactionTemplate requiresNewTransactionTemplate;

  public CollectionsScanService(
      EntityManager entityManager,
      OrgSettingsRepository orgSettingsRepository,
      CollectionActivityRepository activityRepository,
      InvoiceRepository invoiceRepository,
      CustomerRepository customerRepository,
      ReminderComposer reminderComposer,
      NotificationService notificationService,
      AuditService auditService,
      AiExecutionGateService gateService,
      PlatformTransactionManager transactionManager) {
    this.entityManager = entityManager;
    this.orgSettingsRepository = orgSettingsRepository;
    this.activityRepository = activityRepository;
    this.invoiceRepository = invoiceRepository;
    this.customerRepository = customerRepository;
    this.reminderComposer = reminderComposer;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.gateService = gateService;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Counts for the job log. */
  public record ScanResult(int proposed, int skipped, int escalated, int superseded) {}

  /**
   * Runs one tenant's daily scan (steps 1-6 of ADR-325 §3.1). Tenant {@code search_path} is already
   * bound by the {@code JobWorker}. No-ops when collections is disabled (or no {@code org_settings}
   * row exists).
   */
  @Transactional
  public ScanResult scanForTenant() {
    // Step 1 — policy load.
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    if (settings == null || !settings.getCollections().isCollectionsEnabled()) {
      log.debug("CollectionsScanService: collections disabled or unconfigured — no-op");
      return new ScanResult(0, 0, 0, 0);
    }
    var policy = settings.getCollections();

    // Step 2 — candidate query (exempt customers produce no rows at all).
    var candidates = loadCandidates();

    int proposed = 0;
    int skipped = 0;
    int escalated = 0;
    int superseded = 0;

    for (var candidate : candidates) {
      try {
        // REQUIRES_NEW: each candidate commits/rolls back in its own physical transaction, so a
        // persistence failure on one row (constraint race, throwing gate-expiry/audit write, or a
        // participating composer) rolls back ONLY that candidate and cannot mark the shared scan
        // transaction rollback-only — the loop below keeps processing later candidates cleanly.
        var outcome =
            requiresNewTransactionTemplate.execute(status -> processCandidate(candidate, policy));
        proposed += outcome.proposed();
        skipped += outcome.skipped();
        escalated += outcome.escalated();
        superseded += outcome.superseded();
        // Fire the escalation notification ONLY after the candidate transaction has committed, so
        // the ESCALATION row + audit are durable first. A crash between commit and send loses at
        // most one notification (re-sent never — the idempotent findByInvoiceIdAndStage guard means
        // the next scan sees the durable row and does not re-flag), preserving escalated-once.
        if (outcome.pendingNotification() != null) {
          fireEscalationNotification(outcome.pendingNotification());
        }
      } catch (RuntimeException e) {
        // One bad candidate must not sink the tenant's scan.
        log.warn(
            "CollectionsScanService: failed processing invoice {}: {}",
            candidate.invoiceId(),
            e.getMessage());
      }
    }

    log.info(
        "CollectionsScanService: scan complete — proposed={}, skipped={}, escalated={},"
            + " superseded={}",
        proposed,
        skipped,
        escalated,
        superseded);
    return new ScanResult(proposed, skipped, escalated, superseded);
  }

  private CandidateOutcome processCandidate(Candidate candidate, CollectionsSettings policy) {
    int proposed = 0;
    int skipped = 0;
    int escalated = 0;
    int superseded = 0;
    // The escalation notification is NOT sent inside this per-candidate transaction (it is
    // REQUIRES_NEW and would commit durably before this candidate tx does — a later failure here
    // would then roll back the ESCALATION row while the notification had already gone out, breaking
    // escalated-once). Instead we record it as owed and let scanForTenant() fire it after commit.
    EscalationNotification pendingNotification = null;

    var invoiceId = candidate.invoiceId();
    var customerId = candidate.customerId();
    int days = candidate.daysOverdue();

    var invoice = invoiceRepository.findById(invoiceId).orElse(null);
    if (invoice == null) {
      return new CandidateOutcome(0, 0, 0, 0, null);
    }

    // Step 4 — escalation (independent of / in addition to the reminder ladder; no gate/AI/email).
    Integer escalate = policy.getEscalateDaysOverdue();
    if (escalate != null
        && days >= escalate
        && activityRepository
            .findByInvoiceIdAndStage(invoiceId, CollectionStage.ESCALATION)
            .isEmpty()) {
      var escalationRow =
          new CollectionActivity(
              invoiceId,
              customerId,
              CollectionStage.ESCALATION,
              CollectionActivityStatus.FLAGGED,
              days,
              "escalated");
      escalationRow = activityRepository.save(escalationRow);
      // Defer the notification until scanForTenant() confirms this candidate tx committed.
      pendingNotification = new EscalationNotification(invoiceId, invoice.getInvoiceNumber(), days);
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("collections.escalation.flagged")
              .entityType("collection_activity")
              .entityId(escalationRow.getId())
              .actorType("SYSTEM")
              .source("SCHEDULER")
              .details(
                  Map.of(
                      "invoice_id", invoiceId.toString(),
                      "invoice_number", nullSafe(invoice.getInvoiceNumber()),
                      "days_overdue", String.valueOf(days)))
              .build());
      escalated++;
    }

    // Step 3 — reminder ladder: highest threshold-crossed stage is the target for this scan.
    var target = highestCrossedStage(days, policy);
    if (target == null) {
      return new CandidateOutcome(proposed, skipped, escalated, superseded, pendingNotification);
    }

    // Record any lower threshold-crossed stages as SKIPPED(superseded_by_higher_stage) — ledger
    // completeness AND the "at most one active reminder per invoice" invariant (ADR-325). A lower
    // stage with no row yet is created SKIPPED; a lower stage still PROPOSED from an earlier scan
    // (a live, un-reviewed gate) is superseded in place — its PENDING gate is expired so the client
    // can never receive two overlapping reminders out of order. Any other existing status
    // (terminal,
    // or already retryable-skipped) is left untouched; it can't produce a second reminder because
    // the scan only (re-)proposes the target stage, never a lower one.
    for (var stage : REMINDER_STAGES) {
      if (stage.ordinal() >= target.ordinal()) {
        break;
      }
      Integer threshold = thresholdFor(stage, policy);
      if (threshold == null || days < threshold) {
        continue;
      }
      var lower = activityRepository.findByInvoiceIdAndStage(invoiceId, stage).orElse(null);
      if (lower == null) {
        activityRepository.save(
            new CollectionActivity(
                invoiceId,
                customerId,
                stage,
                CollectionActivityStatus.SKIPPED,
                days,
                REASON_SUPERSEDED));
        superseded++;
      } else if (lower.getStatus() == CollectionActivityStatus.PROPOSED) {
        // Expire the stale pending gate (defensive null-check: PROPOSED rows carry a gateId), then
        // transition to SKIPPED(superseded) — markSkipped retains the gateId per §2.2 so the last
        // draft stays traceable.
        if (lower.getGateId() != null) {
          gateService.expirePendingGate(lower.getGateId(), REASON_SUPERSEDED);
        }
        lower.markSkipped(REASON_SUPERSEDED);
        activityRepository.save(lower);
        superseded++;
      }
    }

    // Target stage: skip if already actioned in a non-retryable status; otherwise (absent or
    // retryable SKIPPED/SEND_FAILED) (re-)propose in place.
    var existing = activityRepository.findByInvoiceIdAndStage(invoiceId, target).orElse(null);
    if (existing != null && !isRetryable(existing.getStatus())) {
      return new CandidateOutcome(proposed, skipped, escalated, superseded, pendingNotification);
    }

    var activity =
        existing != null
            ? existing
            : new CollectionActivity(
                invoiceId, customerId, target, CollectionActivityStatus.SKIPPED, days, null);

    // Step 5 — recipient check.
    if (candidate.customerEmail() == null || candidate.customerEmail().isBlank()) {
      activity.markSkipped(REASON_NO_RECIPIENT);
      activityRepository.save(activity);
      skipped++;
      return new CandidateOutcome(proposed, skipped, escalated, superseded, pendingNotification);
    }

    // Step 6 — draft via the composer seam.
    var customer = customerRepository.findById(customerId).orElse(null);
    if (customer == null) {
      activity.markSkipped(REASON_NO_RECIPIENT);
      activityRepository.save(activity);
      skipped++;
      return new CandidateOutcome(proposed, skipped, escalated, superseded, pendingNotification);
    }

    try {
      var gate = reminderComposer.compose(activity, invoice, customer);
      if (gate.isPresent()) {
        activity.markProposed(gate.get().getId(), days);
        proposed++;
      } else {
        activity.markSkipped(REASON_DRAFT_UNAVAILABLE);
        skipped++;
      }
    } catch (ReminderComposer.AiUnavailableException e) {
      // AI pre-flight failed (provider unconfigured / no firm profile) — distinct retryable
      // disposition (§6.4): the jobs themselves never crash on AI unavailability.
      log.info(
          "CollectionsScanService: AI unavailable for invoice {} stage {}: {}",
          invoiceId,
          target,
          e.getMessage());
      activity.markSkipped(REASON_AI_UNAVAILABLE);
      skipped++;
    } catch (RuntimeException e) {
      // A bad draft must not sink the batch — record it retryable and continue.
      log.warn(
          "CollectionsScanService: composer failed for invoice {} stage {}: {}",
          invoiceId,
          target,
          e.getMessage());
      activity.markSkipped(REASON_DRAFT_FAILED);
      skipped++;
    }
    activityRepository.save(activity);

    return new CandidateOutcome(proposed, skipped, escalated, superseded, pendingNotification);
  }

  private List<Candidate> loadCandidates() {
    var query = entityManager.createNativeQuery(CANDIDATE_SQL, Tuple.class);
    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    return tuples.stream()
        .map(
            t ->
                new Candidate(
                    t.get("invoice_id", UUID.class),
                    t.get("customer_id", UUID.class),
                    t.get("customer_email", String.class),
                    ((Number) t.get("days_overdue")).intValue()))
        .toList();
  }

  private static CollectionStage highestCrossedStage(int days, CollectionsSettings policy) {
    CollectionStage target = null;
    for (var stage : REMINDER_STAGES) {
      Integer threshold = thresholdFor(stage, policy);
      if (threshold != null && days >= threshold) {
        target = stage;
      }
    }
    return target;
  }

  private static Integer thresholdFor(CollectionStage stage, CollectionsSettings policy) {
    return switch (stage) {
      case STAGE_1 -> policy.getStage1DaysOverdue();
      case STAGE_2 -> policy.getStage2DaysOverdue();
      case STAGE_3 -> policy.getStage3DaysOverdue();
      case ESCALATION -> policy.getEscalateDaysOverdue();
    };
  }

  private static boolean isRetryable(CollectionActivityStatus status) {
    return status == CollectionActivityStatus.SKIPPED
        || status == CollectionActivityStatus.SEND_FAILED;
  }

  private void fireEscalationNotification(EscalationNotification notification) {
    notificationService.notifyAdminsAndOwners(
        "COLLECTION_ESCALATED",
        "Invoice "
            + notification.invoiceNumber()
            + " is "
            + notification.daysOverdue()
            + " days overdue — flagged for a partner call",
        null,
        "INVOICE",
        notification.invoiceId());
  }

  private static String nullSafe(String value) {
    return value != null ? value : "";
  }

  private record Candidate(
      UUID invoiceId, UUID customerId, String customerEmail, int daysOverdue) {}

  /** An escalation notification owed once the candidate transaction has committed. */
  private record EscalationNotification(UUID invoiceId, String invoiceNumber, int daysOverdue) {}

  /**
   * Per-candidate result: the {@link ScanResult} count deltas plus an optional escalation
   * notification that {@link #scanForTenant()} must fire <em>after</em> the candidate transaction
   * commits (so the ESCALATION row is durable before admins/owners are told).
   */
  private record CandidateOutcome(
      int proposed,
      int skipped,
      int escalated,
      int superseded,
      EscalationNotification pendingNotification) {}
}
