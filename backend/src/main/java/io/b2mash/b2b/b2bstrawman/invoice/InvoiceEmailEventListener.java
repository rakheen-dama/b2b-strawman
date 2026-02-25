package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvoiceEmailEventListener {

  private static final Logger log = LoggerFactory.getLogger(InvoiceEmailEventListener.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceEmailService invoiceEmailService;
  private final PdfRenderingService pdfRenderingService;
  private final DocumentTemplateRepository documentTemplateRepository;

  public InvoiceEmailEventListener(
      InvoiceRepository invoiceRepository,
      InvoiceEmailService invoiceEmailService,
      PdfRenderingService pdfRenderingService,
      DocumentTemplateRepository documentTemplateRepository) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceEmailService = invoiceEmailService;
    this.pdfRenderingService = pdfRenderingService;
    this.documentTemplateRepository = documentTemplateRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceSent(InvoiceSentEvent event) {
    if (event.tenantId() != null) {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, event.tenantId());
      if (event.orgId() != null) {
        carrier = carrier.where(RequestScopes.ORG_ID, event.orgId());
      }
      carrier.run(() -> handleInvoiceSent(event));
    } else {
      handleInvoiceSent(event);
    }
  }

  private void handleInvoiceSent(InvoiceSentEvent event) {
    try {
      // 1. Load invoice
      var invoice = invoiceRepository.findById(event.entityId()).orElse(null);
      if (invoice == null) {
        log.warn("Invoice not found for email delivery: {}", event.entityId());
        return;
      }

      // 2. Find invoice template and generate PDF
      var templateOpt =
          documentTemplateRepository
              .findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(TemplateEntityType.INVOICE)
              .stream()
              .findFirst();

      if (templateOpt.isEmpty()) {
        log.warn(
            "No invoice document template found, skipping PDF generation for invoice={}",
            event.entityId());
        return;
      }

      var pdfResult =
          pdfRenderingService.generatePdf(
              templateOpt.get().getId(), invoice.getId(), event.actorMemberId());

      // 3. Send email
      invoiceEmailService.sendInvoiceEmail(invoice, pdfResult.pdfBytes());

    } catch (Exception e) {
      log.error("Failed to send invoice email for invoice={}", event.entityId(), e);
    }
  }
}
