package io.b2mash.b2b.b2bstrawman.integration.accounting;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@IntegrationAdapter(domain = IntegrationDomain.ACCOUNTING, slug = "noop")
public class NoOpAccountingProvider implements AccountingProvider {

  private static final Logger log = LoggerFactory.getLogger(NoOpAccountingProvider.class);

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public AccountingSyncResult syncInvoice(InvoiceSyncRequest request) {
    log.info(
        "NoOp accounting: would sync invoice {} for customer {}",
        request.invoiceNumber(),
        request.customerName());
    return new AccountingSyncResult(
        true, "NOOP-" + UUID.randomUUID().toString().substring(0, 8), null);
  }

  @Override
  public AccountingSyncResult syncCustomer(CustomerSyncRequest request) {
    log.info("NoOp accounting: would sync customer {}", request.customerName());
    return new AccountingSyncResult(
        true, "NOOP-" + UUID.randomUUID().toString().substring(0, 8), null);
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "noop", null);
  }
}
