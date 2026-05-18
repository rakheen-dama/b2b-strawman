package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMapping;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.LineItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XeroInvoicePayloadMapperTest {

  private final XeroInvoicePayloadMapper mapper = new XeroInvoicePayloadMapper();

  @Test
  @SuppressWarnings("unchecked")
  void map_producesCorrectXeroJsonShape() {
    var request =
        new InvoiceSyncRequest(
            "INV-001",
            "Acme Corp",
            List.of(
                new LineItem(
                    "Consulting services",
                    BigDecimal.ONE,
                    new BigDecimal("1500.00"),
                    BigDecimal.ZERO,
                    "STANDARD_15"),
                new LineItem(
                    "Filing fees",
                    BigDecimal.ONE,
                    new BigDecimal("250.00"),
                    BigDecimal.ZERO,
                    "ZERO_RATED")),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            "KAZI-INV-abc123",
            "customer@example.com");

    List<AccountingTaxCodeMapping> taxMappings =
        List.of(
            new AccountingTaxCodeMapping("xero", "STANDARD_15", "OUTPUT2", "Standard Rate", true),
            new AccountingTaxCodeMapping(
                "xero", "ZERO_RATED", "ZERORATEDOUTPUT", "Zero Rated", false));

    Map<String, Object> payload = mapper.map(request, taxMappings);

    // Top-level fields
    assertThat(payload.get("Type")).isEqualTo("ACCREC");
    assertThat(payload.get("Status")).isEqualTo("AUTHORISED");
    assertThat(payload.get("Date")).isEqualTo("2026-05-18");
    assertThat(payload.get("DueDate")).isEqualTo("2026-06-17");
    assertThat(payload.get("Reference")).isEqualTo("KAZI-INV-abc123");

    // Contact
    var contact = (Map<String, Object>) payload.get("Contact");
    assertThat(contact.get("Name")).isEqualTo("Acme Corp");
    assertThat(contact.get("EmailAddress")).isEqualTo("customer@example.com");

    // Line items
    var lineItems = (List<Map<String, Object>>) payload.get("LineItems");
    assertThat(lineItems).hasSize(2);

    Map<String, Object> firstLine = lineItems.get(0);
    assertThat(firstLine.get("Description")).isEqualTo("Consulting services");
    assertThat(firstLine.get("Quantity")).isEqualTo(BigDecimal.ONE);
    assertThat(firstLine.get("UnitAmount")).isEqualTo(new BigDecimal("1500.00"));
    assertThat(firstLine.get("TaxType")).isEqualTo("OUTPUT2");

    Map<String, Object> secondLine = lineItems.get(1);
    assertThat(secondLine.get("Description")).isEqualTo("Filing fees");
    assertThat(secondLine.get("TaxType")).isEqualTo("ZERORATEDOUTPUT");
  }

  @Test
  @SuppressWarnings("unchecked")
  void map_resolvesKnownTaxCodesFromMappings() {
    var request =
        new InvoiceSyncRequest(
            "INV-002",
            "Client Corp",
            List.of(
                new LineItem(
                    "Exempt service",
                    BigDecimal.ONE,
                    new BigDecimal("500.00"),
                    BigDecimal.ZERO,
                    "EXEMPT"),
                new LineItem(
                    "Out of scope",
                    BigDecimal.ONE,
                    new BigDecimal("100.00"),
                    BigDecimal.ZERO,
                    "OUT_OF_SCOPE")),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            "KAZI-INV-def456",
            null);

    List<AccountingTaxCodeMapping> taxMappings =
        List.of(
            new AccountingTaxCodeMapping("xero", "EXEMPT", "EXEMPTOUTPUT", "Exempt Output", false),
            new AccountingTaxCodeMapping(
                "xero", "OUT_OF_SCOPE", "NONE", "No Tax / Out of Scope", false));

    Map<String, Object> payload = mapper.map(request, taxMappings);

    var lineItems = (List<Map<String, Object>>) payload.get("LineItems");
    assertThat(lineItems.get(0).get("TaxType")).isEqualTo("EXEMPTOUTPUT");
    assertThat(lineItems.get(1).get("TaxType")).isEqualTo("NONE");
  }

  @Test
  void map_includesExternalReferenceInReferenceField() {
    var request =
        new InvoiceSyncRequest(
            "INV-003",
            "Test Corp",
            List.of(
                new LineItem(
                    "Service", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, null)),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            "KAZI-INV-unique-ref",
            null);

    Map<String, Object> payload = mapper.map(request, List.of());

    assertThat(payload.get("Reference")).isEqualTo("KAZI-INV-unique-ref");
  }

  @Test
  @SuppressWarnings("unchecked")
  void map_defaultsToNoneForUnknownTaxMode() {
    var request =
        new InvoiceSyncRequest(
            "INV-004",
            "Unknown Tax Corp",
            List.of(
                new LineItem(
                    "Mystery service",
                    BigDecimal.ONE,
                    new BigDecimal("200.00"),
                    BigDecimal.ZERO,
                    "SOME_UNKNOWN_MODE")),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            null,
            null);

    List<AccountingTaxCodeMapping> taxMappings =
        List.of(
            new AccountingTaxCodeMapping("xero", "STANDARD_15", "OUTPUT2", "Standard Rate", true));

    Map<String, Object> payload = mapper.map(request, taxMappings);

    var lineItems = (List<Map<String, Object>>) payload.get("LineItems");
    assertThat(lineItems.getFirst().get("TaxType")).isEqualTo("NONE");
  }

  @Test
  @SuppressWarnings("unchecked")
  void map_defaultsToNoneForNullTaxMode() {
    var request =
        new InvoiceSyncRequest(
            "INV-005",
            "Null Tax Corp",
            List.of(
                new LineItem(
                    "No tax service",
                    BigDecimal.ONE,
                    new BigDecimal("300.00"),
                    BigDecimal.ZERO,
                    null)),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            null,
            null);

    Map<String, Object> payload = mapper.map(request, List.of());

    var lineItems = (List<Map<String, Object>>) payload.get("LineItems");
    assertThat(lineItems.getFirst().get("TaxType")).isEqualTo("NONE");
  }

  @Test
  @SuppressWarnings("unchecked")
  void map_omitsEmailAddressWhenNull() {
    var request =
        new InvoiceSyncRequest(
            "INV-006",
            "No Email Corp",
            List.of(
                new LineItem(
                    "Service", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, null)),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            null,
            null);

    Map<String, Object> payload = mapper.map(request, List.of());

    var contact = (Map<String, Object>) payload.get("Contact");
    assertThat(contact).doesNotContainKey("EmailAddress");
  }

  @Test
  void map_omitsReferenceWhenNull() {
    var request =
        new InvoiceSyncRequest(
            "INV-007",
            "No Ref Corp",
            List.of(
                new LineItem(
                    "Service", BigDecimal.ONE, new BigDecimal("100.00"), BigDecimal.ZERO, null)),
            "ZAR",
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 17),
            null,
            null);

    Map<String, Object> payload = mapper.map(request, List.of());

    assertThat(payload).doesNotContainKey("Reference");
  }
}
