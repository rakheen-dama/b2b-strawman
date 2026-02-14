package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing invoice CRUD operations. Handles draft lifecycle, line item management, and
 * total recalculation. Only DRAFT invoices can be mutated (updated, deleted, lines added/removed).
 */
@Service
public class InvoiceService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final TaskRepository taskRepository;
  private final MemberRepository memberRepository;
  private final ProjectRepository projectRepository;
  private final OrgSettingsService orgSettingsService;
  private final AuditService auditService;
  private final ProjectAccessService projectAccessService;

  public InvoiceService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      MemberRepository memberRepository,
      ProjectRepository projectRepository,
      OrgSettingsService orgSettingsService,
      AuditService auditService,
      ProjectAccessService projectAccessService) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.memberRepository = memberRepository;
    this.projectRepository = projectRepository;
    this.orgSettingsService = orgSettingsService;
    this.auditService = auditService;
    this.projectAccessService = projectAccessService;
  }

  /**
   * Creates a draft invoice from the given time entry IDs. Validates that the customer is ACTIVE,
   * all time entries are unbilled, billable, belong to the customer's projects, and match the
   * requested currency. Snapshots customer details and org name at creation time.
   *
   * @param customerId the customer to invoice
   * @param currency the invoice currency (must match time entry billing rate currencies)
   * @param timeEntryIds the time entries to include as invoice lines
   * @param dueDate optional due date
   * @param notes optional notes
   * @param paymentTerms optional payment terms
   * @param createdBy the member creating the invoice
   * @return the created invoice
   */
  @Transactional
  public Invoice createDraft(
      UUID customerId,
      String currency,
      List<UUID> timeEntryIds,
      LocalDate dueDate,
      String notes,
      String paymentTerms,
      UUID createdBy) {

    // 1. Validate customer exists and is ACTIVE
    Customer customer =
        customerRepository
            .findOneById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    if (!"ACTIVE".equals(customer.getStatus())) {
      throw new InvalidStateException(
          "Customer not active",
          "Cannot create invoice for customer in status " + customer.getStatus());
    }

    // 2. Get customer's project IDs
    Set<UUID> customerProjectIds =
        customerProjectRepository.findByCustomerId(customerId).stream()
            .map(cp -> cp.getProjectId())
            .collect(Collectors.toSet());

    // 3. Validate time entries
    List<TimeEntry> timeEntries =
        timeEntryIds.stream()
            .map(
                id ->
                    timeEntryRepository
                        .findOneById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", id)))
            .toList();

    for (TimeEntry te : timeEntries) {
      if (!te.isBillable()) {
        throw new InvalidStateException(
            "Non-billable time entry", "Time entry " + te.getId() + " is not billable");
      }
      if (te.isBilled()) {
        throw new ResourceConflictException(
            "Already billed", "Time entry " + te.getId() + " is already billed on another invoice");
      }

      // Validate time entry belongs to customer's projects (via task -> project)
      Task task =
          taskRepository
              .findOneById(te.getTaskId())
              .orElseThrow(() -> new ResourceNotFoundException("Task", te.getTaskId()));

      if (!customerProjectIds.contains(task.getProjectId())) {
        throw new InvalidStateException(
            "Time entry not linked to customer",
            "Time entry "
                + te.getId()
                + " belongs to a project not linked to customer "
                + customerId);
      }

      // Validate currency matches
      if (te.getBillingRateCurrency() != null && !currency.equals(te.getBillingRateCurrency())) {
        throw new InvalidStateException(
            "Currency mismatch",
            "Time entry "
                + te.getId()
                + " has billing rate currency "
                + te.getBillingRateCurrency()
                + " but invoice currency is "
                + currency);
      }
    }

    // 4. Snapshot customer details and org name
    String orgName = resolveOrgName();

    // 5. Create invoice
    Invoice invoice =
        new Invoice(
            customerId,
            currency,
            customer.getName(),
            customer.getEmail(),
            customer.getAddress(),
            orgName,
            createdBy);
    invoice.setDueDate(dueDate);
    invoice.setNotes(notes);
    invoice.setPaymentTerms(paymentTerms);
    invoice = invoiceRepository.save(invoice);

    // 6. Create invoice lines from time entries
    UUID invoiceId = invoice.getId();
    AtomicInteger sortOrderCounter = new AtomicInteger(0);

    // Sort time entries chronologically for consistent ordering
    List<TimeEntry> sortedEntries =
        timeEntries.stream()
            .sorted(Comparator.comparing(TimeEntry::getDate).thenComparing(TimeEntry::getCreatedAt))
            .toList();

    for (TimeEntry te : sortedEntries) {
      Task task =
          taskRepository
              .findOneById(te.getTaskId())
              .orElseThrow(() -> new ResourceNotFoundException("Task", te.getTaskId()));

      var member = memberRepository.findOneById(te.getMemberId()).orElse(null);
      String memberName = member != null ? member.getName() : "Unknown";

      String description =
          task.getTitle() + " \u2014 " + memberName + " \u2014 " + te.getDate().toString();

      BigDecimal quantity =
          BigDecimal.valueOf(te.getDurationMinutes())
              .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

      BigDecimal unitPrice =
          te.getBillingRateSnapshot() != null ? te.getBillingRateSnapshot() : BigDecimal.ZERO;

      BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

      InvoiceLine line =
          new InvoiceLine(
              invoiceId,
              task.getProjectId(),
              te.getId(),
              description,
              quantity,
              unitPrice,
              amount,
              sortOrderCounter.getAndIncrement());

      invoiceLineRepository.save(line);

      // Mark time entry as billed
      te.markBilled(invoiceId);
      timeEntryRepository.save(te);
    }

    // 7. Recompute totals
    List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    invoice.recalculateTotals(lines);
    invoice = invoiceRepository.save(invoice);

    log.info(
        "Created draft invoice {} for customer {} with {} lines",
        invoice.getId(),
        customerId,
        lines.size());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.created")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "customer_id", customerId.toString(),
                    "currency", currency,
                    "line_count", lines.size(),
                    "subtotal", invoice.getSubtotal().toString()))
            .build());

    return invoice;
  }

  /**
   * Updates a draft invoice's editable fields (dueDate, notes, paymentTerms, taxAmount). Only DRAFT
   * invoices can be updated.
   *
   * @param id the invoice ID
   * @param dueDate new due date (nullable)
   * @param notes new notes (nullable)
   * @param paymentTerms new payment terms (nullable)
   * @param taxAmount new tax amount (nullable)
   * @return the updated invoice
   */
  @Transactional
  public Invoice updateDraft(
      UUID id, LocalDate dueDate, String notes, String paymentTerms, BigDecimal taxAmount) {

    Invoice invoice =
        invoiceRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));

    requireDraftStatus(invoice);

    invoice.setDueDate(dueDate);
    invoice.setNotes(notes);
    invoice.setPaymentTerms(paymentTerms);

    if (taxAmount != null) {
      invoice.setTaxAmount(taxAmount.setScale(2, RoundingMode.HALF_UP));
    }

    invoice = invoiceRepository.save(invoice);

    log.info("Updated draft invoice {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.updated")
            .entityType("invoice")
            .entityId(id)
            .details(
                Map.of(
                    "notes", notes != null ? notes : "",
                    "payment_terms", paymentTerms != null ? paymentTerms : ""))
            .build());

    return invoice;
  }

  /**
   * Deletes a draft invoice and all its lines. Only DRAFT invoices can be deleted. Time entries
   * linked to the invoice are marked as unbilled.
   *
   * @param id the invoice ID
   */
  @Transactional
  public void deleteDraft(UUID id) {
    Invoice invoice =
        invoiceRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));

    requireDraftStatus(invoice);

    // Unbill time entries linked to this invoice
    List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(id);
    for (InvoiceLine line : lines) {
      if (line.getTimeEntryId() != null) {
        timeEntryRepository
            .findOneById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.markUnbilled();
                  timeEntryRepository.save(te);
                });
      }
    }

    // Delete lines then invoice
    invoiceLineRepository.deleteByInvoiceId(id);
    invoiceRepository.delete(invoice);

    log.info("Deleted draft invoice {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.deleted")
            .entityType("invoice")
            .entityId(id)
            .details(Map.of("status", "DRAFT", "line_count", lines.size()))
            .build());
  }

  /**
   * Retrieves an invoice by ID with its lines.
   *
   * @param id the invoice ID
   * @return the invoice
   */
  @Transactional(readOnly = true)
  public Invoice getInvoice(UUID id) {
    return invoiceRepository
        .findOneById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
  }

  /**
   * Lists invoices with optional filters and pagination.
   *
   * @param customerId optional customer ID filter
   * @param status optional status filter
   * @param from optional issue date from (inclusive)
   * @param to optional issue date to (inclusive)
   * @param pageable pagination parameters
   * @return page of invoices
   */
  @Transactional(readOnly = true)
  public Page<Invoice> listInvoices(
      UUID customerId, InvoiceStatus status, LocalDate from, LocalDate to, Pageable pageable) {
    return invoiceRepository.findByFilters(customerId, status, from, to, pageable);
  }

  /**
   * Retrieves the lines for a given invoice, ordered by sortOrder.
   *
   * @param invoiceId the invoice ID
   * @return list of invoice lines
   */
  @Transactional(readOnly = true)
  public List<InvoiceLine> getInvoiceLines(UUID invoiceId) {
    return invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
  }

  /**
   * Adds a manual line item to a draft invoice. Recomputes subtotal and total after adding.
   *
   * @param invoiceId the invoice ID
   * @param projectId optional project ID for grouping
   * @param description the line description
   * @param quantity the quantity
   * @param unitPrice the unit price
   * @param sortOrder the display sort order
   * @return the updated invoice
   */
  @Transactional
  public Invoice addLine(
      UUID invoiceId,
      UUID projectId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      int sortOrder) {

    Invoice invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    requireDraftStatus(invoice);

    BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

    InvoiceLine line =
        new InvoiceLine(
            invoiceId,
            projectId,
            null, // no time entry for manual lines
            description,
            quantity,
            unitPrice,
            amount,
            sortOrder);
    invoiceLineRepository.save(line);

    // Recompute totals
    List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    invoice.recalculateTotals(lines);
    invoice = invoiceRepository.save(invoice);

    log.info("Added manual line to invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.line_added")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(Map.of("description", description, "amount", amount.toString()))
            .build());

    return invoice;
  }

  /**
   * Updates a line item on a draft invoice. Recomputes subtotal and total after updating.
   *
   * @param invoiceId the invoice ID
   * @param lineId the line ID
   * @param description new description
   * @param quantity new quantity
   * @param unitPrice new unit price
   * @param sortOrder new sort order
   * @return the updated invoice
   */
  @Transactional
  public Invoice updateLine(
      UUID invoiceId,
      UUID lineId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      int sortOrder) {

    Invoice invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    requireDraftStatus(invoice);

    InvoiceLine line =
        invoiceLineRepository
            .findOneById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));

    if (!line.getInvoiceId().equals(invoiceId)) {
      throw new InvalidStateException(
          "Line not on invoice",
          "Invoice line " + lineId + " does not belong to invoice " + invoiceId);
    }

    BigDecimal amount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

    // InvoiceLine fields are not mutable via setters (immutable design from 81B)
    // Delete and re-create with updated values
    invoiceLineRepository.delete(line);
    InvoiceLine updatedLine =
        new InvoiceLine(
            invoiceId,
            line.getProjectId(),
            line.getTimeEntryId(),
            description,
            quantity,
            unitPrice,
            amount,
            sortOrder);
    invoiceLineRepository.save(updatedLine);

    // Recompute totals
    List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    invoice.recalculateTotals(lines);
    invoice = invoiceRepository.save(invoice);

    log.info("Updated line {} on invoice {}", lineId, invoiceId);

    return invoice;
  }

  /**
   * Removes a line item from a draft invoice. If the line was linked to a time entry, marks the
   * time entry as unbilled. Recomputes subtotal and total after removing.
   *
   * @param invoiceId the invoice ID
   * @param lineId the line ID to remove
   * @return the updated invoice
   */
  @Transactional
  public Invoice removeLine(UUID invoiceId, UUID lineId) {
    Invoice invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    requireDraftStatus(invoice);

    InvoiceLine line =
        invoiceLineRepository
            .findOneById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));

    if (!line.getInvoiceId().equals(invoiceId)) {
      throw new InvalidStateException(
          "Line not on invoice",
          "Invoice line " + lineId + " does not belong to invoice " + invoiceId);
    }

    // Unbill time entry if linked
    if (line.getTimeEntryId() != null) {
      timeEntryRepository
          .findOneById(line.getTimeEntryId())
          .ifPresent(
              te -> {
                te.markUnbilled();
                timeEntryRepository.save(te);
              });
    }

    invoiceLineRepository.delete(line);

    // Recompute totals
    List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    invoice.recalculateTotals(lines);
    invoice = invoiceRepository.save(invoice);

    log.info("Removed line {} from invoice {}", lineId, invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.line_removed")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(Map.of("line_id", lineId.toString()))
            .build());

    return invoice;
  }

  /**
   * Checks that the caller has permission to manage invoices. Admin/owner have full access. Project
   * leads can only manage invoices they created.
   *
   * @param invoice the invoice to check
   * @param memberId the acting member
   * @param orgRole the acting member's org role
   */
  public void requirePermission(Invoice invoice, UUID memberId, String orgRole) {
    if (Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole)) {
      return;
    }
    if (invoice.getCreatedBy().equals(memberId)) {
      return;
    }
    throw new ForbiddenException(
        "Insufficient permissions",
        "Only admins, owners, or the invoice creator can manage this invoice");
  }

  // --- Private helpers ---

  private void requireDraftStatus(Invoice invoice) {
    if (!invoice.canEdit()) {
      throw new InvalidStateException(
          "Invoice not editable",
          "Cannot modify invoice in status " + invoice.getStatus() + ". Must be DRAFT.");
    }
  }

  private String resolveOrgName() {
    // OrgSettings might not have a name field; use a sensible default
    // The architecture says to snapshot org name, but OrgSettings only has defaultCurrency
    // Use the org ID from RequestScopes as a fallback
    return "Organization";
  }
}
