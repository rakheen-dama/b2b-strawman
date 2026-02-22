package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CurrencyTotal;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledProjectGroup;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeEntry;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class InvoiceService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final OrganizationRepository organizationRepository;
  private final ProjectRepository projectRepository;
  private final TaskRepository taskRepository;
  private final MemberNameResolver memberNameResolver;
  private final InvoiceNumberService invoiceNumberService;
  private final PaymentProvider paymentProvider;
  private final EntityManager entityManager;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final ITemplateEngine templateEngine;
  private final CustomerLifecycleGuard customerLifecycleGuard;

  public InvoiceService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      TimeEntryRepository timeEntryRepository,
      OrganizationRepository organizationRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      MemberNameResolver memberNameResolver,
      InvoiceNumberService invoiceNumberService,
      PaymentProvider paymentProvider,
      EntityManager entityManager,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      ITemplateEngine templateEngine,
      CustomerLifecycleGuard customerLifecycleGuard) {
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.organizationRepository = organizationRepository;
    this.projectRepository = projectRepository;
    this.taskRepository = taskRepository;
    this.memberNameResolver = memberNameResolver;
    this.invoiceNumberService = invoiceNumberService;
    this.paymentProvider = paymentProvider;
    this.entityManager = entityManager;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.templateEngine = templateEngine;
    this.customerLifecycleGuard = customerLifecycleGuard;
  }

  @Transactional
  public InvoiceResponse createDraft(CreateInvoiceRequest request, UUID createdBy) {
    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    if (!"ACTIVE".equals(customer.getStatus())) {
      throw new InvalidStateException(
          "Customer not active", "Customer must be in ACTIVE status to create an invoice");
    }

    // Check lifecycle guard (complementary to soft-delete status check above)
    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE);

    // Look up organization for orgName snapshot
    String orgId = RequestScopes.requireOrgId();
    var organization =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

    var invoice =
        new Invoice(
            request.customerId(),
            request.currency(),
            customer.getName(),
            customer.getEmail(),
            null, // Customer has no address field
            organization.getName(),
            createdBy);

    if (request.dueDate() != null) {
      invoice.updateDraft(request.dueDate(), request.notes(), request.paymentTerms(), null);
    } else if (request.notes() != null || request.paymentTerms() != null) {
      invoice.updateDraft(null, request.notes(), request.paymentTerms(), null);
    }

    invoice = invoiceRepository.save(invoice);

    // Create line items from time entries
    var timeEntryIds = request.timeEntryIds();
    var linkedTimeEntries = new ArrayList<io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry>();
    if (timeEntryIds != null && !timeEntryIds.isEmpty()) {
      int sortOrder = 0;
      for (UUID timeEntryId : timeEntryIds) {
        var timeEntry =
            timeEntryRepository
                .findById(timeEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

        if (!timeEntry.isBillable()) {
          throw new InvalidStateException(
              "Time entry not billable",
              "Time entry " + timeEntryId + " is not marked as billable");
        }

        if (timeEntry.getInvoiceId() != null) {
          throw new ResourceConflictException(
              "Time entry already invoiced",
              "Time entry " + timeEntryId + " is already linked to an invoice");
        }

        if (timeEntry.getBillingRateCurrency() != null
            && !timeEntry.getBillingRateCurrency().equals(request.currency())) {
          throw new InvalidStateException(
              "Currency mismatch",
              "Time entry "
                  + timeEntryId
                  + " has currency "
                  + timeEntry.getBillingRateCurrency()
                  + " but invoice currency is "
                  + request.currency());
        }

        // Reject time entries without a task — cannot validate customer-project linkage
        if (timeEntry.getTaskId() == null) {
          throw new InvalidStateException(
              "Time entry has no task",
              "Time entry "
                  + timeEntryId
                  + " is not linked to a task and cannot be invoiced for a customer");
        }

        // Look up task to get projectId (used for both validation and line item creation)
        UUID projectId = null;
        var task = taskRepository.findById(timeEntry.getTaskId());
        if (task.isPresent()) {
          projectId = task.get().getProjectId();
          // Validate time entry belongs to customer's projects
          if (!customerProjectRepository.existsByCustomerIdAndProjectId(
              request.customerId(), projectId)) {
            throw new InvalidStateException(
                "Time entry not linked to customer",
                "Time entry "
                    + timeEntryId
                    + " belongs to a project not linked to customer "
                    + request.customerId());
          }
        }

        // Build description: "{task title} -- {date} -- {member name}"
        String description = buildTimeEntryDescription(timeEntry);

        BigDecimal quantity =
            BigDecimal.valueOf(timeEntry.getDurationMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal unitPrice =
            timeEntry.getBillingRateSnapshot() != null
                ? timeEntry.getBillingRateSnapshot()
                : BigDecimal.ZERO;

        var line =
            new InvoiceLine(
                invoice.getId(),
                projectId,
                timeEntryId,
                description,
                quantity,
                unitPrice,
                sortOrder++);
        lineRepository.save(line);
        linkedTimeEntries.add(timeEntry);
      }

      // Link time entries to invoice to prevent double-billing
      for (var timeEntry : linkedTimeEntries) {
        timeEntry.setInvoiceId(invoice.getId());
      }
      timeEntryRepository.saveAll(linkedTimeEntries);
    }

    // Recompute totals
    recalculateInvoiceTotals(invoice);
    invoice = invoiceRepository.save(invoice);

    log.info("Created draft invoice {} for customer {}", invoice.getId(), request.customerId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.created")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "customer_id",
                    request.customerId().toString(),
                    "customer_name",
                    customer.getName(),
                    "currency",
                    request.currency(),
                    "line_count",
                    String.valueOf(timeEntryIds != null ? timeEntryIds.size() : 0),
                    "subtotal",
                    invoice.getSubtotal().toString()))
            .build());

    return buildResponse(invoice);
  }

  @Transactional(readOnly = true)
  public UnbilledTimeResponse getUnbilledTime(UUID customerId, LocalDate from, LocalDate to) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Native SQL query: joins time_entries -> tasks -> projects -> customer_projects -> members
    // Tenant isolation is provided by the dedicated schema (search_path set on connection checkout)
    String sql =
        """
        SELECT te.id AS te_id, te.date AS te_date, te.duration_minutes AS te_duration,
               te.billing_rate_snapshot AS te_rate, te.billing_rate_currency AS te_currency,
               te.description AS te_description,
               t.title AS task_title,
               p.id AS project_id, p.name AS project_name,
               m.name AS member_name
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN projects p ON t.project_id = p.id
        JOIN customer_projects cp ON cp.project_id = p.id
        JOIN members m ON te.member_id = m.id
        WHERE cp.customer_id = :customerId
          AND te.billable = true
          AND te.invoice_id IS NULL
          AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
          AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
        ORDER BY p.name, te.date, m.name
        """;

    @SuppressWarnings("unchecked")
    List<Tuple> rows =
        entityManager
            .createNativeQuery(sql, Tuple.class)
            .setParameter("customerId", customerId)
            .setParameter("fromDate", from)
            .setParameter("toDate", to)
            .getResultList();

    // Post-process: group by projectId, compute per-currency totals and grand totals
    Map<UUID, List<UnbilledTimeEntry>> grouped = new LinkedHashMap<>();
    Map<UUID, String> projectNames = new LinkedHashMap<>();

    for (Tuple row : rows) {
      UUID projectId = row.get("project_id", UUID.class);
      String projectName = row.get("project_name", String.class);
      projectNames.putIfAbsent(projectId, projectName);

      BigDecimal rate = row.get("te_rate", BigDecimal.class);
      String currency = row.get("te_currency", String.class);
      int durationMinutes = ((Number) row.get("te_duration")).intValue();

      BigDecimal billableValue = null;
      if (rate != null) {
        billableValue =
            BigDecimal.valueOf(durationMinutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
      }

      // Hibernate may return LocalDate or java.sql.Date depending on driver/version
      Object dateObj = row.get("te_date");
      LocalDate entryDate;
      if (dateObj instanceof LocalDate ld) {
        entryDate = ld;
      } else {
        entryDate = ((java.sql.Date) dateObj).toLocalDate();
      }

      var entry =
          new UnbilledTimeEntry(
              row.get("te_id", UUID.class),
              row.get("task_title", String.class),
              row.get("member_name", String.class),
              entryDate,
              durationMinutes,
              rate,
              currency,
              billableValue,
              row.get("te_description", String.class));

      grouped.computeIfAbsent(projectId, k -> new ArrayList<>()).add(entry);
    }

    // Build project groups with per-currency totals
    Map<String, double[]> grandHours = new LinkedHashMap<>();
    Map<String, BigDecimal> grandAmounts = new LinkedHashMap<>();

    List<UnbilledProjectGroup> projectGroups = new ArrayList<>();
    for (var mapEntry : grouped.entrySet()) {
      UUID projectId = mapEntry.getKey();
      List<UnbilledTimeEntry> entries = mapEntry.getValue();
      String projectName = projectNames.get(projectId);

      Map<String, double[]> projHours = new LinkedHashMap<>();
      Map<String, BigDecimal> projAmounts = new LinkedHashMap<>();

      for (var e : entries) {
        String cur = e.billingRateCurrency() != null ? e.billingRateCurrency() : "UNKNOWN";
        double hours = e.durationMinutes() / 60.0;
        projHours.computeIfAbsent(cur, k -> new double[1])[0] += hours;
        projAmounts.merge(
            cur, e.billableValue() != null ? e.billableValue() : BigDecimal.ZERO, BigDecimal::add);

        grandHours.computeIfAbsent(cur, k -> new double[1])[0] += hours;
        grandAmounts.merge(
            cur, e.billableValue() != null ? e.billableValue() : BigDecimal.ZERO, BigDecimal::add);
      }

      Map<String, CurrencyTotal> projTotals = new LinkedHashMap<>();
      for (var curEntry : projHours.entrySet()) {
        projTotals.put(
            curEntry.getKey(),
            new CurrencyTotal(
                curEntry.getValue()[0],
                projAmounts.getOrDefault(curEntry.getKey(), BigDecimal.ZERO)));
      }

      projectGroups.add(new UnbilledProjectGroup(projectId, projectName, entries, projTotals));
    }

    Map<String, CurrencyTotal> grandTotals = new LinkedHashMap<>();
    for (var curEntry : grandHours.entrySet()) {
      grandTotals.put(
          curEntry.getKey(),
          new CurrencyTotal(
              curEntry.getValue()[0],
              grandAmounts.getOrDefault(curEntry.getKey(), BigDecimal.ZERO)));
    }

    return new UnbilledTimeResponse(customerId, customer.getName(), projectGroups, grandTotals);
  }

  @Transactional
  public InvoiceResponse updateDraft(UUID invoiceId, UpdateInvoiceRequest request) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.updateDraft(
          request.dueDate(), request.notes(), request.paymentTerms(), request.taxAmount());
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invoice not editable", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);
    log.info("Updated draft invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.updated")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "new_subtotal",
                    invoice.getSubtotal().toString(),
                    "new_total",
                    invoice.getTotal().toString()))
            .build());

    return buildResponse(invoice);
  }

  @Transactional
  public void deleteDraft(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not deletable", "Only draft invoices can be deleted");
    }

    // Unlink time entries before deleting lines
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    var timeEntryIdsToUnlink =
        lines.stream().map(InvoiceLine::getTimeEntryId).filter(Objects::nonNull).toList();
    if (!timeEntryIdsToUnlink.isEmpty()) {
      for (UUID teId : timeEntryIdsToUnlink) {
        timeEntryRepository
            .findById(teId)
            .ifPresent(
                te -> {
                  te.setInvoiceId(null);
                });
      }
    }

    // Delete lines explicitly first (then invoice)
    lineRepository.deleteAll(lines);
    invoiceRepository.delete(invoice);
    log.info("Deleted draft invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.deleted")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(
                Map.of(
                    "customer_name",
                    invoice.getCustomerName(),
                    "line_count",
                    String.valueOf(lines.size())))
            .build());
  }

  @Transactional(readOnly = true)
  public InvoiceResponse findById(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    return buildResponse(invoice);
  }

  @Transactional(readOnly = true)
  public List<InvoiceResponse> findAll(UUID customerId, InvoiceStatus status, UUID projectId) {
    List<Invoice> invoices;

    if (customerId != null) {
      invoices = invoiceRepository.findByCustomerId(customerId);
    } else if (status != null) {
      invoices = invoiceRepository.findByStatus(status);
    } else if (projectId != null) {
      invoices = invoiceRepository.findByProjectId(projectId);
    } else {
      invoices = invoiceRepository.findAllOrdered();
    }

    return invoices.stream().map(this::buildResponse).toList();
  }

  @Transactional
  public InvoiceResponse addLineItem(UUID invoiceId, AddLineItemRequest request) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Line items can only be added to draft invoices");
    }

    var line =
        new InvoiceLine(
            invoiceId,
            request.projectId(),
            null, // manual line item, no time entry
            request.description(),
            request.quantity(),
            request.unitPrice(),
            request.sortOrder());
    lineRepository.save(line);

    recalculateInvoiceTotals(invoice);
    invoice = invoiceRepository.save(invoice);

    log.info("Added line item to invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.updated")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(
                Map.of(
                    "action",
                    "line_item_added",
                    "new_subtotal",
                    invoice.getSubtotal().toString(),
                    "new_total",
                    invoice.getTotal().toString()))
            .build());

    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse updateLineItem(
      UUID invoiceId, UUID lineId, UpdateLineItemRequest request) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Line items can only be updated on draft invoices");
    }

    var line =
        lineRepository
            .findById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));

    if (!line.getInvoiceId().equals(invoiceId)) {
      throw new ResourceNotFoundException("InvoiceLine", lineId);
    }

    line.update(
        request.description(), request.quantity(), request.unitPrice(), request.sortOrder());
    lineRepository.save(line);

    recalculateInvoiceTotals(invoice);
    invoice = invoiceRepository.save(invoice);

    log.info("Updated line item {} on invoice {}", lineId, invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.updated")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(
                Map.of(
                    "action",
                    "line_item_updated",
                    "new_subtotal",
                    invoice.getSubtotal().toString(),
                    "new_total",
                    invoice.getTotal().toString()))
            .build());

    return buildResponse(invoice);
  }

  @Transactional
  public void deleteLineItem(UUID invoiceId, UUID lineId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Line items can only be removed from draft invoices");
    }

    var line =
        lineRepository
            .findById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));

    if (!line.getInvoiceId().equals(invoiceId)) {
      throw new ResourceNotFoundException("InvoiceLine", lineId);
    }

    // Unlink time entry if this line was generated from one
    if (line.getTimeEntryId() != null) {
      timeEntryRepository
          .findById(line.getTimeEntryId())
          .ifPresent(
              te -> {
                te.setInvoiceId(null);
              });
    }

    lineRepository.delete(line);

    recalculateInvoiceTotals(invoice);
    invoiceRepository.save(invoice);

    log.info("Deleted line item {} from invoice {}", lineId, invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.updated")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(
                Map.of(
                    "action",
                    "line_item_removed",
                    "new_subtotal",
                    invoice.getSubtotal().toString(),
                    "new_total",
                    invoice.getTotal().toString()))
            .build());
  }

  // --- Lifecycle transitions ---

  @Transactional
  public InvoiceResponse approve(UUID invoiceId, UUID approvedBy) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    // Validate has at least one line item
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    if (lines.isEmpty()) {
      throw new InvalidStateException(
          "No line items", "Invoice must have at least one line item before approval");
    }

    // Re-check all time-entry-based line items: verify each referenced time entry
    // still has invoice_id IS NULL or equals this invoice (guards against concurrent
    // draft + approve race condition — ADR-050)
    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        var timeEntry =
            timeEntryRepository
                .findById(line.getTimeEntryId())
                .orElseThrow(
                    () -> new ResourceNotFoundException("TimeEntry", line.getTimeEntryId()));
        if (timeEntry.getInvoiceId() != null && !timeEntry.getInvoiceId().equals(invoiceId)) {
          throw new ResourceConflictException(
              "Time entry already billed",
              "Time entry " + line.getTimeEntryId() + " is already linked to another invoice");
        }
      }
    }

    // Assign invoice number
    String invoiceNumber = invoiceNumberService.assignNumber();

    try {
      invoice.approve(invoiceNumber, approvedBy);
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);

    // Mark time entries as billed
    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        timeEntryRepository
            .findById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(invoiceId);
                  timeEntryRepository.save(te);
                });
      }
    }

    log.info("Approved invoice {} with number {}", invoiceId, invoiceNumber);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.approved")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoiceNumber,
                    "issue_date",
                    invoice.getIssueDate().toString(),
                    "total",
                    invoice.getTotal().toString(),
                    "time_entry_count",
                    String.valueOf(lines.stream().filter(l -> l.getTimeEntryId() != null).count())))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceApprovedEvent(
            "invoice.approved",
            "invoice",
            invoice.getId(),
            null,
            approvedBy,
            resolveActorName(approvedBy),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of("invoice_number", invoiceNumber, "customer_name", invoice.getCustomerName()),
            invoice.getCreatedBy(),
            invoiceNumber,
            invoice.getCustomerName()));

    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse send(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.markSent();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);
    log.info("Marked invoice {} as sent", invoiceId);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.sent")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(Map.of("invoice_number", invoice.getInvoiceNumber()))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceSentEvent(
            "invoice.sent",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber(),
                "customer_name",
                invoice.getCustomerName()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName()));

    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse recordPayment(UUID invoiceId, String paymentReference) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    // Validate status BEFORE calling payment provider (irreversible side effect)
    if (invoice.getStatus() != InvoiceStatus.SENT) {
      throw new ResourceConflictException(
          "Invalid status transition", "Only sent invoices can be paid");
    }

    // Call payment provider
    var paymentRequest =
        new PaymentRequest(
            invoiceId,
            invoice.getTotal(),
            invoice.getCurrency(),
            "Payment for invoice " + invoice.getInvoiceNumber());
    var paymentResult = paymentProvider.recordPayment(paymentRequest);

    if (!paymentResult.success()) {
      throw new InvalidStateException("Payment failed", paymentResult.errorMessage());
    }

    // Use provider reference if no manual reference provided
    String effectiveReference =
        paymentReference != null ? paymentReference : paymentResult.paymentReference();

    invoice.recordPayment(effectiveReference);

    invoice = invoiceRepository.save(invoice);
    log.info("Recorded payment for invoice {} with reference {}", invoiceId, effectiveReference);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.paid")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber(),
                    "payment_reference",
                    effectiveReference,
                    "total",
                    invoice.getTotal().toString(),
                    "paid_at",
                    invoice.getPaidAt().toString()))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoicePaidEvent(
            "invoice.paid",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber(),
                "customer_name",
                invoice.getCustomerName(),
                "payment_reference",
                effectiveReference),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            effectiveReference));

    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse voidInvoice(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.voidInvoice();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);

    // Clear invoice_id on all time entries referenced by this invoice's line items
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        timeEntryRepository
            .findById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(null);
                  timeEntryRepository.save(te);
                });
      }
    }

    log.info("Voided invoice {}", invoiceId);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.voided")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                    "total",
                    invoice.getTotal().toString(),
                    "reverted_time_entry_count",
                    String.valueOf(lines.stream().filter(l -> l.getTimeEntryId() != null).count())))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceVoidedEvent(
            "invoice.voided",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                "customer_name",
                invoice.getCustomerName()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            invoice.getApprovedBy()));

    return buildResponse(invoice);
  }

  // --- Preview ---

  @Transactional(readOnly = true)
  public String renderPreview(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    var projectNames = resolveProjectNames(lines);

    // Group lines by project name (preserving insertion order)
    // Lines with null projectId go under "Other Items"
    var groupedLines = new LinkedHashMap<String, List<InvoiceLine>>();
    var grouped =
        lines.stream()
            .collect(
                Collectors.groupingBy(
                    line -> {
                      if (line.getProjectId() == null) {
                        return "Other Items";
                      }
                      return projectNames.getOrDefault(line.getProjectId(), "Unknown Project");
                    },
                    LinkedHashMap::new,
                    Collectors.toList()));

    // Move "Other Items" to the end if present
    if (grouped.containsKey("Other Items")) {
      var otherItems = grouped.remove("Other Items");
      groupedLines.putAll(grouped);
      groupedLines.put("Other Items", otherItems);
    } else {
      groupedLines.putAll(grouped);
    }

    // Precompute per-group subtotals
    var groupSubtotals = new LinkedHashMap<String, BigDecimal>();
    for (var entry : groupedLines.entrySet()) {
      BigDecimal subtotal =
          entry.getValue().stream()
              .map(InvoiceLine::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      groupSubtotals.put(entry.getKey(), subtotal);
    }

    Context ctx = new Context();
    ctx.setVariable("invoice", invoice);
    ctx.setVariable("groupedLines", groupedLines);
    ctx.setVariable("groupSubtotals", groupSubtotals);

    return templateEngine.process("invoice-preview", ctx);
  }

  // --- Private helpers ---

  private String resolveActorName(UUID memberId) {
    if (memberId == null) return "System";
    return memberNameResolver.resolveName(memberId);
  }

  private void recalculateInvoiceTotals(Invoice invoice) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    BigDecimal subtotal =
        lines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    invoice.recalculateTotals(subtotal);
  }

  /** Batch-fetch project names for a list of invoice lines (single query instead of N+1). */
  private Map<UUID, String> resolveProjectNames(List<InvoiceLine> lines) {
    var projectIds =
        lines.stream().map(InvoiceLine::getProjectId).filter(Objects::nonNull).distinct().toList();

    if (!projectIds.isEmpty()) {
      return projectRepository.findAllById(projectIds).stream()
          .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
    }
    return Map.of();
  }

  private InvoiceResponse buildResponse(Invoice invoice) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    var projectNames = resolveProjectNames(lines);

    var lineResponses =
        lines.stream()
            .map(
                line ->
                    InvoiceLineResponse.from(
                        line,
                        line.getProjectId() != null ? projectNames.get(line.getProjectId()) : null))
            .toList();

    var memberNames = resolveMemberNames(invoice);
    return InvoiceResponse.from(invoice, lineResponses, memberNames);
  }

  private Map<UUID, String> resolveMemberNames(Invoice invoice) {
    var ids =
        Stream.of(invoice.getCreatedBy(), invoice.getApprovedBy())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }

  private String buildTimeEntryDescription(
      io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry timeEntry) {
    String taskTitle = "Untitled";
    String memberName = "Unknown";

    if (timeEntry.getTaskId() != null) {
      taskTitle =
          taskRepository.findById(timeEntry.getTaskId()).map(t -> t.getTitle()).orElse("Untitled");
    }

    if (timeEntry.getMemberId() != null) {
      memberName = memberNameResolver.resolveName(timeEntry.getMemberId());
    }

    return taskTitle + " -- " + timeEntry.getDate() + " -- " + memberName;
  }
}
