package io.b2mash.b2b.b2bstrawman.integration.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoOpAccountingProviderTest {

  private final NoOpAccountingProvider provider = new NoOpAccountingProvider();

  @Test
  void providerId_returns_noop() {
    assertThat(provider.providerId()).isEqualTo("noop");
  }

  @Test
  void syncInvoice_returns_success_with_noop_reference() {
    var request =
        new InvoiceSyncRequest(
            "INV-001",
            "Acme Corp",
            List.of(new LineItem("Consulting", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO)),
            "ZAR",
            LocalDate.now(),
            LocalDate.now().plusDays(30));

    var result = provider.syncInvoice(request);

    assertThat(result.success()).isTrue();
    assertThat(result.externalReferenceId()).startsWith("NOOP-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void syncCustomer_returns_success_with_noop_reference() {
    var request =
        new CustomerSyncRequest(
            "Acme Corp", "billing@acme.com", "123 Main St", null, "Cape Town", "8001", "ZA");

    var result = provider.syncCustomer(request);

    assertThat(result.success()).isTrue();
    assertThat(result.externalReferenceId()).startsWith("NOOP-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_returns_success() {
    var result = provider.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.errorMessage()).isNull();
  }
}
