package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Dev-only controller that simulates a PSP checkout round-trip. Profile-gated to {@code local},
 * {@code dev}, {@code keycloak} — never reachable in {@code prod}. Pairs with {@link
 * MockPaymentGateway}.
 *
 * <p>Two endpoints:
 *
 * <ul>
 *   <li>{@code GET /portal/dev/mock-payment} — renders an order-summary page with two buttons
 *       ("Simulate Successful Payment" / "Simulate Failed Payment").
 *   <li>{@code POST /portal/dev/mock-payment/complete} — drives {@link
 *       PaymentWebhookService#processWebhook(String, String, Map)} synchronously to flip the
 *       invoice SENT→PAID and emit a payment event, then redirects back to the portal success URL.
 * </ul>
 */
@Controller
@Profile({"local", "dev", "keycloak"})
@RequestMapping("/portal/dev")
public class MockPaymentController {

  private static final Logger log = LoggerFactory.getLogger(MockPaymentController.class);
  private static final Pattern TENANT_SCHEMA_PATTERN = Pattern.compile("tenant_[a-f0-9]+");

  private final PaymentWebhookService paymentWebhookService;
  private final InvoiceRepository invoiceRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate transactionTemplate;

  public MockPaymentController(
      PaymentWebhookService paymentWebhookService,
      InvoiceRepository invoiceRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      TransactionTemplate transactionTemplate) {
    this.paymentWebhookService = paymentWebhookService;
    this.invoiceRepository = invoiceRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @GetMapping("/mock-payment")
  public String renderMockPayment(
      @RequestParam(name = "sessionId", required = false) String sessionId,
      @RequestParam(name = "invoiceId", required = false) String invoiceId,
      @RequestParam(name = "amount", required = false) String amount,
      @RequestParam(name = "currency", required = false) String currency,
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      Model model) {
    String tenantSchema = resolveTenantSchemaForInvoice(invoiceId);
    model.addAttribute("sessionId", sessionId == null ? "" : sessionId);
    model.addAttribute("invoiceId", invoiceId == null ? "" : invoiceId);
    model.addAttribute("amount", amount == null ? "0" : amount);
    model.addAttribute("currency", currency == null ? "" : currency);
    model.addAttribute("returnUrl", returnUrl == null ? "" : returnUrl);
    model.addAttribute("tenantSchema", tenantSchema == null ? "" : tenantSchema);
    return "portal/mock-payment";
  }

  @PostMapping("/mock-payment/complete")
  public String completeMockPayment(
      @RequestParam("sessionId") String sessionId,
      @RequestParam("invoiceId") String invoiceId,
      @RequestParam("status") String status,
      @RequestParam(name = "returnUrl", required = false) String returnUrl,
      @RequestParam(name = "tenantSchema", required = false) String tenantSchema) {

    if (tenantSchema == null || tenantSchema.isBlank()) {
      tenantSchema = resolveTenantSchemaForInvoice(invoiceId);
    }
    if (tenantSchema == null || !TENANT_SCHEMA_PATTERN.matcher(tenantSchema).matches()) {
      log.warn("MockPayment: refusing to complete — invalid/unresolved tenantSchema");
      return "redirect:" + safeReturnUrl(returnUrl);
    }

    String orgId =
        orgSchemaMappingRepository
            .findBySchemaName(tenantSchema)
            .map(m -> m.getClerkOrgId())
            .orElse(null);

    String payload =
        String.format(
            "{\"sessionId\":\"%s\",\"status\":\"%s\",\"reference\":\"MOCK-PAY-%s\",\"tenantSchema\":\"%s\",\"invoiceId\":\"%s\"}",
            escapeJson(sessionId),
            escapeJson(status == null ? "PAID" : status.toUpperCase()),
            UUID.randomUUID(),
            escapeJson(tenantSchema),
            escapeJson(invoiceId));

    final String finalTenantSchema = tenantSchema;
    final String finalOrgId = orgId;
    try {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, finalTenantSchema);
      if (finalOrgId != null) {
        carrier = carrier.where(RequestScopes.ORG_ID, finalOrgId);
      }
      carrier.run(() -> paymentWebhookService.processWebhook("mock", payload, Map.of()));
      log.info(
          "MockPayment: completed sessionId={} invoiceId={} status={}",
          sessionId,
          invoiceId,
          status);
    } catch (Exception e) {
      log.error("MockPayment: failed to process webhook for invoice {}", invoiceId, e);
    }

    return "redirect:" + safeReturnUrl(returnUrl);
  }

  /**
   * Looks up the tenant schema that owns the given invoice id. The {@code public.invoices} table is
   * not partitioned by tenant — instead, every {@code tenant_*} schema has its own {@code invoices}
   * table. We iterate registered schemas and probe each via a tenant-bound query. Only used in dev.
   */
  private String resolveTenantSchemaForInvoice(String invoiceIdStr) {
    if (invoiceIdStr == null || invoiceIdStr.isBlank()) {
      return null;
    }
    UUID invoiceId;
    try {
      invoiceId = UUID.fromString(invoiceIdStr);
    } catch (IllegalArgumentException e) {
      return null;
    }
    var mappings = orgSchemaMappingRepository.findAll();
    for (var mapping : mappings) {
      String schema = mapping.getSchemaName();
      if (!TENANT_SCHEMA_PATTERN.matcher(schema).matches()) {
        continue;
      }
      Optional<Invoice> found =
          ScopedValue.where(RequestScopes.TENANT_ID, schema)
              .where(RequestScopes.ORG_ID, mapping.getClerkOrgId())
              .call(() -> transactionTemplate.execute(tx -> invoiceRepository.findById(invoiceId)));
      if (found != null && found.isPresent()) {
        return schema;
      }
    }
    return null;
  }

  private static String safeReturnUrl(String returnUrl) {
    if (returnUrl == null || returnUrl.isBlank()) {
      return "/portal/dev/mock-payment";
    }
    // Allow http/https only; reject anything else (data:, javascript:, etc.)
    String lower = returnUrl.toLowerCase();
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return "/portal/dev/mock-payment";
    }
    return returnUrl;
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
