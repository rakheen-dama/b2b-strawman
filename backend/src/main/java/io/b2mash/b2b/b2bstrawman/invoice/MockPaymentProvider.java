package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "mock")
public class MockPaymentProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

  @Override
  public PaymentResult recordPayment(PaymentRequest request) {
    String reference = "MOCK-PAY-" + UUID.randomUUID().toString().substring(0, 8);
    log.info(
        "Mock payment recorded: invoice={}, amount={} {}, reference={}",
        request.invoiceId(),
        request.amount(),
        request.currency(),
        reference);
    return new PaymentResult(true, reference, null);
  }
}
