package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.PrerequisiteNotMetException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupResolver;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementApprovalStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementBillingStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles invoice creation, line item CRUD, field group operations, and draft management. Extracted
 * from InvoiceService as a focused collaborator.
 */
@Service
public class InvoiceCreationService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceCreationService.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final OrganizationRepository organizationRepository;
  private final TaskRepository taskRepository;
  private final MemberNameResolver memberNameResolver;
  private final AuditService auditService;
  private final CustomerLifecycleGuard customerLifecycleGuard;
  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupService fieldGroupService;
  private final FieldGroupResolver fieldGroupResolver;
  private final OrgSettingsRepository orgSettingsRepository;
  private final ExpenseRepository expenseRepository;
  private final PrerequisiteService prerequisiteService;
  private final TariffItemRepository tariffItemRepository;
  private final VerticalModuleGuard verticalModuleGuard;
  private final InvoiceTaxService invoiceTaxService;
  private final InvoiceRenderingService invoiceRenderingService;
  private final BillingRateService billingRateService;
  private final DisbursementRepository disbursementRepository;
  private final DisbursementService disbursementService;
  private final TaxRateRepository taxRateRepository;

  public InvoiceCreationService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      TimeEntryRepository timeEntryRepository,
      OrganizationRepository organizationRepository,
      TaskRepository taskRepository,
      MemberNameResolver memberNameResolver,
      AuditService auditService,
      CustomerLifecycleGuard customerLifecycleGuard,
      CustomFieldValidator customFieldValidator,
      FieldGroupService fieldGroupService,
      FieldGroupResolver fieldGroupResolver,
      OrgSettingsRepository orgSettingsRepository,
      ExpenseRepository expenseRepository,
      PrerequisiteService prerequisiteService,
      TariffItemRepository tariffItemRepository,
      VerticalModuleGuard verticalModuleGuard,
      InvoiceTaxService invoiceTaxService,
      InvoiceRenderingService invoiceRenderingService,
      BillingRateService billingRateService,
      DisbursementRepository disbursementRepository,
      DisbursementService disbursementService,
      TaxRateRepository taxRateRepository) {
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.organizationRepository = organizationRepository;
    this.taskRepository = taskRepository;
    this.memberNameResolver = memberNameResolver;
    this.auditService = auditService;
    this.customerLifecycleGuard = customerLifecycleGuard;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupService = fieldGroupService;
    this.fieldGroupResolver = fieldGroupResolver;
    this.orgSettingsRepository = orgSettingsRepository;
    this.expenseRepository = expenseRepository;
    this.prerequisiteService = prerequisiteService;
    this.tariffItemRepository = tariffItemRepository;
    this.verticalModuleGuard = verticalModuleGuard;
    this.invoiceTaxService = invoiceTaxService;
    this.invoiceRenderingService = invoiceRenderingService;
    this.billingRateService = billingRateService;
    this.disbursementRepository = disbursementRepository;
    this.disbursementService = disbursementService;
    this.taxRateRepository = taxRateRepository;
  }

  @Transactional
  public InvoiceResponse createDraft(CreateInvoiceRequest request, UUID createdBy) {
    var customer = validateInvoicePrerequisites(request.customerId());

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
            null,
            organization.getName(),
            createdBy);

    // Set all draft fields in one call to avoid the 4-arg overload wiping new fields
    if (request.dueDate() != null
        || request.notes() != null
        || request.paymentTerms() != null
        || request.poNumber() != null
        || request.taxType() != null
        || request.billingPeriodStart() != null
        || request.billingPeriodEnd() != null) {
      validateBillingPeriod(request.billingPeriodStart(), request.billingPeriodEnd());
      invoice.updateDraft(
          request.dueDate(),
          request.notes(),
          request.paymentTerms(),
          null,
          request.poNumber(),
          request.taxType(),
          request.billingPeriodStart(),
          request.billingPeriodEnd());
    }

    invoice = invoiceRepository.save(invoice);

    var timeEntryIds = request.timeEntryIds();
    createTimeEntryLines(invoice, timeEntryIds, request.customerId(), request.currency());

    var expenseIds = request.expenseIds();
    int expSortOffset = timeEntryIds != null && !timeEntryIds.isEmpty() ? timeEntryIds.size() : 0;
    createExpenseLines(invoice, expenseIds, request.customerId(), expSortOffset);

    // Apply org-default tax rate to time + expense lines BEFORE creating disbursement lines —
    // disbursements have VAT-treatment-specific rates resolved per-line that must not be
    // clobbered by the default-tax pass.
    boolean hasDefaultTaxableLines =
        (timeEntryIds != null && !timeEntryIds.isEmpty())
            || (expenseIds != null && !expenseIds.isEmpty());
    if (hasDefaultTaxableLines) {
      invoiceTaxService.applyDefaultTaxToLines(invoice.getId());
    }

    // Disbursement lines come AFTER default-tax so their VAT-treatment-specific rates are
    // preserved (STANDARD_15 / ZERO_RATED_PASS_THROUGH / EXEMPT). markBilled is invoked from
    // the same @Transactional scope; a rollback here unbinds the disbursement.
    var disbursementIds = request.disbursementIds();
    int disbSortOffset = expSortOffset + (expenseIds != null ? expenseIds.size() : 0);
    createDisbursementLines(invoice, disbursementIds, request.customerId(), disbSortOffset);

    applyFieldGroups(invoice);

    invoiceTaxService.recalculateInvoiceTotals(invoice);
    invoice = invoiceRepository.save(invoice);

    log.info("Created draft invoice {} for customer {}", invoice.getId(), request.customerId());

    int totalLineCount =
        (timeEntryIds != null ? timeEntryIds.size() : 0)
            + (expenseIds != null ? expenseIds.size() : 0)
            + (disbursementIds != null ? disbursementIds.size() : 0);

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
                    String.valueOf(totalLineCount),
                    "subtotal",
                    invoice.getSubtotal().toString()))
            .build());

    return invoiceRenderingService.buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse updateDraft(UUID invoiceId, UpdateInvoiceRequest request) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (request.taxAmount() != null
        && lineRepository.existsByInvoiceIdAndTaxRateIdIsNotNull(invoiceId)) {
      throw new InvalidStateException(
          "Tax amount cannot be manually set",
          "Tax amount cannot be manually set when invoice lines have tax rates applied."
              + " Edit individual line tax rates instead.");
    }

    validateBillingPeriod(request.billingPeriodStart(), request.billingPeriodEnd());

    try {
      invoice.updateDraft(
          request.dueDate(),
          request.notes(),
          request.paymentTerms(),
          request.taxAmount(),
          request.poNumber(),
          request.taxType(),
          request.billingPeriodStart(),
          request.billingPeriodEnd());
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

    return invoiceRenderingService.buildResponse(invoice);
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

    // Unlink expense-backed lines — expense returns to UNBILLED so it can be invoiced again.
    for (var line : lines) {
      if (line.getExpenseId() != null) {
        expenseRepository
            .findById(line.getExpenseId())
            .ifPresent(
                expense -> {
                  try {
                    expense.unbill();
                  } catch (IllegalStateException e) {
                    log.warn(
                        "Could not unbill expense {} on draft delete {}: {}",
                        line.getExpenseId(),
                        invoiceId,
                        e.getMessage());
                  }
                });
      }
    }

    // Unlink disbursement-backed lines — reverts disbursement BILLED → UNBILLED so it can be
    // invoiced again. Runs in the same @Transactional as the draft delete.
    for (var line : lines) {
      if (line.getDisbursementId() != null) {
        try {
          disbursementService.unmarkBilled(line.getDisbursementId());
        } catch (ResourceConflictException e) {
          log.warn(
              "Could not unmark-billed disbursement {} on draft delete {}: {}",
              line.getDisbursementId(),
              invoiceId,
              e.getMessage());
        }
      }
    }

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

    InvoiceLine line;

    if (request.tariffItemId() != null) {
      verticalModuleGuard.requireModule("lssa_tariff");

      var tariffItem =
          tariffItemRepository
              .findById(request.tariffItemId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("TariffItem", request.tariffItemId()));

      Objects.requireNonNull(tariffItem.getAmount(), "Tariff item amount must not be null");

      String description =
          (request.description() != null && !request.description().isBlank())
              ? request.description()
              : tariffItem.getDescription();
      BigDecimal unitPrice =
          (request.unitPrice() != null) ? request.unitPrice() : tariffItem.getAmount();

      int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
      line =
          new InvoiceLine(
              invoiceId,
              request.projectId(),
              null,
              description,
              request.quantity(),
              unitPrice,
              sortOrder);
      line.setLineType(InvoiceLineType.TARIFF);
      line.setTariffItemId(request.tariffItemId());
      line.setLineSource("TARIFF");
    } else {
      if (request.description() == null || request.description().isBlank()) {
        throw new InvalidStateException(
            "Description required", "Manual line items require a description");
      }
      if (request.unitPrice() == null) {
        throw new InvalidStateException(
            "Unit price required", "Manual line items require a unit price");
      }
      int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
      line =
          new InvoiceLine(
              invoiceId,
              request.projectId(),
              null,
              request.description(),
              request.quantity(),
              request.unitPrice(),
              sortOrder);
      line.setLineType(InvoiceLineType.MANUAL);
    }

    line = lineRepository.save(line);

    invoiceTaxService.applyTaxToLine(line, request.taxRateId());
    lineRepository.save(line);

    invoiceTaxService.recalculateInvoiceTotals(invoice);
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
                    "line_type",
                    line.getLineType().name(),
                    "new_subtotal",
                    invoice.getSubtotal().toString(),
                    "new_total",
                    invoice.getTotal().toString()))
            .build());

    return invoiceRenderingService.buildResponse(invoice);
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

    int sortOrder = request.sortOrder() != null ? request.sortOrder() : line.getSortOrder();
    line.update(request.description(), request.quantity(), request.unitPrice(), sortOrder);

    if (request.taxRateId() != null) {
      invoiceTaxService.applyTaxToLine(line, request.taxRateId());
    } else if (Boolean.TRUE.equals(request.clearTaxRate())) {
      line.clearTaxRate();
    } else if (line.getTaxRateId() != null) {
      invoiceTaxService.applyTaxToLine(line, line.getTaxRateId());
    }
    lineRepository.save(line);

    invoiceTaxService.recalculateInvoiceTotals(invoice);
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

    return invoiceRenderingService.buildResponse(invoice);
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

    if (line.getTimeEntryId() != null) {
      timeEntryRepository
          .findById(line.getTimeEntryId())
          .ifPresent(
              te -> {
                te.setInvoiceId(null);
              });
    }

    // Unlink expense-backed line — expense returns to UNBILLED.
    if (line.getExpenseId() != null) {
      expenseRepository
          .findById(line.getExpenseId())
          .ifPresent(
              expense -> {
                try {
                  expense.unbill();
                } catch (IllegalStateException e) {
                  log.warn(
                      "Could not unbill expense {} on line delete {}: {}",
                      line.getExpenseId(),
                      lineId,
                      e.getMessage());
                }
              });
    }

    // Unlink disbursement-backed line — reverts BILLED → UNBILLED so the disbursement can be
    // invoiced again. Runs in the same @Transactional.
    if (line.getDisbursementId() != null) {
      try {
        disbursementService.unmarkBilled(line.getDisbursementId());
      } catch (ResourceConflictException e) {
        log.warn(
            "Could not unmark-billed disbursement {} on line delete {}: {}",
            line.getDisbursementId(),
            lineId,
            e.getMessage());
      }
    }

    lineRepository.delete(line);

    invoiceTaxService.recalculateInvoiceTotals(invoice);
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

  @Transactional
  public InvoiceResponse updateCustomFields(UUID invoiceId, Map<String, Object> customFields) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Custom fields can only be updated on draft invoices");
    }

    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.INVOICE,
            customFields != null ? customFields : new java.util.HashMap<>(),
            invoice.getAppliedFieldGroups());

    invoice.setCustomFields(validatedFields);
    invoice = invoiceRepository.save(invoice);

    log.info("Updated custom fields on invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.custom_fields_updated")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : ""))
            .build());

    return invoiceRenderingService.buildResponse(invoice);
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(
      UUID invoiceId, List<UUID> appliedFieldGroups) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ResourceConflictException(
          "Invoice not editable", "Field groups can only be updated on draft invoices");
    }

    appliedFieldGroups =
        fieldGroupResolver.resolveAndValidate(appliedFieldGroups, EntityType.INVOICE);

    invoice.setAppliedFieldGroups(appliedFieldGroups);
    invoiceRepository.save(invoice);

    log.info("Updated field groups on invoice {}", invoiceId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.field_groups_updated")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : ""))
            .build());

    return fieldGroupResolver.collectFieldDefinitions(appliedFieldGroups);
  }

  // --- Private helpers ---

  private void validateBillingPeriod(
      java.time.LocalDate billingPeriodStart, java.time.LocalDate billingPeriodEnd) {
    if (billingPeriodStart != null
        && billingPeriodEnd != null
        && billingPeriodEnd.isBefore(billingPeriodStart)) {
      throw new InvalidStateException(
          "Invalid billing period", "Billing period end date must not be before start date");
    }
  }

  io.b2mash.b2b.b2bstrawman.customer.Customer validateInvoicePrerequisites(UUID customerId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    if (!"ACTIVE".equals(customer.getStatus())) {
      throw new InvalidStateException(
          "Customer not active", "Customer must be in ACTIVE status to create an invoice");
    }

    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_INVOICE);

    var prerequisiteCheck =
        prerequisiteService.checkForContext(
            PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, customerId);
    if (!prerequisiteCheck.passed()) {
      throw new PrerequisiteNotMetException(prerequisiteCheck);
    }

    return customer;
  }

  private void createTimeEntryLines(
      Invoice invoice, List<UUID> timeEntryIds, UUID customerId, String currency) {
    if (timeEntryIds == null || timeEntryIds.isEmpty()) {
      return;
    }

    var linkedTimeEntries = new ArrayList<io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry>();
    int sortOrder = 0;

    for (UUID timeEntryId : timeEntryIds) {
      var timeEntry =
          timeEntryRepository
              .findById(timeEntryId)
              .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

      if (!timeEntry.isBillable()) {
        throw new InvalidStateException(
            "Time entry not billable", "Time entry " + timeEntryId + " is not marked as billable");
      }

      if (timeEntry.getInvoiceId() != null) {
        throw new ResourceConflictException(
            "Time entry already invoiced",
            "Time entry " + timeEntryId + " is already linked to an invoice");
      }

      if (timeEntry.getBillingRateCurrency() != null
          && !timeEntry.getBillingRateCurrency().equals(currency)) {
        throw new InvalidStateException(
            "Currency mismatch",
            "Time entry "
                + timeEntryId
                + " has currency "
                + timeEntry.getBillingRateCurrency()
                + " but invoice currency is "
                + currency);
      }

      if (timeEntry.getTaskId() == null) {
        throw new InvalidStateException(
            "Time entry has no task",
            "Time entry "
                + timeEntryId
                + " is not linked to a task and cannot be invoiced for a customer");
      }

      UUID projectId = null;
      var task = taskRepository.findById(timeEntry.getTaskId());
      if (task.isPresent()) {
        projectId = task.get().getProjectId();
        if (!customerProjectRepository.existsByCustomerIdAndProjectId(customerId, projectId)) {
          throw new InvalidStateException(
              "Time entry not linked to customer",
              "Time entry "
                  + timeEntryId
                  + " belongs to a project not linked to customer "
                  + customerId);
        }
      }

      String description = buildTimeEntryDescription(timeEntry);

      BigDecimal quantity =
          BigDecimal.valueOf(timeEntry.getDurationMinutes())
              .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
      BigDecimal unitPrice = timeEntry.getBillingRateSnapshot();
      if (unitPrice == null && projectId != null) {
        var resolved =
            billingRateService.resolveRate(timeEntry.getMemberId(), projectId, timeEntry.getDate());
        if (resolved.isPresent()) {
          unitPrice = resolved.get().hourlyRate();
          if (timeEntry.getBillingRateCurrency() == null) {
            // Currency mismatch check above already passed for non-null currencies,
            // but for null-currency entries we should validate the resolved currency
            String resolvedCurrency = resolved.get().currency();
            if (!resolvedCurrency.equals(currency)) {
              throw new InvalidStateException(
                  "Currency mismatch",
                  "Time entry "
                      + timeEntryId
                      + " resolves to currency "
                      + resolvedCurrency
                      + " but invoice currency is "
                      + currency);
            }
          }
        }
      }
      if (unitPrice == null) {
        unitPrice = BigDecimal.ZERO;
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
      line.setLineType(InvoiceLineType.TIME);
      lineRepository.save(line);
      linkedTimeEntries.add(timeEntry);
    }

    for (var timeEntry : linkedTimeEntries) {
      timeEntry.setInvoiceId(invoice.getId());
    }
    timeEntryRepository.saveAll(linkedTimeEntries);
  }

  private void createExpenseLines(
      Invoice invoice, List<UUID> expenseIds, UUID customerId, int sortOrderOffset) {
    if (expenseIds == null || expenseIds.isEmpty()) {
      return;
    }

    var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    BigDecimal orgMarkup =
        orgSettings != null ? orgSettings.getDefaultExpenseMarkupPercent() : null;
    int expSortOrder = sortOrderOffset;

    for (UUID expenseId : expenseIds) {
      var expense =
          expenseRepository
              .findById(expenseId)
              .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

      if (!expense.isBillable()) {
        throw new InvalidStateException(
            "Expense not billable", "Expense " + expenseId + " is not marked as billable");
      }
      if (expense.getInvoiceId() != null) {
        throw new ResourceConflictException(
            "Expense already invoiced",
            "Expense " + expenseId + " is already linked to an invoice");
      }
      if (!customerProjectRepository.existsByCustomerIdAndProjectId(
          customerId, expense.getProjectId())) {
        throw new InvalidStateException(
            "Expense not linked to customer",
            "Expense " + expenseId + " belongs to a project not linked to customer " + customerId);
      }

      BigDecimal billableAmount = expense.computeBillableAmount(orgMarkup);
      var line =
          new InvoiceLine(
              invoice.getId(),
              expense.getProjectId(),
              null,
              expense.getDescription() + " [" + expense.getCategory() + "]",
              BigDecimal.ONE,
              billableAmount,
              expSortOrder++);
      line.setExpenseId(expense.getId());
      line.setLineType(InvoiceLineType.EXPENSE);
      lineRepository.save(line);

      expense.markBilled(invoice.getId());
      expenseRepository.save(expense);
    }
  }

  /**
   * Creates DISBURSEMENT-source invoice lines from a list of APPROVED + UNBILLED legal
   * disbursements. Each disbursement:
   *
   * <ul>
   *   <li>Must be in {@code APPROVED} approval status and {@code UNBILLED} billing status.
   *   <li>Must belong to the invoice's customer.
   *   <li>Gets a VAT-treatment-specific tax rate applied via {@link
   *       #resolveTaxRateForVatTreatment(VatTreatment)}.
   *   <li>Is marked {@code BILLED} via {@link DisbursementService#markBilled(UUID, UUID)} inside
   *       the caller's {@code @Transactional} scope — a rollback unbinds the disbursement.
   * </ul>
   *
   * <p>See ADR-247 — parallel-path integration (no shared {@code Billable} interface).
   */
  private void createDisbursementLines(
      Invoice invoice, List<UUID> disbursementIds, UUID customerId, int sortOrderOffset) {
    if (disbursementIds == null || disbursementIds.isEmpty()) {
      return;
    }

    int sortOrder = sortOrderOffset;
    for (UUID disbursementId : disbursementIds) {
      var disbursement =
          disbursementRepository
              .findById(disbursementId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("LegalDisbursement", disbursementId));

      if (!DisbursementApprovalStatus.APPROVED.name().equals(disbursement.getApprovalStatus())) {
        throw new InvalidStateException(
            "Disbursement not approved",
            "Disbursement "
                + disbursementId
                + " is not APPROVED (current="
                + disbursement.getApprovalStatus()
                + ")");
      }
      if (!DisbursementBillingStatus.UNBILLED.name().equals(disbursement.getBillingStatus())) {
        throw new ResourceConflictException(
            "Disbursement already billed",
            "Disbursement "
                + disbursementId
                + " is not UNBILLED (current="
                + disbursement.getBillingStatus()
                + ")");
      }
      if (!disbursement.getCustomerId().equals(customerId)) {
        throw new InvalidStateException(
            "Disbursement not linked to customer",
            "Disbursement "
                + disbursementId
                + " belongs to customer "
                + disbursement.getCustomerId()
                + " but invoice customer is "
                + customerId);
      }

      VatTreatment vatTreatment = VatTreatment.valueOf(disbursement.getVatTreatment());
      TaxRate taxRate = resolveTaxRateForVatTreatment(vatTreatment);

      String description = formatDisbursementLineDescription(disbursement);

      var line =
          new InvoiceLine(
              invoice.getId(),
              disbursement.getProjectId(),
              null,
              description,
              BigDecimal.ONE,
              disbursement.getAmount(),
              sortOrder++);
      line.setDisbursementId(disbursement.getId());
      line.setLineSource("DISBURSEMENT");
      line.setLineType(InvoiceLineType.DISBURSEMENT);
      line = lineRepository.save(line);

      // Apply VAT-treatment-specific tax snapshot (sets taxRateId + taxAmount + tax_exempt etc.).
      invoiceTaxService.applyTaxToLine(line, taxRate.getId());
      lineRepository.save(line);

      // Mark billed — fires DisbursementBilledEvent and audit event. Joins outer @Transactional.
      disbursementService.markBilled(disbursement.getId(), line.getId());
    }
  }

  /**
   * Resolves the {@link TaxRate} to apply based on a disbursement's {@link VatTreatment}. See
   * phase67 architecture §67.3.3 — tax resolution lives in the invoice layer, not on the
   * disbursement entity.
   *
   * <ul>
   *   <li>{@code STANDARD_15} → the org's default active tax rate (expected to be VAT 15%).
   *   <li>{@code ZERO_RATED_PASS_THROUGH} → the single active, non-exempt tax rate with {@code
   *       rate=0.00}.
   *   <li>{@code EXEMPT} → the single active, exempt tax rate.
   * </ul>
   *
   * <p>For the catalogue-scan branches (ZERO_RATED and EXEMPT) we require <em>exactly one</em>
   * matching row; if the seed catalogue is ever extended with a second zero-rated or exempt row
   * (e.g. zero-rated exports) the resolution must fail loudly rather than silently pick one.
   */
  private TaxRate resolveTaxRateForVatTreatment(VatTreatment treatment) {
    return switch (treatment) {
      case STANDARD_15 ->
          taxRateRepository
              .findByIsDefaultTrue()
              .filter(TaxRate::isActive)
              .orElseThrow(
                  () ->
                      new InvalidStateException(
                          "No default tax rate",
                          "Cannot resolve STANDARD_15 tax rate — no active default TaxRate in this"
                              + " tenant"));
      case ZERO_RATED_PASS_THROUGH ->
          resolveExactlyOneTaxRate(
              r -> r.isActive() && r.getRate().compareTo(BigDecimal.ZERO) == 0 && !r.isExempt(),
              "No zero-rated tax rate",
              "No active zero-rated TaxRate configured for this tenant",
              "Ambiguous zero-rated tax rate",
              "Multiple active zero-rated TaxRates configured for this tenant — "
                  + "disbursement pass-through VAT resolution requires exactly one");
      case EXEMPT ->
          resolveExactlyOneTaxRate(
              r -> r.isActive() && r.isExempt(),
              "No exempt tax rate",
              "No active exempt TaxRate configured for this tenant",
              "Ambiguous exempt tax rate",
              "Multiple active exempt TaxRates configured for this tenant — "
                  + "disbursement EXEMPT VAT resolution requires exactly one");
    };
  }

  /**
   * Finds the single TaxRate matching {@code predicate}. Throws {@link InvalidStateException} when
   * zero or multiple rows match — catalogue misconfiguration must fail loudly rather than pick one
   * silently via ordering.
   */
  private TaxRate resolveExactlyOneTaxRate(
      java.util.function.Predicate<TaxRate> predicate,
      String noneTitle,
      String noneDetail,
      String ambiguousTitle,
      String ambiguousDetail) {
    var matches = taxRateRepository.findAllByOrderBySortOrder().stream().filter(predicate).toList();
    if (matches.isEmpty()) {
      throw new InvalidStateException(noneTitle, noneDetail);
    }
    if (matches.size() > 1) {
      throw new InvalidStateException(ambiguousTitle, ambiguousDetail);
    }
    return matches.get(0);
  }

  /**
   * Builds an LSSA-convention line description for a legal disbursement: {@code "{category-label}:
   * {description} ({supplier}, {incurred_date})"}. Example: {@code "Sheriff fees: Service on
   * defendant (Sheriff Edenvale, 2026-04-10)"}.
   */
  private String formatDisbursementLineDescription(LegalDisbursement d) {
    String categoryLabel = humanizeCategoryLabel(d.getCategory());
    return String.format(
        "%s: %s (%s, %s)",
        categoryLabel, d.getDescription(), d.getSupplierName(), d.getIncurredDate());
  }

  /** Turns an enum-style name like {@code "SHERIFF_FEES"} into {@code "Sheriff fees"}. */
  private String humanizeCategoryLabel(String enumName) {
    if (enumName == null || enumName.isEmpty()) {
      return "";
    }
    String[] parts = enumName.toLowerCase().split("_");
    StringBuilder out = new StringBuilder();
    out.append(Character.toUpperCase(parts[0].charAt(0))).append(parts[0].substring(1));
    for (int i = 1; i < parts.length; i++) {
      out.append(' ').append(parts[i]);
    }
    return out.toString();
  }

  private void applyFieldGroups(Invoice invoice) {
    var autoApplyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.INVOICE);
    if (!autoApplyIds.isEmpty()) {
      var merged =
          new ArrayList<>(
              invoice.getAppliedFieldGroups() != null
                  ? invoice.getAppliedFieldGroups()
                  : List.of());
      for (UUID autoId : autoApplyIds) {
        if (!merged.contains(autoId)) {
          merged.add(autoId);
        }
      }
      invoice.setAppliedFieldGroups(merged);
    }
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
