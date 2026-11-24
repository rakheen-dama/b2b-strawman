package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.audit.SafeAuditDetails;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TaxContext;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 93 658A (architecture §17.3.10, ADR-390): the quiet issue of a brought-forward fee note — a
 * debt the previous system generated, numbered and sent. Callable only from {@code ..takeon..}
 * ({@code TakeOnEntryPointsArchTest}).
 *
 * <p>What it deliberately does NOT do, by construction (not by a flag): no {@code assignNumber}
 * (the firm's sequence is not used for a brought-forward fee note, FD-20 — though a source number
 * in the firm's own {@code INV-n} format moves it on so Kazi's own numbers continue after it,
 * D-253), no {@code InvoiceApprovedEvent} / {@code InvoiceSentEvent} (so no email, PDF,
 * notification, Xero push or automation), no {@code validateInvoiceSend}, no payment link, no
 * {@code INVOICE_GENERATION} prerequisite (FD-21), no tax rate on the line (the amount is the
 * VAT-inclusive balance, FD-26). It publishes only {@code InvoiceSyncEvent} "SENT" with no payment
 * URL (portal visibility, FD-9). Logs ids only.
 *
 * <p>The two audit rows are written here, not in {@link InvoiceCreationService} ({@code
 * MatterScopedPublisherGuardTest}); their details come only from {@link SafeAuditDetails} — no
 * amount, number or name.
 */
@Service
public class BroughtForwardFeeNoteService {

  private static final Logger log = LoggerFactory.getLogger(BroughtForwardFeeNoteService.class);

  /** One brought-forward fee note, typed by the take-on dry run (§17.3.10). */
  public record BroughtForwardFeeNote(
      UUID clientId,
      UUID matterId,
      String feeNoteNumber,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal outstandingAmount,
      String sourceRef,
      LocalDate asAt,
      String description,
      Map<String, Object> customFields,
      Map<String, Object> parkedColumns) {}

  /**
   * Why a client cannot take a fee note, as {@link InvoiceCreationService}'s own rule words it:
   * {@code notActive} when its legacy status is not ACTIVE, else its lifecycle refused.
   */
  public record ClientRefusal(boolean notActive, String message) {}

  /** The parked-columns note kept on the fee note (D-163, as on a taken-on client). */
  public static final String PARKED_KEY = "takeon_parked";

  static final DateTimeFormatter AS_AT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  /** X3: the one line's text, e.g. "Balance brought forward as at 31 October 2026 — fee note N". */
  public static String lineText(LocalDate asAt, String number) {
    return "Balance brought forward as at %s — fee note %s".formatted(AS_AT.format(asAt), number);
  }

  private final InvoiceCreationService creation;
  private final InvoiceNumberService numbers;
  private final CustomFieldValidator customFieldValidator;
  private final InvoiceRepository invoices;
  private final InvoiceLineRepository lines;
  private final InvoiceTaxService tax;
  private final TaxCalculationService taxCalculation;
  private final OrgSettingsRepository orgSettings;
  private final AuditService auditService;
  private final ApplicationEventPublisher events;

  public BroughtForwardFeeNoteService(
      InvoiceCreationService creation,
      InvoiceNumberService numbers,
      CustomFieldValidator customFieldValidator,
      InvoiceRepository invoices,
      InvoiceLineRepository lines,
      InvoiceTaxService tax,
      TaxCalculationService taxCalculation,
      OrgSettingsRepository orgSettings,
      AuditService auditService,
      ApplicationEventPublisher events) {
    this.creation = creation;
    this.numbers = numbers;
    this.customFieldValidator = customFieldValidator;
    this.invoices = invoices;
    this.lines = lines;
    this.tax = tax;
    this.taxCalculation = taxCalculation;
    this.orgSettings = orgSettings;
    this.auditService = auditService;
    this.events = events;
  }

  /**
   * The client checks every fee note needs (legacy ACTIVE status, lifecycle {@code
   * CREATE_INVOICE}), as a value: {@link InvoiceCreationService#requireInvoiceableClient}'s own
   * rule and text, read by the take-on dry run and commit without a throw (D-25).
   */
  public Optional<ClientRefusal> clientRefusal(Customer client) {
    return creation
        .invoiceableClientRefusal(client)
        .map(r -> new ClientRefusal(r.notActive(), r.exception().getBody().getDetail()));
  }

  /** The firm's currency, as {@code OrgSettingsService.getDefaultCurrency} reads it. */
  public String firmCurrency() {
    return orgSettings
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultCurrency)
        .orElse(OrgSettingsService.DEFAULT_CURRENCY);
  }

  /**
   * Issues one brought-forward fee note, SENT, in the caller's (the take-on row's) transaction
   * ({@code MANDATORY}). Throws the client checks' refusals ({@code ResourceNotFoundException},
   * {@code InvalidStateException}) and a custom-field refusal; the take-on commit re-checks the
   * client first so a row fails cleanly, and any other throw rolls the row back.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Invoice issue(BroughtForwardFeeNote spec, UUID takeOnBatchId, UUID actorMemberId) {
    // 1. Legacy ACTIVE status + lifecycle guard CREATE_INVOICE — NOT INVOICE_GENERATION (FD-21).
    var client = creation.requireInvoiceableClient(spec.clientId());

    // 2. The same customer snapshot createDraft takes, in the firm's currency.
    var invoice = creation.newDraft(client, firmCurrency(), actorMemberId);
    if (spec.description() != null) {
      invoice.updateDraft(null, spec.description(), null, null);
    }
    invoice.setCustomFields(customFields(spec));
    invoice = invoices.save(invoice); // DRAFT, no batch id yet: the CHECK holds

    // 3. One untaxed MANUAL line (FD-26) — never applyTaxToLine: it would apply the default rate.
    var line =
        new InvoiceLine(
            invoice.getId(),
            spec.matterId(),
            null,
            lineText(spec.asAt(), spec.feeNoteNumber()),
            BigDecimal.ONE,
            spec.outstandingAmount(),
            0);
    line.setLineType(InvoiceLineType.MANUAL);
    lines.save(line);
    tax.recalculateInvoiceTotals(invoice);

    // 4. A source number in the firm's own format: Kazi's own numbers continue after it (D-253).
    //    Before the numbered save, so the counter's row lock is held first — a concurrent approval
    //    waits and then numbers after it.
    InvoiceNumberService.sequenceValueOf(spec.feeNoteNumber()).ifPresent(numbers::advancePast);

    // 5. DRAFT → SENT in one transition: source number and dates, approver, take-on batch.
    invoice.issueBroughtForward(
        spec.feeNoteNumber(), spec.issueDate(), spec.dueDate(), takeOnBatchId, actorMemberId);
    invoice = invoices.save(invoice);

    // 6. Audit — ids and a token only.
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.created")
            // ADR-342: storage key, do not rename
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                SafeAuditDetails.builder()
                    .token("source", "TAKE_ON")
                    .put("take_on_batch_id", takeOnBatchId)
                    .build())
            .build());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.brought_forward_issued")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                SafeAuditDetails.builder()
                    .put("invoice_id", invoice.getId())
                    .put("take_on_batch_id", takeOnBatchId)
                    .build())
            .build());

    // 7. The portal fee note only: "SENT", no payment URL or session (FD-9).
    publishPortalSync(invoice);
    log.debug(
        "Issued brought-forward fee note {} in take-on batch {}", invoice.getId(), takeOnBatchId);
    return invoice;
  }

  /**
   * The custom fields validated at write time, as {@code TakenOnCustomerService} does; then the
   * parked columns as {@code takeon_parked} (after validation: the validator strips slugs it does
   * not know, and there is no field definition for the parked-columns note, D-163).
   */
  private Map<String, Object> customFields(BroughtForwardFeeNote spec) {
    Map<String, Object> validated =
        new LinkedHashMap<>(
            customFieldValidator.validate(
                EntityType.INVOICE,
                spec.customFields() != null ? spec.customFields() : new HashMap<>(),
                null));
    if (spec.parkedColumns() != null && !spec.parkedColumns().isEmpty()) {
      validated.put(PARKED_KEY, new LinkedHashMap<>(spec.parkedColumns()));
    }
    return validated;
  }

  /** The portal sync {@code InvoiceTransitionService.send} publishes, without a payment link. */
  private void publishPortalSync(Invoice invoice) {
    var invoiceLines = lines.findByInvoiceIdOrderBySortOrder(invoice.getId());
    var settings = orgSettings.findForCurrentTenant().orElse(null);
    var taxContext =
        new TaxContext(
            taxCalculation.buildTaxBreakdown(invoiceLines),
            settings != null ? settings.getTax().getTaxRegistrationNumber() : null,
            settings != null ? settings.getTax().getTaxRegistrationLabel() : null,
            settings != null ? settings.getTax().getTaxLabel() : null,
            settings != null && settings.getTax().isTaxInclusive(),
            taxCalculation.hasPerLineTax(invoiceLines));
    events.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            "SENT",
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getSubtotal(),
            invoice.getTaxAmount(),
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getNotes(),
            null,
            null,
            RequestScopes.getOrgIdOrNull(),
            RequestScopes.getTenantIdOrNull(),
            taxContext,
            invoice.getAmountPaid()));
  }
}
