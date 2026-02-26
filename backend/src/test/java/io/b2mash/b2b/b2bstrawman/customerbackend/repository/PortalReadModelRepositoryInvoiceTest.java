package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalReadModelRepositoryInvoiceTest {

  @Autowired private PortalReadModelRepository repository;

  private static final String ORG_ID = "org_test_invoice_readmodel";
  private final UUID customerId = UUID.randomUUID();
  private final UUID otherCustomerId = UUID.randomUUID();

  @Test
  void upsertInvoiceInsertsAndUpdates() {
    UUID invoiceId = UUID.randomUUID();

    repository.upsertPortalInvoice(
        invoiceId,
        ORG_ID,
        customerId,
        "INV-001",
        "SENT",
        LocalDate.of(2026, 1, 15),
        LocalDate.of(2026, 2, 15),
        new BigDecimal("1000.00"),
        new BigDecimal("150.00"),
        new BigDecimal("1150.00"),
        "ZAR",
        "Original notes",
        null,
        null);

    var before = repository.findInvoiceById(invoiceId, ORG_ID);
    assertThat(before).isPresent();
    assertThat(before.get().invoiceNumber()).isEqualTo("INV-001");
    assertThat(before.get().status()).isEqualTo("SENT");
    assertThat(before.get().total()).isEqualByComparingTo("1150.00");

    // Upsert again with updated status
    repository.upsertPortalInvoice(
        invoiceId,
        ORG_ID,
        customerId,
        "INV-001",
        "PAID",
        LocalDate.of(2026, 1, 15),
        LocalDate.of(2026, 2, 15),
        new BigDecimal("1000.00"),
        new BigDecimal("150.00"),
        new BigDecimal("1150.00"),
        "ZAR",
        "Updated notes",
        null,
        null);

    var after = repository.findInvoiceById(invoiceId, ORG_ID);
    assertThat(after).isPresent();
    assertThat(after.get().status()).isEqualTo("PAID");
    assertThat(after.get().notes()).isEqualTo("Updated notes");
  }

  @Test
  void upsertInvoiceLines_andCascadeDeleteOnInvoiceDelete() {
    UUID invoiceId = UUID.randomUUID();
    UUID lineId1 = UUID.randomUUID();
    UUID lineId2 = UUID.randomUUID();

    // Insert invoice
    repository.upsertPortalInvoice(
        invoiceId,
        ORG_ID,
        customerId,
        "INV-002",
        "SENT",
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 3, 1),
        new BigDecimal("500.00"),
        BigDecimal.ZERO,
        new BigDecimal("500.00"),
        "ZAR",
        null,
        null,
        null);

    // Insert two lines
    repository.upsertPortalInvoiceLine(
        lineId1,
        invoiceId,
        "Line item 1",
        new BigDecimal("2.0000"),
        new BigDecimal("100.00"),
        new BigDecimal("200.00"),
        0);
    repository.upsertPortalInvoiceLine(
        lineId2,
        invoiceId,
        "Line item 2",
        new BigDecimal("3.0000"),
        new BigDecimal("100.00"),
        new BigDecimal("300.00"),
        1);

    // Verify lines exist
    var lines = repository.findInvoiceLinesByInvoice(invoiceId);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).sortOrder()).isEqualTo(0);
    assertThat(lines.get(1).sortOrder()).isEqualTo(1);

    // Delete invoice — cascade should remove lines
    repository.deletePortalInvoice(invoiceId, ORG_ID);

    var linesAfter = repository.findInvoiceLinesByInvoice(invoiceId);
    assertThat(linesAfter).isEmpty();

    var invoiceAfter = repository.findInvoiceById(invoiceId, ORG_ID);
    assertThat(invoiceAfter).isEmpty();
  }

  @Test
  void findInvoicesByCustomer_scopedByOrgAndCustomer() {
    UUID invoiceA = UUID.randomUUID();
    UUID invoiceB = UUID.randomUUID();

    // Insert invoice for customerId
    repository.upsertPortalInvoice(
        invoiceA,
        ORG_ID,
        customerId,
        "INV-010",
        "SENT",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        "ZAR",
        null,
        null,
        null);

    // Insert invoice for otherCustomerId
    repository.upsertPortalInvoice(
        invoiceB,
        ORG_ID,
        otherCustomerId,
        "INV-011",
        "SENT",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 1),
        new BigDecimal("200.00"),
        BigDecimal.ZERO,
        new BigDecimal("200.00"),
        "ZAR",
        null,
        null,
        null);

    // Query for customerId — should only return invoiceA
    var customerInvoices = repository.findInvoicesByCustomer(ORG_ID, customerId);
    assertThat(customerInvoices).anyMatch(i -> i.id().equals(invoiceA));
    assertThat(customerInvoices).noneMatch(i -> i.id().equals(invoiceB));

    // Query for otherCustomerId — should only return invoiceB
    var otherInvoices = repository.findInvoicesByCustomer(ORG_ID, otherCustomerId);
    assertThat(otherInvoices).anyMatch(i -> i.id().equals(invoiceB));
    assertThat(otherInvoices).noneMatch(i -> i.id().equals(invoiceA));
  }
}
