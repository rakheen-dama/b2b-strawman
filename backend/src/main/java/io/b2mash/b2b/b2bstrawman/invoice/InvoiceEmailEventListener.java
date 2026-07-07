package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.template.PdfResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InvoiceEmailEventListener {

  private static final Logger log = LoggerFactory.getLogger(InvoiceEmailEventListener.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceEmailService invoiceEmailService;
  private final GeneratedDocumentService generatedDocumentService;
  private final TransactionTemplate requiresNewTransactionTemplate;

  public InvoiceEmailEventListener(
      InvoiceRepository invoiceRepository,
      InvoiceEmailService invoiceEmailService,
      GeneratedDocumentService generatedDocumentService,
      PlatformTransactionManager transactionManager) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceEmailService = invoiceEmailService;
    this.generatedDocumentService = generatedDocumentService;
    // The listener runs AFTER_COMMIT, where the originating transaction is already complete. A
    // plain (REQUIRES) transaction started here does not commit independently, so the persisted
    // GeneratedDocument would never reach the database. REQUIRES_NEW forces a fresh, independently
    // committed transaction — the same pattern NotificationService uses for AFTER_COMMIT writes.
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceSent(InvoiceSentEvent event) {
    if (event.tenantId() == null) {
      log.warn("InvoiceSentEvent has null tenantId, dropping: invoice={}", event.entityId());
      return;
    }
    RequestScopes.runForTenant(event.tenantId(), event.orgId(), () -> handleInvoiceSent(event));
  }

  private void handleInvoiceSent(InvoiceSentEvent event) {
    try {
      // 1. Load invoice
      var invoice = invoiceRepository.findById(event.entityId()).orElse(null);
      if (invoice == null) {
        log.warn("Invoice not found for email delivery: {}", event.entityId());
        return;
      }

      // 2. Find invoice template and generate PDF.
      // LZKC-012 — the vertical-profile-aware default: legal-za fee-note-za / accounting-za
      // invoice-za, i.e. the line-item client document. The entire fallback chain (including the
      // first-active-INVOICE-template last resort for non-legal-za tenants) lives inside
      // resolveDefaultInvoiceTemplate so that a legal-za tenant whose fee-note template is
      // unavailable resolves EMPTY here — never the generic cover letter, which was the LZKC-012
      // bug (the client received a cover letter instead of a real fee note).
      var templateOpt = generatedDocumentService.resolveDefaultInvoiceTemplate();

      if (templateOpt.isEmpty()) {
        // Degrade gracefully: still deliver the invoice email (it carries the portal link the
        // client pays through) — just without a PDF attachment, rather than attaching the wrong
        // document or silently dropping the email.
        log.warn(
            "No suitable invoice document template found, sending invoice email without PDF"
                + " attachment for invoice={}",
            event.entityId());
        invoiceEmailService.sendInvoiceEmail(invoice, null);
        return;
      }

      // OBS-3001 — persist the fee-note PDF through the canonical GeneratedDocument pipeline
      // (renders PDF, uploads to S3, persists a GeneratedDocument row) rather than rendering an
      // ephemeral PDF only for the email. Without a persisted GeneratedDocument the portal
      // download endpoint (PortalReadModelService.getInvoiceDownloadUrl) finds nothing and 404s.
      // saveToDocuments=false: the portal invoice download resolves directly off the
      // GeneratedDocument row, so no firm-side Document / portal-documents projection is needed
      // (and we avoid leaking the fee note into those surfaces). acknowledgeWarnings=true: the
      // invoice already passed firm-side validation at send time — email delivery must not be
      // blocked on optional-field warnings.
      //
      // The generation runs in a REQUIRES_NEW transaction so the row is durably committed even
      // though we are in an AFTER_COMMIT callback. Email delivery happens outside that transaction
      // (and is itself best-effort) so a transient SMTP failure cannot roll back the persisted PDF.
      var templateId = templateOpt.get().getId();
      var invoiceId = invoice.getId();
      PdfResult pdfResult =
          requiresNewTransactionTemplate.execute(
              tx ->
                  generatedDocumentService
                      .generateDocument(
                          templateId, invoiceId, false, true, List.of(), event.actorMemberId())
                      .pdfResult());

      // 3. Send email with the same rendered PDF that was persisted
      invoiceEmailService.sendInvoiceEmail(invoice, pdfResult.pdfBytes());

    } catch (Exception e) {
      log.error("Failed to send invoice email for invoice={}", event.entityId(), e);
    }
  }
}
