package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment receipts as generated documents keyed to payment events (Phase 89 632B, ADR-364,
 * G88-F32).
 *
 * <p>One {@code payment-receipt-za} {@code GeneratedDocument} per COMPLETED payment event, linked
 * by {@code payment_events.receipt_generated_document_id}. The receipt number {@code
 * {invoiceNumber}/R{seq}} is stored, never derived (R6.2): it is taken from {@code
 * invoices.receipt_sequence} under the invoice row lock and never reused.
 *
 * <p>Locking (D11): every path takes the invoice row lock first, then the payment-event row lock —
 * the same order as {@code InvoiceTransitionService.postPayment} / {@code reversePayment} — and
 * both rows are first loaded <em>by</em> the locking query, so the persistence context never serves
 * a stale pre-lock copy. Under the locks the FK is re-read; when already set the existing document
 * is returned (R6.3), so a listener and a lazy call racing on one event produce one receipt.
 *
 * <p>No client email (ADR-350): {@code saveToDocuments = false}, {@code notifyClient = false},
 * {@code INTERNAL}. Actor (B2, §14.12 #4): the posting member when one is known, else the fee
 * note's creator ({@code generated_documents.generated_by} is NOT NULL) — recorded honestly as
 * {@code actor_source} on the {@code invoice.receipt_generated} audit row.
 */
@Service
public class PaymentReceiptService {

  private static final Logger log = LoggerFactory.getLogger(PaymentReceiptService.class);

  /** Audit {@code actor_source}: a member posted the payment / asked for the receipt. */
  public static final String ACTOR_POSTING_MEMBER = "posting_member";

  /**
   * Audit {@code actor_source}: no member (PSP webhook, Xero pull, portal) — the fee note creator.
   */
  public static final String ACTOR_INVOICE_CREATOR = "invoice_creator";

  static final String TRIGGER_LISTENER = "listener";
  static final String TRIGGER_LAZY = "lazy";

  // The receipt keys are not in VariableMetadataRegistry (D7) — the caller supplies their formats.
  private static final Map<String, String> FORMAT_HINTS =
      Map.of(
          "invoice.amountPaid", "currency",
          "invoice.balanceDue", "currency",
          "invoice.paidAt", "date",
          "payment.amount", "currency",
          "payment.balanceAfter", "currency",
          "payment.date", "date");

  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final GeneratedDocumentService generatedDocumentService;
  private final AuditService auditService;

  public PaymentReceiptService(
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      InvoiceLineRepository invoiceLineRepository,
      GeneratedDocumentService generatedDocumentService,
      AuditService auditService) {
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.generatedDocumentService = generatedDocumentService;
    this.auditService = auditService;
  }

  /**
   * Lazy path (portal receipt download, firm "Generate receipt"): returns the payment event's
   * receipt document id, generating it in the <em>caller's</em> transaction when the FK is null
   * (listener failed, receipt deleted, or a payment that predates Phase 89). Idempotent (R6.3).
   *
   * @param actorMemberId the requesting member, or {@code null} when there is none (the portal) —
   *     the fee note's creator is then the actor ({@code actor_source = invoice_creator})
   * @throws ResourceNotFoundException unknown fee note, a payment event of another fee note, or no
   *     receipt template installed
   * @throws InvalidStateException the payment event is not COMPLETED
   */
  @Transactional
  public UUID ensureReceipt(UUID invoiceId, UUID paymentEventId, UUID actorMemberId) {
    return generateLocked(
            invoiceId,
            paymentEventId,
            actorMemberId,
            actorMemberId != null ? ACTOR_POSTING_MEMBER : ACTOR_INVOICE_CREATOR,
            TRIGGER_LAZY,
            true)
        .orElseThrow(); // required=true never returns empty
  }

  /**
   * Listener path ({@link PaymentReceiptListener}, AFTER_COMMIT): an independent transaction — the
   * payment has already committed. Returns empty (WARN, no exception) when the tenant has no
   * receipt template (a non-legal tenant, or a pack not yet reconciled); every other failure throws
   * for the listener to log.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<UUID> generateForPaymentEvent(
      UUID invoiceId, UUID paymentEventId, UUID actorMemberId, String actorSource) {
    return generateLocked(
        invoiceId, paymentEventId, actorMemberId, actorSource, TRIGGER_LISTENER, false);
  }

  /**
   * Reversal (R6.2): a void receipt must not stay downloadable — deletes the receipt row in the
   * caller's (reversal) transaction and its stored object after commit. The sequence is untouched,
   * so the reversed receipt's number is never reissued.
   */
  @Transactional
  public void deleteReceiptDocument(UUID generatedDocumentId) {
    generatedDocumentService.deleteWithStoredObject(generatedDocumentId);
  }

  private Optional<UUID> generateLocked(
      UUID invoiceId,
      UUID paymentEventId,
      UUID actorMemberId,
      String actorSource,
      String trigger,
      boolean required) {
    Optional<DocumentTemplate> template = generatedDocumentService.resolveReceiptTemplate();
    if (template.isEmpty()) {
      if (required) {
        throw ResourceNotFoundException.withDetail(
            "Receipt unavailable",
            "The payment receipt template (payment-receipt-za) is not installed for this firm.");
      }
      log.warn(
          "No payment-receipt-za template for this tenant — receipt skipped (the lazy path will"
              + " generate it once the template exists): invoice={} paymentEvent={}",
          invoiceId,
          paymentEventId);
      return Optional.empty();
    }

    // Lock order invoice → payment event (D11); both loaded by the locking query itself.
    var invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId));
    var paymentEvent =
        paymentEventRepository
            .findByIdAndInvoiceIdForUpdate(paymentEventId, invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentEvent", paymentEventId));
    if (paymentEvent.getStatus() != PaymentEventStatus.COMPLETED) {
      throw new InvalidStateException(
          "Receipt unavailable", "Only a completed payment has a receipt.");
    }
    if (paymentEvent.getReceiptGeneratedDocumentId() != null) {
      return Optional.of(paymentEvent.getReceiptGeneratedDocumentId()); // R6.3
    }

    UUID actor = actorMemberId != null ? actorMemberId : invoice.getCreatedBy();
    String receiptNumber = invoice.nextReceiptNumber();
    var payment = paymentContext(invoice, paymentEvent, receiptNumber);
    var result =
        generatedDocumentService.generateDocument(
            template.get().getId(),
            invoiceId,
            false,
            true,
            List.of(),
            actor,
            Document.Visibility.INTERNAL,
            Map.of("payment", payment),
            FORMAT_HINTS,
            false);
    UUID documentId = result.generatedDocument().getId();
    paymentEvent.attachReceipt(documentId, receiptNumber);

    var details =
        AuditDetails.matterScoped(
            invoiceLineRepository.findSingleProjectIdOrNull(invoiceId), invoice.getCustomerId());
    details.put("invoice_number", invoice.getInvoiceNumber());
    details.put("payment_event_id", paymentEventId.toString());
    details.put("receipt_number", receiptNumber);
    details.put("generated_document_id", documentId.toString());
    details.put("generated_by", actor.toString());
    details.put("actor_source", actorSource);
    details.put("trigger", trigger);
    auditService.log(
        AuditEventBuilder.builder()
            // ADR-342: storage key, do not rename (additive Phase 89 key)
            .eventType("invoice.receipt_generated")
            .entityType("invoice")
            .entityId(invoiceId)
            .details(details)
            .build());
    log.info(
        "Generated payment receipt {} for invoice={} paymentEvent={} ({}, {})",
        receiptNumber,
        invoiceId,
        paymentEventId,
        trigger,
        actorSource);
    return Optional.of(documentId);
  }

  /**
   * {@code payment.*} for the receipt: this event's row plus its stored number. {@code
   * balanceAfter} = total − Σ COMPLETED amounts up to and including this event (oldest first,
   * tie-break by id — the same order as {@code invoice.payments[]}), floored at zero (D15).
   */
  private Map<String, Object> paymentContext(
      Invoice invoice, PaymentEvent paymentEvent, String receiptNumber) {
    BigDecimal paidUpToThis = BigDecimal.ZERO;
    for (var event :
        paymentEventRepository.findByInvoiceIdAndStatus(
            invoice.getId(), PaymentEventStatus.COMPLETED)) {
      if (isAtOrBefore(event, paymentEvent)) {
        paidUpToThis = paidUpToThis.add(event.getAmount());
      }
    }
    var payment = new LinkedHashMap<String, Object>();
    payment.put("id", paymentEvent.getId());
    payment.put("number", receiptNumber);
    payment.put("date", LocalDate.ofInstant(paymentEvent.getCreatedAt(), KaziTime.ZONE).toString());
    payment.put("methodLabel", PaymentMethod.labelOf(paymentEvent.getPaymentMethod()));
    payment.put("amount", paymentEvent.getAmount());
    payment.put("reference", paymentEvent.getPaymentReference());
    payment.put("balanceAfter", invoice.getTotal().subtract(paidUpToThis).max(BigDecimal.ZERO));
    return payment;
  }

  private static boolean isAtOrBefore(PaymentEvent candidate, PaymentEvent target) {
    int byTime = candidate.getCreatedAt().compareTo(target.getCreatedAt());
    return byTime < 0 || (byTime == 0 && candidate.getId().compareTo(target.getId()) <= 0);
  }
}
