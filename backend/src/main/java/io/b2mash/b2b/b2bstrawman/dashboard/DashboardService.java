package io.b2mash.b2b.b2bstrawman.dashboard;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.CrossProjectActivityProjection;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.CrossMatterActivityItem;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.FicaOutstandingResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.FicaOutstandingResponse.FicaOutstandingClient;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiValues;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterBreakdownEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterHealth;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterHealthMetrics;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MemberHoursEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PersonalDashboard;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrendPoint;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrustPositionResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrustPositionResponse.TrustAccountRef;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.UnbilledWipResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.UnbilledWipResponse.FeeEarnerWip;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.UpcomingDeadline;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.UtilizationSummary;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.FicaStatusProjector;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestService;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.MatterWithRole;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.timeentry.MemberTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.MyWorkProjectTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.UnbilledWipByMemberProjection;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for computing project and org-level dashboard data including health scoring, task
 * summaries, and company KPIs. Uses Caffeine caches: project-scoped (1-min TTL) and org-scoped
 * (3-min TTL).
 */
@Service
public class DashboardService {

  /** Lower bound for the activity-feed {@code limit} (clamped). */
  static final int MIN_ACTIVITY_LIMIT = 1;

  /** Upper bound for the activity-feed {@code limit} (clamped). */
  static final int MAX_ACTIVITY_LIMIT = 50;

  /** Bounds for the fica-outstanding {@code limit} (clamped). */
  static final int MIN_FICA_OUTSTANDING_LIMIT = 1;

  static final int MAX_FICA_OUTSTANDING_LIMIT = 50;

  private static final String DISBURSEMENTS_MODULE = "disbursements";
  private static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";

  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final AuditEventRepository auditEventRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final ProjectRepository projectRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;
  private final ProjectAccessService projectAccessService;
  private final OrgSettingsService orgSettingsService;
  private final VerticalModuleGuard moduleGuard;
  private final InformationRequestService informationRequestService;
  private final TrustAccountService trustAccountService;
  private final TrustTransactionService trustTransactionService;
  private final ClientLedgerService clientLedgerService;
  private final TrustReconciliationRepository trustReconciliationRepository;
  private final DisbursementRepository disbursementRepository;
  private final Clock clock;

  private final Cache<String, Object> projectCache =
      Caffeine.newBuilder().maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(1)).build();

  private final Cache<String, Object> orgCache =
      Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(3)).build();

  public DashboardService(
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      AuditEventRepository auditEventRepository,
      ProjectBudgetRepository projectBudgetRepository,
      ProjectRepository projectRepository,
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository,
      ProjectAccessService projectAccessService,
      OrgSettingsService orgSettingsService,
      VerticalModuleGuard moduleGuard,
      InformationRequestService informationRequestService,
      TrustAccountService trustAccountService,
      TrustTransactionService trustTransactionService,
      ClientLedgerService clientLedgerService,
      TrustReconciliationRepository trustReconciliationRepository,
      DisbursementRepository disbursementRepository,
      Clock clock) {
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.auditEventRepository = auditEventRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.projectRepository = projectRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
    this.projectAccessService = projectAccessService;
    this.orgSettingsService = orgSettingsService;
    this.moduleGuard = moduleGuard;
    this.informationRequestService = informationRequestService;
    this.trustAccountService = trustAccountService;
    this.trustTransactionService = trustTransactionService;
    this.clientLedgerService = clientLedgerService;
    this.trustReconciliationRepository = trustReconciliationRepository;
    this.disbursementRepository = disbursementRepository;
    this.clock = clock;
  }

  // --- Project-scoped endpoints (Epic 75B) ---

  /**
   * Computes the health status for a project, gathering task counts, budget data, and last activity
   * information. Results are cached for 1 minute per tenant+project.
   *
   * @param projectId the project to compute health for
   * @param tenantId the tenant schema for cache key isolation
   * @param actor the authenticated actor; must have view access to the project
   * @return the project health detail with status, reasons, and metrics
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public MatterHealthDetail getProjectHealth(UUID projectId, String tenantId, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);

    String key = tenantId + ":project:" + projectId + ":health";
    MatterHealthDetail cached = (MatterHealthDetail) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    MatterHealthDetail result = computeProjectHealth(projectId);
    projectCache.put(key, result);
    return result;
  }

  /**
   * Computes task summary counts for a project, broken down by status. Results are cached for 1
   * minute per tenant+project.
   *
   * @param projectId the project to summarize
   * @param tenantId the tenant schema for cache key isolation
   * @param actor the authenticated actor; must have view access to the project
   * @return the task summary with counts by status and overdue count
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public TaskSummary getTaskSummary(UUID projectId, String tenantId, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);

    String key = tenantId + ":project:" + projectId + ":task-summary";
    TaskSummary cached = (TaskSummary) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    TaskSummary result = computeTaskSummary(projectId);
    projectCache.put(key, result);
    return result;
  }

  // --- Org-level endpoints (Epic 76A) ---

  /**
   * Returns org-level KPIs with trend and previous-period comparison. Financial fields
   * (billablePercent, averageMarginPercent) are redacted for non-admin/non-owner members. Cache
   * stores the full (non-redacted) version; redaction happens after cache retrieval.
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param orgRole the caller's org role for financial field redaction
   * @param from start date of the KPI period
   * @param to end date of the KPI period
   * @return KPI response with trend and previous period data
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public KpiResponse getCompanyKpis(String tenantId, String orgRole, LocalDate from, LocalDate to) {
    requireValidDateRange(from, to);

    String key = tenantId + ":kpis:" + from + "_" + to;

    // Always cache the FULL version
    KpiResponse full = (KpiResponse) orgCache.getIfPresent(key);
    if (full == null) {
      KpiValues currentValues = computeKpiValues(from, to);
      List<TrendPoint> trend = computeTrend(from, to);
      KpiValues previousPeriod = computePreviousPeriod(from, to);

      full =
          new KpiResponse(
              currentValues.activeMatterCount(),
              currentValues.totalHoursLogged(),
              currentValues.billablePercent(),
              currentValues.overdueTaskCount(),
              currentValues.averageMarginPercent(),
              trend,
              previousPeriod);

      orgCache.put(key, full);
    }

    // Single redaction point
    return isAdminOrOwner(orgRole) ? full : full.withFinancialsRedacted();
  }

  /**
   * Returns the project health list for accessible projects, sorted by severity (CRITICAL first),
   * then by completion percent ascending (least complete first).
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param actor the authenticated actor for access filtering and visibility
   * @return sorted list of project health summaries
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<MatterHealth> getProjectHealthList(String tenantId, ActorContext actor) {
    String key = tenantId + ":project-health:" + actor.orgRole();
    List<MatterHealth> cached = (List<MatterHealth>) orgCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    List<MatterHealth> result = computeProjectHealthList(actor);
    orgCache.put(key, result);
    return result;
  }

  // --- Org-level endpoints (Epic 76B) ---

  /**
   * Returns recent cross-project activity events. Admin/owner sees all events; regular members see
   * only events from projects they belong to. Results are cached in the org cache.
   *
   * @param tenantId the tenant schema for cache key isolation
   * @param actor the authenticated actor for access filtering and visibility
   * @param limit requested number of events; clamped to [{@value #MIN_ACTIVITY_LIMIT}, {@value
   *     #MAX_ACTIVITY_LIMIT}]
   * @return list of activity items with actor and project names
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<CrossMatterActivityItem> getCrossProjectActivity(
      String tenantId, ActorContext actor, int limit) {
    int clampedLimit = Math.max(MIN_ACTIVITY_LIMIT, Math.min(MAX_ACTIVITY_LIMIT, limit));

    String cacheKey =
        tenantId + ":activity:" + actor.memberId() + ":" + actor.orgRole() + ":" + clampedLimit;

    List<CrossMatterActivityItem> cached =
        (List<CrossMatterActivityItem>) orgCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    List<CrossMatterActivityItem> result = computeCrossProjectActivity(actor, clampedLimit);
    orgCache.put(cacheKey, result);
    return result;
  }

  // --- Project member-hours endpoint (Epic 79A) ---

  /**
   * Returns per-member hour breakdown for a project within a date range. Reuses the existing
   * projectTimeSummaryByMember query and maps results to MemberHoursEntry DTOs. Results are cached
   * in the project cache with 1-minute TTL.
   *
   * @param projectId the project to query
   * @param tenantId the tenant schema for cache key isolation
   * @param from start date of the period (must not be after {@code to})
   * @param to end date of the period
   * @param actor the authenticated actor; must have view access to the project
   * @return list of member hours entries sorted by totalHours descending
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<MemberHoursEntry> getProjectMemberHours(
      UUID projectId, String tenantId, LocalDate from, LocalDate to, ActorContext actor) {
    requireValidDateRange(from, to);
    projectAccessService.requireViewAccess(projectId, actor);

    String key = tenantId + ":project:" + projectId + ":member-hours:" + from + "_" + to;
    List<MemberHoursEntry> cached = (List<MemberHoursEntry>) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    List<MemberHoursEntry> result = computeProjectMemberHours(projectId, from, to);
    projectCache.put(key, result);
    return result;
  }

  // --- Personal dashboard endpoint (Epic 79A) ---

  /**
   * Returns a personal dashboard for the authenticated member with utilization, project breakdown,
   * overdue task count, upcoming deadlines, and trend data. Self-scoped: the underlying queries
   * filter by member_id, which serves as its own authorization (ADR-023). Results are cached in the
   * project cache with 1-minute TTL.
   *
   * @param memberId the authenticated member's ID
   * @param tenantId the tenant schema for cache key isolation
   * @param from start date of the period
   * @param to end date of the period
   * @return personal dashboard data
   */
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public PersonalDashboard getPersonalDashboard(
      UUID memberId, String tenantId, LocalDate from, LocalDate to) {
    requireValidDateRange(from, to);

    String key = tenantId + ":personal:" + memberId + ":" + from + "_" + to;
    PersonalDashboard cached = (PersonalDashboard) projectCache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    PersonalDashboard result = computePersonalDashboard(memberId, from, to);
    projectCache.put(key, result);
    return result;
  }

  // --- Legal-native home reads (Phase 87, Epic 614A — arch §12.3.3) ---

  /**
   * Unbilled WIP by fee earner. Callers with {@code FINANCIAL_VISIBILITY} (or admin/owner) see
   * every fee earner with values; everyone else gets {@link
   * UnbilledWipResponse#withFinancialsRedacted} — their own row, hours only. Value counts only time
   * recorded in the org default currency; the disbursement total is null when the {@code
   * disbursements} module is off. Not cached.
   */
  @Transactional(readOnly = true)
  public UnbilledWipResponse getUnbilledWip(ActorContext actor) {
    String currency = orgSettingsService.getDefaultCurrency();
    List<FeeEarnerWip> rows =
        foldUnbilledWipByMember(timeEntryRepository.findUnbilledWipByMember(), currency);
    int totalMinutes = rows.stream().mapToInt(FeeEarnerWip::minutes).sum();
    BigDecimal totalValue =
        rows.stream()
            .map(FeeEarnerWip::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal disbursementTotal =
        moduleGuard.isModuleEnabled(DISBURSEMENTS_MODULE)
            ? disbursementRepository.sumApprovedUnbilledAmount().setScale(2, RoundingMode.HALF_UP)
            : null;
    var full =
        new UnbilledWipResponse(
            LocalDate.now(clock),
            currency,
            totalMinutes,
            totalValue,
            disbursementTotal,
            rows,
            false);
    boolean seesFinancials =
        isAdminOrOwner(actor.orgRole()) || RequestScopes.hasCapability("FINANCIAL_VISIBILITY");
    return seesFinancials ? full : full.withFinancialsRedacted(actor.memberId());
  }

  /**
   * Non-archived ONBOARDING/ACTIVE clients whose FICA status (batched through {@link
   * FicaStatusProjector}) is not DONE, longest-outstanding first. {@code daysOutstanding} counts
   * from the client's last lifecycle transition (or creation). {@code outstandingCount} counts all
   * outstanding clients; {@code clients} holds the top {@code limit} (clamped to 1–50).
   */
  @Transactional(readOnly = true)
  public FicaOutstandingResponse getFicaOutstanding(int limit) {
    int clamped = Math.max(MIN_FICA_OUTSTANDING_LIMIT, Math.min(MAX_FICA_OUTSTANDING_LIMIT, limit));
    List<Customer> clients =
        customerRepository.findByStatusAndLifecycleStatusIn(
            "ACTIVE", List.of(LifecycleStatus.ONBOARDING, LifecycleStatus.ACTIVE));
    var statuses =
        informationRequestService.getFicaStatuses(clients.stream().map(Customer::getId).toList());
    LocalDate today = LocalDate.now(clock);
    List<FicaOutstandingClient> outstanding =
        clients.stream()
            .filter(c -> !FicaStatusProjector.DONE.equals(statuses.get(c.getId()).status()))
            .map(
                c -> {
                  var fica = statuses.get(c.getId());
                  Instant openedAt =
                      c.getLifecycleStatusChangedAt() != null
                          ? c.getLifecycleStatusChangedAt()
                          : c.getCreatedAt();
                  int days =
                      (int)
                          ChronoUnit.DAYS.between(
                              LocalDate.ofInstant(openedAt, clock.getZone()), today);
                  return new FicaOutstandingClient(
                      c.getId(),
                      c.getName(),
                      c.getLifecycleStatus().name(),
                      fica.status(),
                      fica.requestId(),
                      openedAt,
                      days);
                })
            .sorted(
                Comparator.comparingInt(FicaOutstandingClient::daysOutstanding)
                    .reversed()
                    .thenComparing(FicaOutstandingClient::clientName))
            .toList();
    return new FicaOutstandingResponse(
        outstanding.size(), clients.size(), outstanding.stream().limit(clamped).toList(), clamped);
  }

  /**
   * Trust position for {@code accountId}, or the primary GENERAL account (falling back to the first
   * active account). Composes the services behind {@code /cashbook-balance}, {@code /total-balance}
   * and {@code /pending-approvals}, so module-guard and 404 semantics match the parts; only the
   * latest reconciliation is read from its repository. 404 when no trust account exists.
   */
  @Transactional(readOnly = true)
  public TrustPositionResponse getTrustPosition(UUID accountId) {
    moduleGuard.requireModule(TRUST_ACCOUNTING_MODULE);
    var accounts = trustAccountService.listTrustAccounts();
    var account =
        accountId != null
            ? trustAccountService.getTrustAccount(accountId)
            : trustAccountService
                .getPrimaryAccount()
                .or(() -> accounts.stream().findFirst())
                .orElseThrow(
                    () ->
                        ResourceNotFoundException.withDetail(
                            "No trust account", "No trust account has been configured"));
    BigDecimal cashbook = trustTransactionService.getCashbookBalance(account.id()).balance();
    BigDecimal ledger = clientLedgerService.getTotalTrustBalance(account.id()).balance();
    int pendingApprovals = trustTransactionService.getPendingApprovals(account.id()).size();
    Optional<TrustReconciliation> latest =
        trustReconciliationRepository.findFirstByTrustAccountIdOrderByPeriodEndDescCreatedAtDesc(
            account.id());
    LocalDate today = LocalDate.now(clock);
    return new TrustPositionResponse(
        account.id(),
        account.accountName(),
        account.bankName(),
        cashbook,
        ledger,
        cashbook.subtract(ledger),
        latest.map(TrustReconciliation::getPeriodEnd).orElse(null),
        latest.map(r -> r.getStatus().name()).orElse(null),
        latest.map(TrustReconciliation::isBalanced).orElse(null),
        latest.map(r -> (int) ChronoUnit.DAYS.between(r.getPeriodEnd(), today)).orElse(null),
        pendingApprovals,
        accounts.stream()
            .map(a -> new TrustAccountRef(a.id(), a.accountName(), a.isPrimary()))
            .toList());
  }

  /**
   * Folds the per-(member, currency) aggregate rows into one row per fee earner: minutes and oldest
   * entry date over every row; value only from rows in {@code currency} (rows without a rate
   * snapshot carry value 0). Sorted by value desc, then minutes desc, then name.
   */
  private static List<FeeEarnerWip> foldUnbilledWipByMember(
      List<UnbilledWipByMemberProjection> rows, String currency) {
    Map<UUID, FeeEarnerWip> byMember = new LinkedHashMap<>();
    for (var row : rows) {
      BigDecimal value =
          currency.equals(row.getCurrency()) && row.getValue() != null
              ? row.getValue()
              : BigDecimal.ZERO;
      var current =
          new FeeEarnerWip(
              row.getMemberId(),
              row.getMemberName(),
              row.getMinutes() != null ? row.getMinutes().intValue() : 0,
              value,
              row.getOldestEntryDate());
      byMember.merge(
          row.getMemberId(),
          current,
          (a, b) ->
              new FeeEarnerWip(
                  a.memberId(),
                  a.memberName(),
                  a.minutes() + b.minutes(),
                  a.value().add(b.value()),
                  earliest(a.oldestEntryDate(), b.oldestEntryDate())));
    }
    return byMember.values().stream()
        .map(
            r ->
                new FeeEarnerWip(
                    r.memberId(),
                    r.memberName(),
                    r.minutes(),
                    r.value().setScale(2, RoundingMode.HALF_UP),
                    r.oldestEntryDate()))
        .sorted(
            Comparator.comparing(FeeEarnerWip::value)
                .reversed()
                .thenComparing(Comparator.comparingInt(FeeEarnerWip::minutes).reversed())
                .thenComparing(FeeEarnerWip::memberName))
        .toList();
  }

  private static LocalDate earliest(LocalDate a, LocalDate b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.isBefore(b) ? a : b;
  }

  // --- Private: project-scoped computation ---

  private MatterHealthDetail computeProjectHealth(UUID projectId) {
    LocalDate today = LocalDate.now();

    // Task counts
    long totalTasks = taskRepository.countByProjectId(projectId);
    long doneTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
    long inProgressTasks =
        taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS);
    long todoTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.OPEN);
    long overdueTasks = taskRepository.countOverdueByProjectId(projectId, today);

    double completionPercent = totalTasks > 0 ? (double) doneTasks / totalTasks * 100 : 0;

    // Budget data
    Double budgetConsumedPercent = null;
    int alertThresholdPct = ProjectHealthCalculator.DEFAULT_BUDGET_ALERT_THRESHOLD;
    Optional<ProjectBudget> budgetOpt = projectBudgetRepository.findByProjectId(projectId);
    if (budgetOpt.isPresent()) {
      ProjectBudget budget = budgetOpt.get();
      alertThresholdPct = budget.getAlertThresholdPct();
      BigDecimal budgetHours = budget.getBudgetHours();
      if (budgetHours != null && budgetHours.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal consumed = timeEntryRepository.budgetHoursConsumed(projectId).getHoursConsumed();
        budgetConsumedPercent =
            consumed
                .multiply(BigDecimal.valueOf(100))
                .divide(budgetHours, 2, RoundingMode.HALF_UP)
                .doubleValue();
      }
    }

    // Last activity
    int daysSinceLastActivity = computeDaysSinceLastActivity(projectId);

    // Hours this period (all-time for now)
    double hoursThisPeriod =
        timeEntryRepository.projectTimeSummary(projectId, null, null).getTotalMinutes() / 60.0;

    // Build health input and calculate
    var input =
        new ProjectHealthInput(
            (int) totalTasks,
            (int) doneTasks,
            (int) overdueTasks,
            budgetConsumedPercent,
            alertThresholdPct,
            completionPercent,
            daysSinceLastActivity);

    ProjectHealthResult healthResult = ProjectHealthCalculator.calculate(input);

    var metrics =
        new MatterHealthMetrics(
            (int) doneTasks,
            (int) inProgressTasks,
            (int) todoTasks,
            (int) overdueTasks,
            (int) totalTasks,
            completionPercent,
            budgetConsumedPercent,
            hoursThisPeriod,
            daysSinceLastActivity);

    return new MatterHealthDetail(healthResult.status(), healthResult.reasons(), metrics);
  }

  private TaskSummary computeTaskSummary(UUID projectId) {
    var rows = taskRepository.getTaskSummaryByProjectId(projectId, LocalDate.now());
    if (rows.isEmpty()) {
      return new TaskSummary(0, 0, 0, 0, 0, 0);
    }
    Object[] row = rows.getFirst();
    return new TaskSummary(
        ((Number) row[0]).intValue(),
        ((Number) row[1]).intValue(),
        ((Number) row[2]).intValue(),
        ((Number) row[3]).intValue(),
        ((Number) row[4]).intValue(),
        ((Number) row[5]).intValue());
  }

  // --- Private: Epic 79A computation ---

  private List<MemberHoursEntry> computeProjectMemberHours(
      UUID projectId, LocalDate from, LocalDate to) {
    List<MemberTimeSummaryProjection> rows =
        timeEntryRepository.projectTimeSummaryByMember(projectId, from, to);
    return rows.stream()
        .map(
            r ->
                new MemberHoursEntry(
                    r.getMemberId(),
                    r.getMemberName(),
                    r.getTotalMinutes() / 60.0,
                    r.getBillableMinutes() / 60.0))
        .toList();
  }

  private PersonalDashboard computePersonalDashboard(UUID memberId, LocalDate from, LocalDate to) {
    // 1. Utilization
    var timeSummary = timeEntryRepository.memberTimeSummary(memberId, from, to);
    double totalHours = timeSummary.getTotalMinutes() / 60.0;
    double billableHours = timeSummary.getBillableMinutes() / 60.0;
    Double billablePercent =
        timeSummary.getTotalMinutes() > 0
            ? (double) timeSummary.getBillableMinutes() / timeSummary.getTotalMinutes() * 100
            : null;
    var utilization = new UtilizationSummary(totalHours, billableHours, billablePercent);

    // 2. Project breakdown (top 5 + "Other")
    List<MyWorkProjectTimeSummaryProjection> byProject =
        timeEntryRepository.memberTimeSummaryByProject(memberId, from, to);
    long totalMinutesAll =
        byProject.stream().mapToLong(MyWorkProjectTimeSummaryProjection::getTotalMinutes).sum();
    List<MatterBreakdownEntry> projectBreakdown = new ArrayList<>();
    long otherMinutes = 0;
    for (int i = 0; i < byProject.size(); i++) {
      var row = byProject.get(i);
      if (i < 5) {
        double hours = row.getTotalMinutes() / 60.0;
        double percent =
            totalMinutesAll > 0 ? (double) row.getTotalMinutes() / totalMinutesAll * 100 : 0;
        projectBreakdown.add(
            new MatterBreakdownEntry(row.getProjectId(), row.getProjectName(), hours, percent));
      } else {
        otherMinutes += row.getTotalMinutes();
      }
    }
    if (otherMinutes > 0) {
      double otherHours = otherMinutes / 60.0;
      double otherPercent = totalMinutesAll > 0 ? (double) otherMinutes / totalMinutesAll * 100 : 0;
      projectBreakdown.add(new MatterBreakdownEntry(null, "Other", otherHours, otherPercent));
    }

    // 3. Overdue task count
    LocalDate today = LocalDate.now();
    int overdueTaskCount = (int) taskRepository.countOverdueByAssignee(memberId, today);

    // 4. Upcoming deadlines (next 5)
    List<Task> upcomingTasks =
        taskRepository.findUpcomingByAssignee(memberId, today).stream().limit(5).toList();
    // Batch-load project names
    List<UUID> projectIds = upcomingTasks.stream().map(Task::getProjectId).distinct().toList();
    Map<UUID, String> projectNameMap = new HashMap<>();
    if (!projectIds.isEmpty()) {
      projectRepository
          .findAllById(projectIds)
          .forEach(p -> projectNameMap.put(p.getId(), p.getName()));
    }
    List<UpcomingDeadline> upcomingDeadlines =
        upcomingTasks.stream()
            .map(
                t ->
                    new UpcomingDeadline(
                        t.getId(),
                        t.getTitle(),
                        projectNameMap.getOrDefault(t.getProjectId(), "Unknown"),
                        t.getDueDate()))
            .toList();

    // 5. Trend (member-scoped)
    List<TrendPoint> trend = computeMemberTrend(memberId, from, to);

    return new PersonalDashboard(
        utilization, projectBreakdown, overdueTaskCount, upcomingDeadlines, trend);
  }

  private List<TrendPoint> computeMemberTrend(UUID memberId, LocalDate from, LocalDate to) {
    long daysBetween = ChronoUnit.DAYS.between(from, to);

    String granularity;
    String format;
    if (daysBetween <= 7) {
      granularity = "day";
      format = "YYYY-MM-DD";
    } else if (daysBetween <= 90) {
      granularity = "week";
      format = "IYYY-\"W\"IW";
    } else {
      granularity = "month";
      format = "YYYY-MM";
    }

    LocalDate trendFrom = computeTrendStart(to, granularity, 6);
    var results =
        timeEntryRepository.findMemberHoursTrend(memberId, trendFrom, to, granularity, format);

    return results.stream()
        .map(r -> new TrendPoint(r.getPeriod(), r.getTotalMinutes() / 60.0))
        .toList();
  }

  // --- Private: org-level computation ---

  private KpiValues computeKpiValues(LocalDate from, LocalDate to) {
    long activeCount = projectRepository.countActiveProjects(ProjectStatus.ACTIVE);

    var hoursSummary = timeEntryRepository.findOrgHoursSummary(from, to);
    double totalHours = hoursSummary.getTotalMinutes() / 60.0;
    long billableMinutes = hoursSummary.getBillableMinutes();
    long totalMinutes = hoursSummary.getTotalMinutes();
    Double billablePercent =
        totalMinutes > 0 ? (double) billableMinutes / totalMinutes * 100 : null;

    long overdueCount = taskRepository.countOrgOverdue(LocalDate.now());

    // averageMarginPercent: null until Phase 8 rate snapshots are available
    Double averageMarginPercent = null;

    return new KpiValues(
        (int) activeCount, totalHours, billablePercent, (int) overdueCount, averageMarginPercent);
  }

  private List<TrendPoint> computeTrend(LocalDate from, LocalDate to) {
    long daysBetween = ChronoUnit.DAYS.between(from, to);

    String granularity;
    String format;
    if (daysBetween <= 7) {
      granularity = "day";
      format = "YYYY-MM-DD";
    } else if (daysBetween <= 90) {
      granularity = "week";
      format = "IYYY-\"W\"IW";
    } else {
      granularity = "month";
      format = "YYYY-MM";
    }

    // Query the last 6 periods leading up to 'to'
    LocalDate trendFrom = computeTrendStart(to, granularity, 6);
    var results = timeEntryRepository.findHoursTrend(trendFrom, to, granularity, format);

    return results.stream()
        .map(r -> new TrendPoint(r.getPeriod(), r.getTotalMinutes() / 60.0))
        .toList();
  }

  private LocalDate computeTrendStart(LocalDate to, String granularity, int periods) {
    return switch (granularity) {
      case "day" -> to.minusDays(periods);
      case "week" -> to.minusWeeks(periods);
      case "month" -> to.minusMonths(periods);
      default -> to.minusDays(periods);
    };
  }

  private KpiValues computePreviousPeriod(LocalDate from, LocalDate to) {
    long daysBetween = ChronoUnit.DAYS.between(from, to) + 1;
    LocalDate prevFrom = from.minusDays(daysBetween);
    LocalDate prevTo = from.minusDays(1);

    return computeKpiValues(prevFrom, prevTo);
  }

  private List<MatterHealth> computeProjectHealthList(ActorContext actor) {
    // Get accessible projects based on role
    List<MatterWithRole> projectsWithRoles;
    if (isAdminOrOwner(actor.orgRole())) {
      projectsWithRoles = projectRepository.findAllProjectsWithRole(actor.memberId());
    } else {
      projectsWithRoles = projectRepository.findProjectsForMember(actor.memberId());
    }

    LocalDate today = LocalDate.now();

    return projectsWithRoles.stream()
        .map(
            projectWithRole -> {
              var project = projectWithRole.matter();
              UUID projectId = project.getId();

              long totalTasks = taskRepository.countByProjectId(projectId);
              long doneTasks = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
              long overdueTasks = taskRepository.countOverdueByProjectId(projectId, today);
              double completionPercent = totalTasks > 0 ? (double) doneTasks / totalTasks * 100 : 0;

              // Budget data
              Double budgetConsumedPercent = null;
              int alertThresholdPct = ProjectHealthCalculator.DEFAULT_BUDGET_ALERT_THRESHOLD;
              var budgetOpt = projectBudgetRepository.findByProjectId(projectId);
              if (budgetOpt.isPresent()) {
                var budget = budgetOpt.get();
                alertThresholdPct = budget.getAlertThresholdPct();
                var budgetHours = budget.getBudgetHours();
                if (budgetHours != null && budgetHours.compareTo(BigDecimal.ZERO) > 0) {
                  var consumed =
                      timeEntryRepository.budgetHoursConsumed(projectId).getHoursConsumed();
                  budgetConsumedPercent =
                      consumed
                          .multiply(BigDecimal.valueOf(100))
                          .divide(budgetHours, 2, RoundingMode.HALF_UP)
                          .doubleValue();
                }
              }

              int daysSinceLastActivity = computeDaysSinceLastActivity(projectId);

              // Hours logged (all-time for the list view)
              double hoursLogged =
                  timeEntryRepository.projectTimeSummary(projectId, null, null).getTotalMinutes()
                      / 60.0;

              // Calculate health
              var input =
                  new ProjectHealthInput(
                      (int) totalTasks,
                      (int) doneTasks,
                      (int) overdueTasks,
                      budgetConsumedPercent,
                      alertThresholdPct,
                      completionPercent,
                      daysSinceLastActivity);
              var healthResult = ProjectHealthCalculator.calculate(input);

              // Look up customer name
              String customerName = lookupCustomerName(projectId);

              return new MatterHealth(
                  projectId,
                  project.getName(),
                  customerName,
                  healthResult.status(),
                  healthResult.reasons(),
                  (int) doneTasks,
                  (int) totalTasks,
                  completionPercent,
                  budgetConsumedPercent,
                  hoursLogged);
            })
        .sorted(
            Comparator.comparingInt((MatterHealth ph) -> ph.healthStatus().severity())
                .reversed()
                .thenComparingDouble(MatterHealth::completionPercent))
        .toList();
  }

  private List<CrossMatterActivityItem> computeCrossProjectActivity(ActorContext actor, int limit) {
    List<CrossProjectActivityProjection> rows;
    if (isAdminOrOwner(actor.orgRole())) {
      rows = auditEventRepository.findCrossProjectActivity(limit);
    } else {
      rows = auditEventRepository.findCrossProjectActivityForMember(actor.memberId(), limit);
    }

    return rows.stream()
        .map(
            row ->
                new CrossMatterActivityItem(
                    row.getEventId(),
                    row.getEventType(),
                    buildActivityDescription(row.getEventType(), row.getActorName()),
                    row.getActorName(),
                    row.getProjectId(),
                    row.getProjectName(),
                    row.getOccurredAt()))
        .toList();
  }

  private String buildActivityDescription(String eventType, String actorName) {
    String actor = actorName != null ? actorName : "System";
    if (eventType == null) {
      return actor + " performed an action";
    }
    String[] parts = eventType.split("\\.");
    if (parts.length < 2) {
      return actor + " performed " + eventType;
    }
    String entity = parts[0];
    String action = parts[1];
    return switch (action) {
      case "created" -> actor + " created a " + entity;
      case "updated" -> actor + " updated a " + entity;
      case "deleted" -> actor + " deleted a " + entity;
      case "uploaded" -> actor + " uploaded a " + entity;
      default -> actor + " performed " + eventType;
    };
  }

  private String lookupCustomerName(UUID projectId) {
    return customerProjectRepository
        .findFirstCustomerByProjectId(projectId)
        .flatMap(customerRepository::findById)
        .map(c -> c.getName())
        .orElse(null);
  }

  // --- Shared helpers ---

  private void requireValidDateRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidStateException(
          "Invalid Date Range", "'from' date must not be after 'to' date");
    }
  }

  private boolean isAdminOrOwner(String orgRole) {
    return Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole);
  }

  private int computeDaysSinceLastActivity(UUID projectId) {
    Optional<Instant> lastActivity = auditEventRepository.findMostRecentByProject(projectId);
    if (lastActivity.isEmpty()) {
      return 0; // No audit events — treat as fresh (not stale)
    }
    return (int) Duration.between(lastActivity.get(), Instant.now()).toDays();
  }
}
