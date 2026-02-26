package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentReconciliationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes verified payment webhooks from external PSP providers. */
@Service
public class PaymentWebhookService {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

  private final IntegrationRegistry integrationRegistry;
  private final PaymentReconciliationService reconciliationService;

  public PaymentWebhookService(
      IntegrationRegistry integrationRegistry, PaymentReconciliationService reconciliationService) {
    this.integrationRegistry = integrationRegistry;
    this.reconciliationService = reconciliationService;
  }

  /**
   * Verifies and processes a payment webhook. Returns silently for unverified or non-actionable
   * webhooks. All verified webhooks are delegated to {@link PaymentReconciliationService}.
   */
  @Transactional
  public void processWebhook(String provider, String payload, Map<String, String> headers) {
    var gateway =
        integrationRegistry.resolveBySlug(
            IntegrationDomain.PAYMENT, provider, PaymentGateway.class);
    var result = gateway.handleWebhook(payload, headers);

    if (!result.verified()) {
      log.warn("Unverified {} webhook: eventType={}", provider, result.eventType());
      return;
    }

    reconciliationService.processWebhookResult(result, provider);
  }
}
