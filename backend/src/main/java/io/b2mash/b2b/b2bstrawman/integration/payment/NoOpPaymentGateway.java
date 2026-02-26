package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-op payment gateway used as the default adapter when no PSP is configured. Records manual
 * payments with generated references and rejects online checkout operations.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "noop")
public class NoOpPaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(NoOpPaymentGateway.class);

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public PaymentResult recordManualPayment(PaymentRequest request) {
    String reference = "MANUAL-" + UUID.randomUUID();
    log.info(
        "NoOp payment: recorded manual payment for invoice={}, amount={} {}, reference={}",
        request.invoiceId(),
        request.amount(),
        request.currency(),
        reference);
    return new PaymentResult(true, reference, null);
  }

  @Override
  public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
    return CreateSessionResult.notSupported("NoOp adapter does not support online checkout");
  }

  @Override
  public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
    throw new UnsupportedOperationException("NoOp adapter does not support webhooks");
  }

  @Override
  public PaymentStatus queryPaymentStatus(String sessionId) {
    throw new UnsupportedOperationException("NoOp adapter does not support payment status queries");
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "noop", null);
  }
}
