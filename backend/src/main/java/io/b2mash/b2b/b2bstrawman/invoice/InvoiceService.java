package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final CustomerRepository customerRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final OrganizationRepository organizationRepository;
  private final ProjectRepository projectRepository;
  private final TaskRepository taskRepository;
  private final MemberRepository memberRepository;
  private final InvoiceNumberService invoiceNumberService;
  private final PaymentProvider paymentProvider;

  public InvoiceService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      CustomerRepository customerRepository,
      TimeEntryRepository timeEntryRepository,
      OrganizationRepository organizationRepository,
      ProjectRepository projectRepository,
      TaskRepository taskRepository,
      MemberRepository memberRepository,
      InvoiceNumberService invoiceNumberService,
      PaymentProvider paymentProvider) {
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.customerRepository = customerRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.organizationRepository = organizationRepository;
    this.projectRepository = projectRepository;
    this.taskRepository = taskRepository;
    this.memberRepository = memberRepository;
    this.invoiceNumberService = invoiceNumberService;
    this.paymentProvider = paymentProvider;
  }

  @Transactional
  public InvoiceResponse createDraft(CreateInvoiceRequest request, UUID createdBy) {
    var customer =
        customerRepository
            .findOneById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    if (!"ACTIVE".equals(customer.getStatus())) {
      throw new InvalidStateException(
          "Customer not active", "Customer must be in ACTIVE status to create an invoice");
    }

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
                .findOneById(timeEntryId)
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

        // Build description: "{task title} -- {date} -- {member name}"
        String description = buildTimeEntryDescription(timeEntry);

        BigDecimal quantity =
            BigDecimal.valueOf(timeEntry.getDurationMinutes())
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal unitPrice =
            timeEntry.getBillingRateSnapshot() != null
                ? timeEntry.getBillingRateSnapshot()
                : BigDecimal.ZERO;

        // Look up task to get projectId
        UUID projectId = null;
        if (timeEntry.getTaskId() != null) {
          var task = taskRepository.findOneById(timeEntry.getTaskId());
          if (task.isPresent()) {
            projectId = task.get().getProjectId();
          }
        }

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
    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse updateDraft(UUID invoiceId, UpdateInvoiceRequest request) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.updateDraft(
          request.dueDate(), request.notes(), request.paymentTerms(), request.taxAmount());
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invoice not editable", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);
    log.info("Updated draft invoice {}", invoiceId);
    return buildResponse(invoice);
  }

  @Transactional
  public void deleteDraft(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
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
            .findOneById(teId)
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
  }

  @Transactional(readOnly = true)
  public InvoiceResponse findById(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
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
            .findOneById(invoiceId)
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
    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse updateLineItem(
      UUID invoiceId, UUID lineId, UpdateLineItemRequest request) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Line items can only be updated on draft invoices");
    }

    var line =
        lineRepository
            .findOneById(lineId)
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
    return buildResponse(invoice);
  }

  @Transactional
  public void deleteLineItem(UUID invoiceId, UUID lineId) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Line items can only be removed from draft invoices");
    }

    var line =
        lineRepository
            .findOneById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", lineId));

    if (!line.getInvoiceId().equals(invoiceId)) {
      throw new ResourceNotFoundException("InvoiceLine", lineId);
    }

    // Unlink time entry if this line was generated from one
    if (line.getTimeEntryId() != null) {
      timeEntryRepository
          .findOneById(line.getTimeEntryId())
          .ifPresent(
              te -> {
                te.setInvoiceId(null);
              });
    }

    lineRepository.delete(line);

    recalculateInvoiceTotals(invoice);
    invoiceRepository.save(invoice);

    log.info("Deleted line item {} from invoice {}", lineId, invoiceId);
  }

  // --- Lifecycle transitions ---

  @Transactional
  public InvoiceResponse approve(UUID invoiceId, UUID approvedBy) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
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
                .findOneById(line.getTimeEntryId())
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
    String tenantId = RequestScopes.TENANT_ID.get();
    String invoiceNumber = invoiceNumberService.assignNumber(tenantId);

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
            .findOneById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(invoiceId);
                  timeEntryRepository.save(te);
                });
      }
    }

    log.info("Approved invoice {} with number {}", invoiceId, invoiceNumber);
    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse send(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.markSent();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);
    log.info("Marked invoice {} as sent", invoiceId);
    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse recordPayment(UUID invoiceId, String paymentReference) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
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
    return buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse voidInvoice(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findOneById(invoiceId)
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
            .findOneById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(null);
                  timeEntryRepository.save(te);
                });
      }
    }

    log.info("Voided invoice {}", invoiceId);
    return buildResponse(invoice);
  }

  // --- Private helpers ---

  private void recalculateInvoiceTotals(Invoice invoice) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    BigDecimal subtotal =
        lines.stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    invoice.recalculateTotals(subtotal);
  }

  private InvoiceResponse buildResponse(Invoice invoice) {
    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());

    // Batch-fetch project names for enrichment (single query instead of N+1)
    var projectIds =
        lines.stream().map(InvoiceLine::getProjectId).filter(Objects::nonNull).distinct().toList();

    Map<UUID, String> projectNames;
    if (!projectIds.isEmpty()) {
      projectNames =
          projectRepository.findAllByIds(projectIds).stream()
              .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
    } else {
      projectNames = Map.of();
    }

    var lineResponses =
        lines.stream()
            .map(
                line ->
                    InvoiceLineResponse.from(
                        line,
                        line.getProjectId() != null ? projectNames.get(line.getProjectId()) : null))
            .toList();

    return InvoiceResponse.from(invoice, lineResponses);
  }

  private String buildTimeEntryDescription(
      io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry timeEntry) {
    String taskTitle = "Untitled";
    String memberName = "Unknown";

    if (timeEntry.getTaskId() != null) {
      taskTitle =
          taskRepository
              .findOneById(timeEntry.getTaskId())
              .map(t -> t.getTitle())
              .orElse("Untitled");
    }

    if (timeEntry.getMemberId() != null) {
      memberName =
          memberRepository
              .findOneById(timeEntry.getMemberId())
              .map(m -> m.getName())
              .orElse("Unknown");
    }

    return taskTitle + " -- " + timeEntry.getDate() + " -- " + memberName;
  }
}
