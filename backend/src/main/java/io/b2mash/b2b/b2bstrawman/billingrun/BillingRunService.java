package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunItemResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunPreviewResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.ExpenseResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.LoadPreviewRequest;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.TimeEntryResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.UpdateEntrySelectionsRequest;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.expense.Expense;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteViolation;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingRunService {

  private static final Logger log = LoggerFactory.getLogger(BillingRunService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final BillingRunEntrySelectionRepository billingRunEntrySelectionRepository;
  private final AuditService auditService;
  private final CustomerRepository customerRepository;
  private final PrerequisiteService prerequisiteService;
  private final TimeEntryRepository timeEntryRepository;
  private final ExpenseRepository expenseRepository;
  private final EntityManager entityManager;

  public BillingRunService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      BillingRunEntrySelectionRepository billingRunEntrySelectionRepository,
      AuditService auditService,
      CustomerRepository customerRepository,
      PrerequisiteService prerequisiteService,
      TimeEntryRepository timeEntryRepository,
      ExpenseRepository expenseRepository,
      EntityManager entityManager) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.billingRunEntrySelectionRepository = billingRunEntrySelectionRepository;
    this.auditService = auditService;
    this.customerRepository = customerRepository;
    this.prerequisiteService = prerequisiteService;
    this.timeEntryRepository = timeEntryRepository;
    this.expenseRepository = expenseRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public BillingRunResponse createRun(CreateBillingRunRequest request, UUID actorMemberId) {
    if (request.periodFrom().isAfter(request.periodTo())) {
      throw new InvalidStateException("Invalid period", "periodFrom must be on or before periodTo");
    }

    var run =
        new BillingRun(
            request.name(),
            request.periodFrom(),
            request.periodTo(),
            request.currency(),
            request.includeExpenses(),
            request.includeRetainers(),
            actorMemberId);

    run = billingRunRepository.save(run);

    log.info(
        "Created billing run {} for period {} to {}",
        run.getId(),
        run.getPeriodFrom(),
        run.getPeriodTo());

    auditService.log(AuditEventBuilder.billingRunCreated(run));

    return BillingRunResponse.from(run);
  }

  @Transactional
  public void cancelRun(UUID billingRunId, UUID actorMemberId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot cancel billing run",
          "Only billing runs in PREVIEW status can be cancelled. Current status: "
              + run.getStatus());
    }

    // Audit before delete so the event captures the run details
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("billing_run.cancelled")
            .entityType("billing_run")
            .entityId(billingRunId)
            .details(
                Map.of(
                    "name", run.getName() != null ? run.getName() : "",
                    "period_from", run.getPeriodFrom().toString(),
                    "period_to", run.getPeriodTo().toString(),
                    "status", run.getStatus().name()))
            .build());

    // Hard delete is appropriate here: PREVIEW-only billing runs have no generated invoices
    // or financial impact. Later slices (306B) will implement soft cancel for
    // IN_PROGRESS/COMPLETED runs that have associated invoices.
    billingRunEntrySelectionRepository.deleteByBillingRunId(billingRunId);
    billingRunItemRepository.deleteByBillingRunId(billingRunId);
    billingRunRepository.delete(run);

    log.info("Cancelled and deleted billing run {}", billingRunId);
  }

  @Transactional(readOnly = true)
  public BillingRunResponse getRun(UUID billingRunId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));
    return BillingRunResponse.from(run);
  }

  @Transactional(readOnly = true)
  public Page<BillingRunResponse> listRuns(Pageable pageable, List<BillingRunStatus> statuses) {
    Pageable effectivePageable = applyDefaultSort(pageable);
    Page<BillingRun> page;
    if (statuses != null && !statuses.isEmpty()) {
      page = billingRunRepository.findByStatusIn(statuses, effectivePageable);
    } else {
      page = billingRunRepository.findAll(effectivePageable);
    }
    return page.map(BillingRunResponse::from);
  }

  @Transactional
  public BillingRunPreviewResponse loadPreview(UUID billingRunId, LoadPreviewRequest request) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot load preview",
          "Only billing runs in PREVIEW status can be previewed. Current status: "
              + run.getStatus());
    }

    // Clear existing items/selections if re-running preview
    billingRunEntrySelectionRepository.deleteByBillingRunId(billingRunId);
    billingRunItemRepository.deleteByBillingRunId(billingRunId);
    entityManager.flush();

    // Determine customer IDs — auto-discover or use provided list
    List<CustomerDiscoveryRow> discoveredCustomers;
    if (request == null || request.customerIds() == null || request.customerIds().isEmpty()) {
      discoveredCustomers =
          discoverCustomersWithUnbilledWork(
              run.getPeriodFrom(), run.getPeriodTo(), run.getCurrency());
    } else {
      discoveredCustomers =
          discoverSpecificCustomers(
              request.customerIds(), run.getPeriodFrom(), run.getPeriodTo(), run.getCurrency());
    }

    List<BillingRunItemResponse> itemResponses = new ArrayList<>();
    BigDecimal totalUnbilledAmount = BigDecimal.ZERO;

    for (var row : discoveredCustomers) {
      // Check prerequisites
      var prereqCheck =
          prerequisiteService.checkForContext(
              PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, row.customerId());

      var item = new BillingRunItem(billingRunId, row.customerId());
      item.setUnbilledTimeAmount(row.unbilledTimeAmount());
      item.setUnbilledExpenseAmount(row.unbilledExpenseAmount());
      item.setUnbilledTimeCount(row.unbilledTimeCount());
      item.setUnbilledExpenseCount(row.unbilledExpenseCount());

      boolean hasIssues = !prereqCheck.passed();
      String issueReason = null;
      if (hasIssues) {
        item.markExcluded();
        issueReason =
            prereqCheck.violations().stream()
                .map(PrerequisiteViolation::message)
                .collect(Collectors.joining("; "));
        item.setFailureReason(issueReason);
      }

      item = billingRunItemRepository.save(item);

      // Create entry selections for this item's unbilled entries
      createEntrySelections(
          item.getId(),
          row.customerId(),
          run.getPeriodFrom(),
          run.getPeriodTo(),
          run.getCurrency(),
          run.isIncludeExpenses());

      BigDecimal itemTotal =
          (row.unbilledTimeAmount() != null ? row.unbilledTimeAmount() : BigDecimal.ZERO)
              .add(
                  row.unbilledExpenseAmount() != null
                      ? row.unbilledExpenseAmount()
                      : BigDecimal.ZERO);

      itemResponses.add(
          new BillingRunItemResponse(
              item.getId(),
              row.customerId(),
              row.customerName(),
              item.getStatus(),
              row.unbilledTimeAmount() != null ? row.unbilledTimeAmount() : BigDecimal.ZERO,
              row.unbilledExpenseAmount() != null ? row.unbilledExpenseAmount() : BigDecimal.ZERO,
              row.unbilledTimeCount(),
              row.unbilledExpenseCount(),
              itemTotal,
              hasIssues,
              issueReason,
              null,
              item.getFailureReason()));

      if (!hasIssues) {
        totalUnbilledAmount = totalUnbilledAmount.add(itemTotal);
      }
    }

    run.setTotalCustomers(discoveredCustomers.size());
    billingRunRepository.save(run);

    log.info(
        "Loaded preview for billing run {} — {} customers, total unbilled: {}",
        billingRunId,
        discoveredCustomers.size(),
        totalUnbilledAmount);

    return new BillingRunPreviewResponse(
        billingRunId, discoveredCustomers.size(), totalUnbilledAmount, itemResponses);
  }

  @Transactional(readOnly = true)
  public List<BillingRunItemResponse> getItems(UUID billingRunId) {
    billingRunRepository
        .findById(billingRunId)
        .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    var items = billingRunItemRepository.findByBillingRunId(billingRunId);

    // Batch-fetch all customers to avoid N+1
    List<UUID> customerIds = items.stream().map(BillingRunItem::getCustomerId).toList();
    Map<UUID, Customer> customerMap =
        customerRepository.findAllById(customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));

    return items.stream().map(item -> toItemResponse(item, customerMap)).toList();
  }

  @Transactional(readOnly = true)
  public BillingRunItemResponse getItem(UUID billingRunId, UUID itemId) {
    billingRunRepository
        .findById(billingRunId)
        .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    var item =
        billingRunItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRunItem", itemId));

    if (!item.getBillingRunId().equals(billingRunId)) {
      throw new ResourceNotFoundException("BillingRunItem", itemId);
    }

    var customer = customerRepository.findById(item.getCustomerId()).orElse(null);
    Map<UUID, Customer> customerMap =
        customer != null ? Map.of(customer.getId(), customer) : Map.of();
    return toItemResponse(item, customerMap);
  }

  @Transactional(readOnly = true)
  public List<TimeEntryResponse> getUnbilledTimeEntries(UUID billingRunId, UUID billingRunItemId) {
    validateItemBelongsToRun(billingRunId, billingRunItemId);

    var selections = billingRunEntrySelectionRepository.findByBillingRunItemId(billingRunItemId);

    List<UUID> timeEntryIds =
        selections.stream()
            .filter(s -> s.getEntryType() == EntryType.TIME_ENTRY)
            .map(BillingRunEntrySelection::getEntryId)
            .toList();

    if (timeEntryIds.isEmpty()) {
      return List.of();
    }
    return timeEntryRepository.findAllById(timeEntryIds).stream()
        .map(TimeEntryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ExpenseResponse> getUnbilledExpenses(UUID billingRunId, UUID billingRunItemId) {
    validateItemBelongsToRun(billingRunId, billingRunItemId);

    var selections = billingRunEntrySelectionRepository.findByBillingRunItemId(billingRunItemId);

    List<UUID> expenseIds =
        selections.stream()
            .filter(s -> s.getEntryType() == EntryType.EXPENSE)
            .map(BillingRunEntrySelection::getEntryId)
            .toList();

    if (expenseIds.isEmpty()) {
      return List.of();
    }
    return expenseRepository.findAllById(expenseIds).stream().map(ExpenseResponse::from).toList();
  }

  @Transactional
  public BillingRunItemResponse updateEntrySelection(
      UUID billingRunId, UUID billingRunItemId, UpdateEntrySelectionsRequest request) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot update selections",
          "Only billing runs in PREVIEW status allow selection changes. Current status: "
              + run.getStatus());
    }

    var item =
        billingRunItemRepository
            .findById(billingRunItemId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRunItem", billingRunItemId));

    if (!item.getBillingRunId().equals(billingRunId)) {
      throw new ResourceNotFoundException("BillingRunItem", billingRunItemId);
    }

    for (var selection : request.selections()) {
      var entrySelection =
          billingRunEntrySelectionRepository
              .findByBillingRunItemIdAndEntryTypeAndEntryId(
                  billingRunItemId, selection.entryType(), selection.entryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "BillingRunEntrySelection",
                          "item="
                              + billingRunItemId
                              + ", type="
                              + selection.entryType()
                              + ", entry="
                              + selection.entryId()));
      entrySelection.setIncluded(selection.included());
      billingRunEntrySelectionRepository.save(entrySelection);
    }

    recalculateItemTotals(item);
    item = billingRunItemRepository.save(item);

    var customer = customerRepository.findById(item.getCustomerId()).orElse(null);
    Map<UUID, Customer> customerMap =
        customer != null ? Map.of(customer.getId(), customer) : Map.of();
    return toItemResponse(item, customerMap);
  }

  @Transactional
  public BillingRunItemResponse excludeCustomer(UUID billingRunId, UUID billingRunItemId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot exclude customer",
          "Only billing runs in PREVIEW status allow exclusions. Current status: "
              + run.getStatus());
    }

    var item =
        billingRunItemRepository
            .findById(billingRunItemId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRunItem", billingRunItemId));

    if (!item.getBillingRunId().equals(billingRunId)) {
      throw new ResourceNotFoundException("BillingRunItem", billingRunItemId);
    }

    item.markExcluded();
    item = billingRunItemRepository.save(item);

    log.info("Excluded customer item {} from billing run {}", billingRunItemId, billingRunId);

    var customer = customerRepository.findById(item.getCustomerId()).orElse(null);
    Map<UUID, Customer> customerMap =
        customer != null ? Map.of(customer.getId(), customer) : Map.of();
    return toItemResponse(item, customerMap);
  }

  @Transactional
  public BillingRunItemResponse includeCustomer(UUID billingRunId, UUID billingRunItemId) {
    var run =
        billingRunRepository
            .findById(billingRunId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    if (run.getStatus() != BillingRunStatus.PREVIEW) {
      throw new InvalidStateException(
          "Cannot include customer",
          "Only billing runs in PREVIEW status allow inclusions. Current status: "
              + run.getStatus());
    }

    var item =
        billingRunItemRepository
            .findById(billingRunItemId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRunItem", billingRunItemId));

    if (!item.getBillingRunId().equals(billingRunId)) {
      throw new ResourceNotFoundException("BillingRunItem", billingRunItemId);
    }

    item.reInclude();
    item = billingRunItemRepository.save(item);

    log.info("Re-included customer item {} in billing run {}", billingRunItemId, billingRunId);

    var customer = customerRepository.findById(item.getCustomerId()).orElse(null);
    Map<UUID, Customer> customerMap =
        customer != null ? Map.of(customer.getId(), customer) : Map.of();
    return toItemResponse(item, customerMap);
  }

  private void recalculateItemTotals(BillingRunItem item) {
    List<BillingRunEntrySelection> selections =
        billingRunEntrySelectionRepository.findByBillingRunItemId(item.getId());

    List<UUID> includedTimeEntryIds =
        selections.stream()
            .filter(s -> s.getEntryType() == EntryType.TIME_ENTRY && s.isIncluded())
            .map(BillingRunEntrySelection::getEntryId)
            .toList();

    List<UUID> includedExpenseIds =
        selections.stream()
            .filter(s -> s.getEntryType() == EntryType.EXPENSE && s.isIncluded())
            .map(BillingRunEntrySelection::getEntryId)
            .toList();

    BigDecimal timeAmount = BigDecimal.ZERO;
    if (!includedTimeEntryIds.isEmpty()) {
      var timeEntries = timeEntryRepository.findAllById(includedTimeEntryIds);
      timeAmount =
          timeEntries.stream()
              .map(te -> te.getBillableValue() != null ? te.getBillableValue() : BigDecimal.ZERO)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal expenseAmount = BigDecimal.ZERO;
    if (!includedExpenseIds.isEmpty()) {
      var expenses = expenseRepository.findAllById(includedExpenseIds);
      expenseAmount =
          expenses.stream()
              .map(Expense::getBillableAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    item.setUnbilledTimeAmount(timeAmount);
    item.setUnbilledTimeCount(includedTimeEntryIds.size());
    item.setUnbilledExpenseAmount(expenseAmount);
    item.setUnbilledExpenseCount(includedExpenseIds.size());
  }

  private void validateItemBelongsToRun(UUID billingRunId, UUID billingRunItemId) {
    billingRunRepository
        .findById(billingRunId)
        .orElseThrow(() -> new ResourceNotFoundException("BillingRun", billingRunId));

    var item =
        billingRunItemRepository
            .findById(billingRunItemId)
            .orElseThrow(() -> new ResourceNotFoundException("BillingRunItem", billingRunItemId));

    if (!item.getBillingRunId().equals(billingRunId)) {
      throw new ResourceNotFoundException("BillingRunItem", billingRunItemId);
    }
  }

  // --- Private helpers ---

  private record CustomerDiscoveryRow(
      UUID customerId,
      String customerName,
      int unbilledTimeCount,
      BigDecimal unbilledTimeAmount,
      int unbilledExpenseCount,
      BigDecimal unbilledExpenseAmount) {}

  private List<CustomerDiscoveryRow> discoverCustomersWithUnbilledWork(
      LocalDate periodFrom, LocalDate periodTo, String currency) {
    return discoverCustomers(null, periodFrom, periodTo, currency);
  }

  private List<CustomerDiscoveryRow> discoverSpecificCustomers(
      List<UUID> customerIds, LocalDate periodFrom, LocalDate periodTo, String currency) {
    return discoverCustomers(customerIds, periodFrom, periodTo, currency);
  }

  /**
   * Shared discovery query. When customerIds is null, auto-discovers all ACTIVE customers with
   * unbilled work. When customerIds is provided, filters to those specific customers (still
   * requiring ACTIVE status).
   */
  @SuppressWarnings("unchecked")
  private List<CustomerDiscoveryRow> discoverCustomers(
      List<UUID> customerIds, LocalDate periodFrom, LocalDate periodTo, String currency) {
    String whereClause;
    if (customerIds != null) {
      whereClause = "WHERE c.lifecycle_status = 'ACTIVE' AND c.id IN :customerIds";
    } else {
      whereClause = "WHERE c.lifecycle_status = 'ACTIVE'";
    }

    String sql =
        """
        SELECT
            c.id AS customer_id,
            c.name AS customer_name,
            COUNT(DISTINCT te.id) AS unbilled_time_count,
            COALESCE(SUM(
                CASE WHEN te.id IS NOT NULL
                THEN (te.duration_minutes / 60.0) * te.billing_rate_snapshot
                ELSE 0 END
            ), 0) AS unbilled_time_amount,
            COUNT(DISTINCT e.id) AS unbilled_expense_count,
            COALESCE(SUM(
                CASE WHEN e.id IS NOT NULL
                THEN e.amount * (1 + COALESCE(e.markup_percent, 0) / 100.0)
                ELSE 0 END
            ), 0) AS unbilled_expense_amount
        FROM customers c
        JOIN customer_projects cp ON cp.customer_id = c.id
        JOIN projects p ON cp.project_id = p.id
        LEFT JOIN tasks t ON t.project_id = p.id
        LEFT JOIN time_entries te ON te.task_id = t.id
            AND te.billable = true
            AND te.invoice_id IS NULL
            AND te.billing_rate_currency = :currency
            AND te.date >= :periodFrom
            AND te.date <= :periodTo
        LEFT JOIN expenses e ON e.project_id = p.id
            AND e.billable = true
            AND e.invoice_id IS NULL
            AND e.currency = :currency
            AND e.date >= :periodFrom
            AND e.date <= :periodTo
        %s
        GROUP BY c.id, c.name
        HAVING COUNT(DISTINCT te.id) > 0 OR COUNT(DISTINCT e.id) > 0
        ORDER BY c.name
        """
            .formatted(whereClause);

    var query =
        entityManager
            .createNativeQuery(sql, Tuple.class)
            .setParameter("currency", currency)
            .setParameter("periodFrom", periodFrom)
            .setParameter("periodTo", periodTo);

    if (customerIds != null) {
      query.setParameter("customerIds", customerIds);
    }

    List<Tuple> rows = query.getResultList();
    return rows.stream().map(this::toDiscoveryRow).toList();
  }

  private CustomerDiscoveryRow toDiscoveryRow(Tuple row) {
    return new CustomerDiscoveryRow(
        row.get("customer_id", UUID.class),
        row.get("customer_name", String.class),
        ((Number) row.get("unbilled_time_count")).intValue(),
        row.get("unbilled_time_amount", BigDecimal.class),
        ((Number) row.get("unbilled_expense_count")).intValue(),
        row.get("unbilled_expense_amount", BigDecimal.class));
  }

  @SuppressWarnings("unchecked")
  private void createEntrySelections(
      UUID billingRunItemId,
      UUID customerId,
      LocalDate periodFrom,
      LocalDate periodTo,
      String currency,
      boolean includeExpenses) {
    // Find unbilled time entries for this customer in the period
    String timeSql =
        """
        SELECT te.id
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN projects p ON t.project_id = p.id
        JOIN customer_projects cp ON cp.project_id = p.id
        WHERE cp.customer_id = :customerId
          AND te.billable = true
          AND te.invoice_id IS NULL
          AND te.billing_rate_currency = :currency
          AND te.date >= :periodFrom
          AND te.date <= :periodTo
        """;

    List<UUID> timeEntryIds =
        ((List<Tuple>)
                entityManager
                    .createNativeQuery(timeSql, Tuple.class)
                    .setParameter("customerId", customerId)
                    .setParameter("currency", currency)
                    .setParameter("periodFrom", periodFrom)
                    .setParameter("periodTo", periodTo)
                    .getResultList())
            .stream().map(t -> t.get("id", UUID.class)).toList();

    List<BillingRunEntrySelection> selections = new ArrayList<>();
    for (UUID entryId : timeEntryIds) {
      selections.add(new BillingRunEntrySelection(billingRunItemId, EntryType.TIME_ENTRY, entryId));
    }

    // Find unbilled expenses if included
    if (includeExpenses) {
      String expenseSql =
          """
          SELECT e.id
          FROM expenses e
          JOIN projects p ON e.project_id = p.id
          JOIN customer_projects cp ON cp.project_id = p.id
          WHERE cp.customer_id = :customerId
            AND e.billable = true
            AND e.invoice_id IS NULL
            AND e.currency = :currency
            AND e.date >= :periodFrom
            AND e.date <= :periodTo
          """;

      List<UUID> expenseIds =
          ((List<Tuple>)
                  entityManager
                      .createNativeQuery(expenseSql, Tuple.class)
                      .setParameter("customerId", customerId)
                      .setParameter("currency", currency)
                      .setParameter("periodFrom", periodFrom)
                      .setParameter("periodTo", periodTo)
                      .getResultList())
              .stream().map(t -> t.get("id", UUID.class)).toList();

      for (UUID entryId : expenseIds) {
        selections.add(new BillingRunEntrySelection(billingRunItemId, EntryType.EXPENSE, entryId));
      }
    }

    if (!selections.isEmpty()) {
      billingRunEntrySelectionRepository.saveAll(selections);
    }
  }

  private BillingRunItemResponse toItemResponse(
      BillingRunItem item, Map<UUID, Customer> customerMap) {
    var customer = customerMap.get(item.getCustomerId());
    String customerName = customer != null ? customer.getName() : "Unknown";

    BigDecimal timeAmount =
        item.getUnbilledTimeAmount() != null ? item.getUnbilledTimeAmount() : BigDecimal.ZERO;
    BigDecimal expenseAmount =
        item.getUnbilledExpenseAmount() != null ? item.getUnbilledExpenseAmount() : BigDecimal.ZERO;
    BigDecimal totalAmount = timeAmount.add(expenseAmount);

    boolean hasIssues = item.getStatus() == BillingRunItemStatus.EXCLUDED;

    return new BillingRunItemResponse(
        item.getId(),
        item.getCustomerId(),
        customerName,
        item.getStatus(),
        timeAmount,
        expenseAmount,
        item.getUnbilledTimeCount() != null ? item.getUnbilledTimeCount() : 0,
        item.getUnbilledExpenseCount() != null ? item.getUnbilledExpenseCount() : 0,
        totalAmount,
        hasIssues,
        item.getFailureReason(),
        item.getInvoiceId(),
        item.getFailureReason());
  }

  private Pageable applyDefaultSort(Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }
    return PageRequest.of(
        pageable.getPageNumber(),
        pageable.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
