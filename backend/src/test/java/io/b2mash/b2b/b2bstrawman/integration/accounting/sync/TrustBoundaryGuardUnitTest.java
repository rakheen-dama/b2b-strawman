package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

/**
 * Unit tests for {@link TrustBoundaryGuard} covering paths that cannot be exercised in integration
 * tests (e.g. non-legal tenants where trust tables do not exist).
 */
@ExtendWith(MockitoExtension.class)
class TrustBoundaryGuardUnitTest {

  @Mock private DisbursementRepository disbursementRepository;
  @Mock private TrustLedgerQueryRepository trustLedgerQueryRepository;

  @InjectMocks private TrustBoundaryGuard trustBoundaryGuard;

  @Test
  void evaluate_permitsWhenTrustTablesDoNotExist() {
    // Simulate non-legal tenant: disbursement lookup throws DataAccessException
    when(disbursementRepository.findById(any()))
        .thenThrow(new DataAccessException("relation \"disbursements\" does not exist") {});

    // Derived balance lookup also throws (trust tables absent)
    when(trustLedgerQueryRepository.generalBalance(any()))
        .thenThrow(new DataAccessException("relation \"trust_transactions\" does not exist") {});

    var invoice = createTestInvoice();
    var line = createTestInvoiceLineWithDisbursement(invoice.getId());
    var customer = createTestCustomer();

    var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reason()).isNull();
  }

  @Test
  void evaluate_permitsWhenOnlyTrustTransactionTableMissing() {
    // Disbursement table exists but has no trust-linked entries, trust transaction table missing
    when(trustLedgerQueryRepository.generalBalance(any()))
        .thenThrow(new DataAccessException("relation \"trust_transactions\" does not exist") {});

    var invoice = createTestInvoice();
    var line = createTestInvoiceLine(invoice.getId());
    var customer = createTestCustomer();

    var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

    assertThat(decision.allowed()).isTrue();
  }

  private Invoice createTestInvoice() {
    return new Invoice(
        UUID.randomUUID(), "USD", "Unit Test Customer", null, null, "Test Org", UUID.randomUUID());
  }

  private InvoiceLine createTestInvoiceLine(UUID invoiceId) {
    return new InvoiceLine(
        invoiceId, null, null, "Standard service", BigDecimal.ONE, BigDecimal.TEN, 1);
  }

  private InvoiceLine createTestInvoiceLineWithDisbursement(UUID invoiceId) {
    var line =
        new InvoiceLine(
            invoiceId, null, null, "Disbursement item", BigDecimal.ONE, BigDecimal.TEN, 1);
    line.setDisbursementId(UUID.randomUUID());
    return line;
  }

  private Customer createTestCustomer() {
    return new Customer("Unit Test Customer", "unit@test.com", null, null, null, UUID.randomUUID());
  }
}
