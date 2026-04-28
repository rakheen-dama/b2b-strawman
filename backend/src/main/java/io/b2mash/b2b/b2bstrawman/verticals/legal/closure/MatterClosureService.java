package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.CloseMatterResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureLogResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReportResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.GateReportItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ReopenMatterResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ReopenRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event.MatterClosedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event.MatterReopenedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.StatementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional orchestrator for matter closure (Phase 67, Epic 489B, architecture §67.3).
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li>{@link #evaluate(UUID)} — runs every {@link ClosureGate} against the project and returns a
 *       {@link MatterClosureReport}.
 *   <li>{@link #close(UUID, ClosureRequest, UUID)} — re-evaluates gates, applies the {@code
 *       override} branch when requested, transitions the project to {@code CLOSED}, persists the
 *       closure log, publishes {@link MatterClosedEvent}, emits an audit event, and (separately)
 *       renders the closure letter best-effort. The close + log + audit + event publication run in
 *       ONE transaction ({@link #performClose}); letter rendering runs in its own {@code
 *       REQUIRES_NEW} transaction ({@link #generateClosureLetterSafely}) so its failures cannot
 *       roll back the close. Notification fanout is wired to the event's {@code AFTER_COMMIT}
 *       phase, so listeners never see closes that rolled back.
 *   <li>{@link #reopen(UUID, ReopenRequest, UUID)} — enforces the retention window, transitions
 *       {@code CLOSED → ACTIVE}, stamps reopen details onto the most recent closure-log row, and
 *       publishes {@link MatterReopenedEvent}.
 * </ol>
 *
 * <p>Module-gated through {@link VerticalModuleGuard#requireModule(String)} at every public method.
 * Override-capability ({@code OVERRIDE_MATTER_CLOSURE}) is enforced programmatically inside {@link
 * #close(UUID, ClosureRequest, UUID)} so that a {@code @RequiresCapability("CLOSE_MATTER")} caller
 * still cannot bypass gates without the destructive capability.
 */
@Service
public class MatterClosureService {

  private static final Logger log = LoggerFactory.getLogger(MatterClosureService.class);

  static final String MODULE_ID = "matter_closure";
  static final String OVERRIDE_CAPABILITY = "OVERRIDE_MATTER_CLOSURE";
  static final String CLOSURE_LETTER_SLUG = "matter-closure-letter";
  static final int MIN_OVERRIDE_JUSTIFICATION_LENGTH = 20;
  static final int MIN_REOPEN_NOTES_LENGTH = 10;

  private final List<ClosureGate> orderedGates;
  private final ProjectRepository projectRepository;
  private final MatterClosureLogRepository matterClosureLogRepository;
  private final VerticalModuleGuard moduleGuard;
  private final CapabilityAuthorizationService capabilityAuthorizationService;
  private final OrgSettingsService orgSettingsService;
  private final GeneratedDocumentService generatedDocumentService;
  private final DocumentService documentService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final StatementService statementService;
  private final RetentionPolicyRepository retentionPolicyRepository;

  /**
   * Self-reference used to invoke {@link #performClose}, {@link #generateClosureLetterSafely}, and
   * {@link #generateSoaSafely} through Spring's transactional proxy. A direct {@code
   * this.performClose(...)} call bypasses the proxy and would run without the declared transaction
   * boundary — breaking the fix for C2/C3.
   */
  private final MatterClosureService self;

  public MatterClosureService(
      List<ClosureGate> gates,
      ProjectRepository projectRepository,
      MatterClosureLogRepository matterClosureLogRepository,
      VerticalModuleGuard moduleGuard,
      CapabilityAuthorizationService capabilityAuthorizationService,
      OrgSettingsService orgSettingsService,
      GeneratedDocumentService generatedDocumentService,
      DocumentService documentService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      StatementService statementService,
      RetentionPolicyRepository retentionPolicyRepository,
      @Lazy MatterClosureService self) {
    this.orderedGates = gates.stream().sorted(Comparator.comparingInt(ClosureGate::order)).toList();
    this.projectRepository = projectRepository;
    this.matterClosureLogRepository = matterClosureLogRepository;
    this.moduleGuard = moduleGuard;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
    this.orgSettingsService = orgSettingsService;
    this.generatedDocumentService = generatedDocumentService;
    this.documentService = documentService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.statementService = statementService;
    this.retentionPolicyRepository = retentionPolicyRepository;
    this.self = self;
  }

  // ---------- evaluate ----------

  @Transactional(readOnly = true)
  public MatterClosureReport evaluate(UUID projectId) {
    moduleGuard.requireModule(MODULE_ID);
    var project = requireProject(projectId);
    List<GateReportItem> items = runGates(project);
    boolean allPassed = items.stream().allMatch(GateReportItem::passed);
    return new MatterClosureReport(projectId, Instant.now(), allPassed, items);
  }

  @Transactional(readOnly = true)
  public ClosureReportResponse evaluateForController(UUID projectId) {
    MatterClosureReport report = evaluate(projectId);
    return new ClosureReportResponse(
        report.projectId(), report.evaluatedAt(), report.allPassed(), report.gates());
  }

  // ---------- close ----------

  /**
   * Closes a matter. Deliberately NOT {@code @Transactional}: the orchestration runs in two
   * separate transactional units so a failure in closure-letter rendering can never roll back the
   * close itself (Phase 67, Epic 489B, C3).
   *
   * <ol>
   *   <li>Fail-fast override-capability check (H1) — before any work.
   *   <li>{@link #performClose} runs the close + log + audit + event in ONE transaction. On commit
   *       the {@link MatterClosedEvent} fires its {@code AFTER_COMMIT} handlers (notifications).
   *   <li>Closure-letter generation runs in its own {@code REQUIRES_NEW} transaction (best-effort);
   *       any failure is swallowed so the close stays committed and the letter can be regenerated
   *       from the UI later.
   * </ol>
   */
  public CloseMatterResponse close(UUID projectId, ClosureRequest req, UUID actingMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    // H1: fail fast on override without capability, BEFORE running gates or touching the project.
    if (req.override()) {
      capabilityAuthorizationService.requireCapability(OVERRIDE_CAPABILITY);
      validateOverrideJustification(req.overrideJustification());
    }

    // Main close transaction (project state, log row, audit, event publication).
    CloseInternalResult internal = self.performClose(projectId, req, actingMemberId);

    // Letter generation in a SEPARATE transaction. Failure MUST NOT affect the close.
    UUID closureLetterDocId = null;
    if (req.generateClosureLetter()) {
      closureLetterDocId =
          self.generateClosureLetterSafely(projectId, internal.closureLogId(), actingMemberId);
    }

    // GAP-L-93: Statement of Account generation in its OWN separate transaction. Failure MUST NOT
    // affect the close (mirrors the closure-letter REQUIRES_NEW guarantee). When suppressed (flag
    // false) or on best-effort failure, soaDocId stays null — the response still returns and the
    // operator can re-generate via the standalone "Generate Statement of Account" button.
    UUID soaDocId = null;
    if (req.isGenerateStatementOfAccount()) {
      // Wrap the proxy call in try/catch so a rollback in the inner REQUIRES_NEW transaction
      // (e.g., StatementService throws when the disbursements module is disabled) cannot
      // poison the outer (already-committed) close. Mirrors C3's best-effort guarantee.
      try {
        soaDocId = self.generateSoaSafely(projectId, actingMemberId);
      } catch (RuntimeException e) {
        log.warn(
            "SoA generation failed for project={}; closure proceeds without SoA", projectId, e);
      }
    }

    // GAP-L-96 (TODO 489C resolved): retention_policies MATTER row is now seeded inside
    // performClose() — see ensureMatterRetentionPolicy() invoked from the transactional core.
    // The row is tenant-wide (UNIQUE on record_type+trigger_event), idempotent across closures.
    return new CloseMatterResponse(
        projectId,
        internal.status(),
        internal.closedAt(),
        internal.closureLogId(),
        closureLetterDocId,
        soaDocId,
        internal.retentionEndsAt());
  }

  /**
   * Internal: the transactional core of {@link #close}. Runs gates, transitions the project,
   * persists the closure log, emits audit + event. Letter generation is NOT done here (C3).
   *
   * <p>Must be invoked via the Spring proxy (through {@code self}) so the {@code @Transactional}
   * boundary and the {@code AFTER_COMMIT} event dispatch work correctly.
   */
  @Transactional
  public CloseInternalResult performClose(UUID projectId, ClosureRequest req, UUID actingMemberId) {
    var project = requireProject(projectId);

    // 1. Re-evaluate gates inside the transaction.
    List<GateReportItem> gateItems = runGates(project);
    boolean allPassed = gateItems.stream().allMatch(GateReportItem::passed);

    // 2. Gate enforcement. (Override capability was checked in close() before we entered the TX.)
    if (!allPassed && !req.override()) {
      throw new ClosureGateFailedException(
          new MatterClosureReport(projectId, Instant.now(), false, gateItems));
    }

    // 3. Transition the project. The entity enforces ACTIVE/COMPLETED → CLOSED via
    //    requireTransition(); it throws InvalidStateException if the source status is wrong.
    project.closeMatter(actingMemberId);
    var savedProject = projectRepository.save(project);

    // 4. Persist the closure log with the serialised gate report + override details.
    Map<String, Object> gateReportJson = serialiseGateReport(gateItems);
    var closureLog =
        new MatterClosureLog(
            projectId,
            actingMemberId,
            savedProject.getClosedAt(),
            req.reason().name(),
            req.notes(),
            gateReportJson,
            req.override(),
            req.override() ? req.overrideJustification() : null);
    var savedLog = matterClosureLogRepository.save(closureLog);

    // 5. Audit.
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("matter_closure.closed")
            .entityType("project")
            .entityId(projectId)
            .details(
                Map.of(
                    "reason", req.reason().name(),
                    "override_used", req.override(),
                    "closure_log_id", savedLog.getId().toString()))
            .build());

    // 6. Publish domain event. Listeners run AFTER_COMMIT (see
    //    MatterClosureNotificationHandler) so subscribers never see closes that rolled back.
    eventPublisher.publishEvent(
        MatterClosedEvent.of(
            projectId, savedLog.getId(), req.reason().name(), req.override(), actingMemberId));

    // 7. ADR-249 / GAP-L-96: ensure a tenant-wide retention policy row exists for closed matters.
    //    Idempotent — subsequent closures reuse the same row (UNIQUE on
    //    (record_type, trigger_event)). Best-effort: any failure must not abort the close.
    int retentionYears =
        orgSettingsService.getOrCreateForCurrentTenant().getEffectiveLegalMatterRetentionYears();
    ensureMatterRetentionPolicy(retentionYears);

    // 8. Build intermediate result (letter id is filled in by the outer orchestrator).
    LocalDate retentionEndsAt =
        savedProject.getClosedAt().atZone(ZoneOffset.UTC).toLocalDate().plusYears(retentionYears);

    return new CloseInternalResult(
        savedProject.getStatus().name(),
        savedProject.getClosedAt(),
        savedLog.getId(),
        retentionEndsAt);
  }

  /**
   * GAP-L-96 / ADR-249: ensure a tenant-wide {@code retention_policies} row exists for {@code
   * record_type='MATTER'} on first matter closure for this tenant. The row is forward-compatibility
   * for the planned retention sweeper extension; subsequent closures reuse the same row (UNIQUE on
   * {@code (record_type, trigger_event)}).
   *
   * <p>Uses an atomic {@code INSERT ... ON CONFLICT DO NOTHING} so concurrent closes on the same
   * tenant cannot race past an existence check and conflict at outer-transaction flush. The single
   * SQL statement is race-proof at the DB level — no pre-check, no two-phase pattern, no commit-
   * time exception that could abort {@code performClose}.
   *
   * <p>{@code retentionDays = retentionYears * 365}. Trigger event {@code MATTER_CLOSED}, action
   * {@code ARCHIVE}. Note: MATTER is NOT in the financial-record-types list, so the JURISDICTION
   * minimum-months check that lives inside {@code RetentionPolicyService.create} does not apply —
   * and we intentionally bypass that service to keep the insert path atomic at the repository
   * layer.
   */
  private void ensureMatterRetentionPolicy(int retentionYears) {
    final int retentionDays;
    try {
      retentionDays = Math.multiplyExact(retentionYears, 365);
    } catch (ArithmeticException overflow) {
      // Defense-in-depth: OrgSettings now caps retentionYears at 100 (@Max), so this branch is
      // unreachable in practice. Guarded anyway so a future relaxation of the cap or a direct
      // DB write that bypasses bean validation cannot silently produce a negative retention_days
      // value via Integer overflow.
      log.warn(
          "Skipping MATTER retention-policy seed: retentionYears={} overflows when multiplied by"
              + " 365",
          retentionYears,
          overflow);
      return;
    }
    try {
      retentionPolicyRepository.insertIfAbsent("MATTER", retentionDays, "MATTER_CLOSED", "ARCHIVE");
    } catch (RuntimeException e) {
      // The atomic upsert already absorbs the race; this catch only handles unrelated runtime
      // failures (e.g. driver-level connection issues) and ensures the close proceeds.
      log.warn("Failed to seed MATTER retention policy on closure; close proceeds", e);
    }
  }

  /**
   * Generates the closure letter in its own transaction and stamps the resulting document id onto
   * the closure-log row. Any failure is swallowed — the close itself has already committed (C3).
   *
   * <p>{@code REQUIRES_NEW} guarantees a fresh transaction even if a caller's test harness still
   * has one open, so a rollback here cannot poison the outer close transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID generateClosureLetterSafely(UUID projectId, UUID closureLogId, UUID actingMemberId) {
    try {
      var result =
          generatedDocumentService.generateForProject(
              projectId, CLOSURE_LETTER_SLUG, actingMemberId);
      if (result == null || result.generatedDocument() == null) {
        return null;
      }
      UUID letterDocId = result.generatedDocument().getId();
      UUID linkedDocumentId = result.generatedDocument().getDocumentId();
      matterClosureLogRepository
          .findById(closureLogId)
          .ifPresent(
              logRow -> {
                logRow.setClosureLetterDocumentId(letterDocId);
                matterClosureLogRepository.save(logRow);
              });

      // GAP-L-74 part B / GAP-L-74-followup: closure letter is by definition client-facing (per
      // scenario step 61.8). Flip the linked Document from INTERNAL (default) to PORTAL so it
      // appears on the portal Documents tab via PortalQueryService.listProjectDocuments. PORTAL
      // (vs SHARED) signals "system auto-shared as part of a closure pack" — distinct from a firm
      // user explicitly clicking "share with client". Best-effort: any failure here must not roll
      // back the (already committed) close — we are inside REQUIRES_NEW so a rollback only affects
      // this letter-step transaction, but we still try/catch defensively so a visibility-flip
      // failure leaves the letter generated rather than throwing.
      if (linkedDocumentId != null) {
        try {
          documentService.markSystemAutoShared(linkedDocumentId);
        } catch (RuntimeException visibilityEx) {
          log.warn(
              "Failed to flip closure-letter document visibility to PORTAL: project={}, doc={}",
              projectId,
              linkedDocumentId,
              visibilityEx);
        }
      }

      return letterDocId;
    } catch (RuntimeException e) {
      log.warn(
          "Closure letter generation failed for project={}; closure proceeds without letter",
          projectId,
          e);
      return null;
    }
  }

  /**
   * GAP-L-93: generates the Statement of Account in its own transaction so that a render failure
   * cannot roll back the (already-committed) close. Default period spans the matter's open date
   * (project {@code createdAt}) → today (UTC). If a different window is needed, the operator can
   * re-generate via the standalone "Generate Statement of Account" button on the matter header.
   *
   * <p>Deliberately does NOT swallow exceptions internally — Spring's REQUIRES_NEW proxy needs a
   * clean throw path to roll back the inner transaction without marking it
   * rollback-only-but-not-rolled-back. The caller ({@link #close}) wraps the proxy invocation in
   * try/catch and treats any failure as a null SoA id, preserving the close commit (C3).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID generateSoaSafely(UUID projectId, UUID actingMemberId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate openDate =
        projectRepository
            .findById(projectId)
            .map(
                p ->
                    p.getCreatedAt() != null
                        ? p.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
                        : today.minusYears(1))
            .orElse(today.minusYears(1));
    var resp =
        statementService.generate(
            projectId, new GenerateStatementRequest(openDate, today, null), actingMemberId);
    return resp.id();
  }

  /** Intermediate result from {@link #performClose} feeding {@link #close}'s final response. */
  public record CloseInternalResult(
      String status, Instant closedAt, UUID closureLogId, LocalDate retentionEndsAt) {}

  // ---------- reopen ----------

  @Transactional
  public ReopenMatterResponse reopen(UUID projectId, ReopenRequest req, UUID actingMemberId) {
    moduleGuard.requireModule(MODULE_ID);

    validateReopenNotes(req.notes());

    var project = requireProject(projectId);

    // Retention window check. The canonical retention anchor is the project's
    // retentionClockStartedAt (stamped on first close, preserved across reopens). Use calendar-year
    // arithmetic via LocalDate + plusYears so leap years don't bite.
    Instant anchor = project.getRetentionClockStartedAt();
    if (anchor == null) {
      // Fall back to closedAt — at this point the project must be CLOSED, so closedAt is set.
      anchor = project.getClosedAt();
    }
    if (anchor == null) {
      throw new InvalidStateException(
          "Cannot reopen matter", "Project has no retention anchor — it was never closed.");
    }
    int retentionYears =
        orgSettingsService.getOrCreateForCurrentTenant().getEffectiveLegalMatterRetentionYears();
    LocalDate retentionEndsOn =
        anchor.atZone(ZoneOffset.UTC).toLocalDate().plusYears(retentionYears);
    if (LocalDate.now(ZoneOffset.UTC).isAfter(retentionEndsOn)) {
      throw new RetentionElapsedException(projectId, retentionEndsOn);
    }

    // Transition CLOSED → ACTIVE. The entity throws InvalidStateException if status is wrong.
    project.reopenMatter();
    var savedProject = projectRepository.save(project);

    // Stamp reopen details onto the latest closure log row.
    var latestLog =
        matterClosureLogRepository
            .findTopByProjectIdOrderByClosedAtDesc(projectId)
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "Closure log missing", "No closure log row found for matter " + projectId));
    Instant reopenedAt = Instant.now();
    latestLog.recordReopen(reopenedAt, actingMemberId, req.notes());
    var savedLog = matterClosureLogRepository.save(latestLog);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("matter_closure.reopened")
            .entityType("project")
            .entityId(projectId)
            .details(
                Map.of(
                    "closure_log_id", savedLog.getId().toString(),
                    "reopened_by", actingMemberId.toString()))
            .build());

    eventPublisher.publishEvent(MatterReopenedEvent.of(projectId, actingMemberId, req.notes()));

    return new ReopenMatterResponse(
        projectId, savedProject.getStatus().name(), reopenedAt, savedLog.getId());
  }

  // ---------- log query ----------

  @Transactional(readOnly = true)
  public List<ClosureLogResponse> getLog(UUID projectId) {
    moduleGuard.requireModule(MODULE_ID);
    // Ensure the project exists in this tenant so we don't silently return [] for an unrelated id.
    requireProject(projectId);
    return matterClosureLogRepository.findByProjectIdOrderByClosedAtDesc(projectId).stream()
        .map(ClosureLogResponse::from)
        .toList();
  }

  // ---------- helpers ----------

  private Project requireProject(UUID projectId) {
    return projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
  }

  private List<GateReportItem> runGates(Project project) {
    List<GateReportItem> items = new ArrayList<>(orderedGates.size());
    for (ClosureGate gate : orderedGates) {
      GateResult result = gate.evaluate(project);
      items.add(
          new GateReportItem(
              gate.order(),
              result.code(),
              result.passed(),
              result.message(),
              result.detail() != null ? result.detail() : Map.of()));
    }
    return items;
  }

  private Map<String, Object> serialiseGateReport(List<GateReportItem> items) {
    Map<String, Object> root = new LinkedHashMap<>();
    List<Map<String, Object>> serialisedGates = new ArrayList<>(items.size());
    for (GateReportItem item : items) {
      Map<String, Object> g = new LinkedHashMap<>();
      g.put("order", item.order());
      g.put("code", item.code());
      g.put("passed", item.passed());
      g.put("message", item.message());
      g.put("detail", item.detail() != null ? new HashMap<>(item.detail()) : Map.of());
      serialisedGates.add(g);
    }
    root.put("gates", serialisedGates);
    root.put("allPassed", items.stream().allMatch(GateReportItem::passed));
    root.put("evaluatedAt", Instant.now().toString());
    return root;
  }

  private void validateOverrideJustification(String justification) {
    if (justification == null
        || justification.trim().length() < MIN_OVERRIDE_JUSTIFICATION_LENGTH) {
      throw new InvalidStateException(
          "Override justification required",
          "overrideJustification must be at least "
              + MIN_OVERRIDE_JUSTIFICATION_LENGTH
              + " non-whitespace characters when override=true.");
    }
  }

  private void validateReopenNotes(String notes) {
    if (notes == null || notes.trim().length() < MIN_REOPEN_NOTES_LENGTH) {
      throw new InvalidStateException(
          "Reopen notes required",
          "notes must be at least "
              + MIN_REOPEN_NOTES_LENGTH
              + " non-whitespace characters to reopen a matter.");
    }
  }
}
