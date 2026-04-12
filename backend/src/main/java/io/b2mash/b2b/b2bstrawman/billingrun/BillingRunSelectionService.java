package io.b2mash.b2b.b2bstrawman.billingrun;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunItemResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunPreviewResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles billing run preview loading, item retrieval, entry selection, and customer
 * inclusion/exclusion. Extracted from BillingRunService as a focused collaborator.
 */
@Service
public class BillingRunSelectionService {

  private static final Logger log = LoggerFactory.getLogger(BillingRunSelectionService.class);

  private final BillingRunRepository billingRunRepository;
  private final BillingRunItemRepository billingRunItemRepository;
  private final BillingRunEntrySelectionRepository billingRunEntrySelectionRepository;
  private final CustomerRepository customerRepository;
  private final PrerequisiteService prerequisiteService;
  private final TimeEntryRepository timeEntryRepository;
  private final ExpenseRepository expenseRepository;
  private final EntityManager entityManager;

  public BillingRunSelectionService(
      BillingRunRepository billingRunRepository,
      BillingRunItemRepository billingRunItemRepository,
      BillingRunEntrySelectionRepository billingRunEntrySelectionRepository,
      CustomerRepository customerRepository,
      PrerequisiteService prerequisiteService,
      TimeEntryRepository timeEntryRepository,
      ExpenseRepository expenseRepository,
      EntityManager entityManager) {
    this.billingRunRepository = billingRunRepository;
    this.billingRunItemRepository = billingRunItemRepository;
    this.billingRunEntrySelectionRepository = billingRunEntrySelectionRepository;
    this.customerRepository = customerRepository;
    this.prerequisiteService = prerequisiteService;
    this.timeEntryRepository = timeEntryRepository;
    this.expenseRepository = expenseRepository;
    this.entityManager = entityManager;
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

    billingRunEntrySelectionRepository.deleteByBillingRunId(billingRunId);
    billingRunItemRepository.deleteByBillingRunId(billingRunId);
    entityManager.flush();

    List<CustomerDiscoveryRow> discoveredCustomers = discoverEligibleCustomers(run, request);

    List<BillingRunItemResponse> itemResponses = new ArrayList<>();
    BigDecimal totalUnbilledAmount = BigDecimal.ZERO;

    for (var row : discoveredCustomers) {
      var previewItem = createPreviewItem(billingRunId, row, run);
      itemResponses.add(previewItem.response());
      if (!previewItem.hasIssues()) {
        totalUnbilledAmount = totalUnbilledAmount.add(previewItem.itemTotal());
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

  // Package-private helpers used by BillingRunGenerationService

  List<UUID> resolveSelectedTimeEntryIds(BillingRunItem item) {
    return billingRunEntrySelectionRepository.findByBillingRunItemId(item.getId()).stream()
        .filter(s -> s.getEntryType() == EntryType.TIME_ENTRY && s.isIncluded())
        .map(BillingRunEntrySelection::getEntryId)
        .toList();
  }

  List<UUID> resolveSelectedExpenseIds(BillingRunItem item) {
    return billingRunEntrySelectionRepository.findByBillingRunItemId(item.getId()).stream()
        .filter(s -> s.getEntryType() == EntryType.EXPENSE && s.isIncluded())
        .map(BillingRunEntrySelection::getEntryId)
        .toList();
  }

  // --- Private helpers ---

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

  private List<CustomerDiscoveryRow> discoverEligibleCustomers(
      BillingRun run, LoadPreviewRequest request) {
    if (request == null || request.customerIds() == null || request.customerIds().isEmpty()) {
      return discoverCustomers(null, run.getPeriodFrom(), run.getPeriodTo(), run.getCurrency());
    } else {
      return discoverCustomers(
          request.customerIds(), run.getPeriodFrom(), run.getPeriodTo(), run.getCurrency());
    }
  }

  private record PreviewItemResult(
      BillingRunItemResponse response, boolean hasIssues, BigDecimal itemTotal) {}

  private PreviewItemResult createPreviewItem(
      UUID billingRunId, CustomerDiscoveryRow row, BillingRun run) {
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

    var response =
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
            item.getFailureReason());

    return new PreviewItemResult(response, hasIssues, itemTotal);
  }

  private record CustomerDiscoveryRow(
      UUID customerId,
      String customerName,
      int unbilledTimeCount,
      BigDecimal unbilledTimeAmount,
      int unbilledExpenseCount,
      BigDecimal unbilledExpenseAmount) {}

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
            AND (te.billing_rate_currency = :currency OR te.billing_rate_currency IS NULL)
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
          AND (te.billing_rate_currency = :currency OR te.billing_rate_currency IS NULL)
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

  BillingRunItemResponse toItemResponse(BillingRunItem item, Map<UUID, Customer> customerMap) {
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
}
