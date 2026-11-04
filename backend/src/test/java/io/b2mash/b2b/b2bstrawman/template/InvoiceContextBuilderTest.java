package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentPosting;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentReceiptService;
import io.b2mash.b2b.b2bstrawman.invoice.TaxType;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceContextBuilderTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceLineRepository invoiceLineRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private TemplateContextHelper contextHelper;
  // Phase 89 632B (D22): constructor arg — unstubbed it returns no payments, so every older case
  // sees an empty invoice.payments list.
  @Mock private PaymentEventRepository paymentEventRepository;
  // Only for the receipt/context agreement case (not constructor args of the builder).
  @Mock private GeneratedDocumentService generatedDocumentService;
  @Mock private AuditService auditService;

  @InjectMocks private InvoiceContextBuilder builder;

  private final UUID invoiceId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient()
        .when(contextHelper.resolveDropdownLabels(any(), any(EntityType.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void supportsInvoiceEntityType() {
    assertThat(builder.supports()).isEqualTo(TemplateEntityType.INVOICE);
  }

  @Test
  void buildContextWithProjectAndLines() {
    var invoice =
        new Invoice(customerId, "USD", "Acme Corp", "acme@test.com", null, "Test Org", memberId);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

    var projectId = UUID.randomUUID();
    var line =
        new InvoiceLine(
            invoiceId,
            projectId,
            null,
            "Consulting",
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(150),
            1);
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId))
        .thenReturn(List.of(line));

    var customer = TestCustomerFactory.createActiveCustomer("Acme Corp", "acme@test.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    var project = new Project("Project X", "desc", memberId);
    project.setReferenceNumber("PRJ-001");
    project.setPriority(ProjectPriority.HIGH);
    project.setWorkType("engagement");
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    assertThat(context).containsKey("invoice");
    assertThat(context).containsKey("lines");
    assertThat(context).containsKey("customer");
    assertThat(context).containsKey("project");

    @SuppressWarnings("unchecked")
    var invoiceMap = (Map<String, Object>) context.get("invoice");
    assertThat(invoiceMap.get("currency")).isEqualTo("USD");

    @SuppressWarnings("unchecked")
    var lines = (List<Map<String, Object>>) context.get("lines");
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().get("description")).isEqualTo("Consulting");
    assertThat(lines.getFirst().get("quantity")).isEqualTo(BigDecimal.valueOf(10));

    @SuppressWarnings("unchecked")
    var projectMap = (Map<String, Object>) context.get("project");
    assertThat(projectMap).isNotNull();
    assertThat(projectMap.get("name")).isEqualTo("Project X");
    // Promoted project fields (Epic 462) — advertised by VariableMetadataRegistry for invoices.
    assertThat(projectMap.get("referenceNumber")).isEqualTo("PRJ-001");
    assertThat(projectMap.get("priority")).isEqualTo("high");
    assertThat(projectMap.get("workType")).isEqualTo("ENGAGEMENT");
  }

  @Test
  void buildContextWithoutProject() {
    var invoice =
        new Invoice(customerId, "ZAR", "Solo Corp", "solo@test.com", null, "Org", memberId);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

    // Line with no projectId
    var line =
        new InvoiceLine(
            invoiceId, null, null, "Ad-hoc Work", BigDecimal.ONE, BigDecimal.valueOf(200), 1);
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId))
        .thenReturn(List.of(line));

    var customer = TestCustomerFactory.createActiveCustomer("Solo Corp", "solo@test.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    assertThat(context.get("project")).isNull();
  }

  @Test
  void buildContextWithCustomerCustomFields() {
    var invoice =
        new Invoice(customerId, "EUR", "Fields Corp", "f@test.com", null, "Org", memberId);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId)).thenReturn(List.of());

    var customer = TestCustomerFactory.createActiveCustomer("Fields Corp", "f@test.com", memberId);
    customer.setCustomFields(Map.of("vat_number", "VAT123"));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");
    assertThat(customFields).containsEntry("vat_number", "VAT123");
  }

  @Test
  void buildContextExposesPromotedInvoiceFieldsAsDirectVariables() {
    var invoice =
        new Invoice(customerId, "ZAR", "Promoted Co", "p@test.com", null, "Org", memberId);
    invoice.setPoNumber("PO-12345");
    invoice.setTaxType(TaxType.VAT);
    invoice.setBillingPeriodStart(LocalDate.of(2026, 1, 1));
    invoice.setBillingPeriodEnd(LocalDate.of(2026, 1, 31));

    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId)).thenReturn(List.of());

    var customer = TestCustomerFactory.createActiveCustomer("Promoted Co", "p@test.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    @SuppressWarnings("unchecked")
    var invoiceMap = (Map<String, Object>) context.get("invoice");

    // Direct promoted variables (Epic 460).
    assertThat(invoiceMap.get("poNumber")).isEqualTo("PO-12345");
    assertThat(invoiceMap.get("taxType")).isEqualTo("VAT");
    assertThat(invoiceMap.get("billingPeriodStart")).isEqualTo("2026-01-01");
    assertThat(invoiceMap.get("billingPeriodEnd")).isEqualTo("2026-01-31");

    // Backward-compat aliases. TaxType is lowercased to match pre-Phase-63 pack values.
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) invoiceMap.get("customFields");
    assertThat(customFields).containsEntry("purchase_order_number", "PO-12345");
    assertThat(customFields).containsEntry("tax_type", "vat");
    assertThat(customFields).containsEntry("billing_period_start", "2026-01-01");
    assertThat(customFields).containsEntry("billing_period_end", "2026-01-31");
  }

  @Test
  void buildContextExposesCustomerVatNumberViaPromotedTaxNumber() {
    // Verifies Epic 462's customerVatNumber top-level convenience prefers the promoted
    // structural customer.taxNumber column over the JSONB fallback.
    var invoice = new Invoice(customerId, "ZAR", "Acme Co", "acme@test.com", null, "Org", memberId);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId)).thenReturn(List.of());

    var customer = TestCustomerFactory.createActiveCustomer("Acme Co", "acme@test.com", memberId);
    customer.setTaxNumber("VAT-777");
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    assertThat(context.get("customerVatNumber")).isEqualTo("VAT-777");

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    assertThat(customerMap.get("taxNumber")).isEqualTo("VAT-777");

    // Backward-compat alias.
    @SuppressWarnings("unchecked")
    var customerCustomFields = (Map<String, Object>) customerMap.get("customFields");
    assertThat(customerCustomFields).containsEntry("tax_number", "VAT-777");
    assertThat(customerCustomFields).containsEntry("vat_number", "VAT-777");
  }

  @Test
  void buildContextFallsBackToLegacyVatNumberWhenPromotedColumnIsNull() {
    // Pre-Phase-63: no structural taxNumber, only JSONB vat_number.
    var invoice = new Invoice(customerId, "ZAR", "Legacy Co", "l@test.com", null, "Org", memberId);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId)).thenReturn(List.of());

    var customer = TestCustomerFactory.createActiveCustomer("Legacy Co", "l@test.com", memberId);
    customer.setCustomFields(Map.of("vat_number", "LEGACY-VAT"));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(invoiceId, memberId);

    assertThat(context.get("customerVatNumber")).isEqualTo("LEGACY-VAT");
  }

  @Test
  void throwsWhenInvoiceNotFound() {
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(invoiceId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- Phase 89 632B: payment keys (ADR-364) ---

  @Test
  @SuppressWarnings("unchecked")
  void paymentKeys_amountPaidBalanceDuePaidAtInSaDate_andPaymentsOldestFirstWithBalanceAfter() {
    var invoice = paidInvoice(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
    // 23:30 UTC on 4 Nov is 01:30 on 5 Nov in Johannesburg — the business date is the SA date.
    TestIds.withField(invoice, "paidAt", Instant.parse("2026-11-04T23:30:00Z"));
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    var second =
        event(
            "600.00", PaymentMethod.CASH, "CASH-2", Instant.parse("2026-11-04T23:30:00Z"), "0002");
    var first =
        event("400.00", PaymentMethod.EFT, "FNB-1", Instant.parse("2026-11-01T08:00:00Z"), "0001");
    // repository order is unspecified — the builder sorts by created_at ascending
    when(paymentEventRepository.findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED))
        .thenReturn(List.of(second, first));

    var invoiceMap = (Map<String, Object>) builder.buildContext(invoiceId, memberId).get("invoice");

    assertThat((BigDecimal) invoiceMap.get("amountPaid")).isEqualByComparingTo("1000.00");
    assertThat((BigDecimal) invoiceMap.get("balanceDue")).isEqualByComparingTo("0");
    assertThat(invoiceMap.get("paidAt")).isEqualTo("2026-11-05");
    var payments = (List<Map<String, Object>>) invoiceMap.get("payments");
    assertThat(payments).hasSize(2);
    assertThat(payments.get(0))
        .containsEntry("date", "2026-11-01")
        .containsEntry("methodLabel", "EFT")
        .containsEntry("reference", "FNB-1")
        .containsEntry("id", first.getId());
    assertThat((BigDecimal) payments.get(0).get("amount")).isEqualByComparingTo("400.00");
    assertThat((BigDecimal) payments.get(0).get("balanceAfter")).isEqualByComparingTo("600.00");
    assertThat(payments.get(1))
        .containsEntry("date", "2026-11-05")
        .containsEntry("methodLabel", "Cash")
        .containsEntry("reference", "CASH-2");
    assertThat((BigDecimal) payments.get(1).get("balanceAfter")).isEqualByComparingTo("0.00");
  }

  @Test
  @SuppressWarnings("unchecked")
  void paymentKeys_overpaymentClampsBalanceAfterAtZero_nullMethodIsPayment_unpaidHasNoPaidAt() {
    var invoice = paidInvoice(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    // An external source (card / Xero / trust transfer) records the real, larger amount (ADR-347).
    var over =
        event(
            "1500.00",
            PaymentMethod.TRUST_TRANSFER,
            "FT-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            "0001");
    var legacy = event("50.00", null, null, Instant.parse("2026-11-03T10:00:00Z"), "0002");
    when(paymentEventRepository.findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED))
        .thenReturn(List.of(over, legacy));

    var invoiceMap = (Map<String, Object>) builder.buildContext(invoiceId, memberId).get("invoice");

    assertThat(invoiceMap.get("paidAt")).isNull();
    var payments = (List<Map<String, Object>>) invoiceMap.get("payments");
    assertThat(payments.get(0)).containsEntry("methodLabel", "Trust transfer");
    assertThat((BigDecimal) payments.get(0).get("balanceAfter")).isEqualByComparingTo("0");
    assertThat(payments.get(1)).containsEntry("methodLabel", "Payment");
    assertThat(payments.get(1).get("reference")).isNull();
    assertThat((BigDecimal) payments.get(1).get("balanceAfter")).isEqualByComparingTo("0");
  }

  /**
   * Review fix (PR #1728): the receipt's own {@code payment.*} and the {@code invoice.payments[]}
   * row for the same event come from one ledger ({@code PaymentLedger}) — same date, method,
   * amount, reference and running balance, including a same-instant tie broken by id.
   */
  @Test
  @SuppressWarnings("unchecked")
  void receiptPaymentContext_agreesWithInvoicePaymentsRow_forTheSameEvent() {
    var invoice = paidInvoice(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
    TestIds.withId(invoice, invoiceId);
    TestIds.withField(invoice, "invoiceNumber", "INV-0007");
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    var sameInstant = Instant.parse("2026-11-03T22:15:00Z"); // 00:15 on 4 Nov in SA
    var a = event("300.00", PaymentMethod.EFT, "A", sameInstant, "000a");
    var b = event("200.00", PaymentMethod.CASH, "B", sameInstant, "000b"); // tie → after a by id
    var c = event("900.00", PaymentMethod.CARD, "C", Instant.parse("2026-11-04T08:00:00Z"), "000c");
    when(paymentEventRepository.findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED))
        .thenReturn(List.of(c, b, a));
    when(paymentEventRepository.findByIdAndInvoiceIdForUpdate(b.getId(), invoiceId))
        .thenReturn(Optional.of(b));
    var template = mock(DocumentTemplate.class);
    when(template.getId()).thenReturn(UUID.randomUUID());
    when(generatedDocumentService.resolveReceiptTemplate()).thenReturn(Optional.of(template));
    var generated = mock(GeneratedDocument.class);
    var generatedId = UUID.randomUUID();
    when(generated.getId()).thenReturn(generatedId);
    when(generatedDocumentService.generateDocument(
            any(),
            eq(invoiceId),
            anyBoolean(),
            anyBoolean(),
            anyList(),
            any(),
            anyString(),
            anyMap(),
            anyMap(),
            anyBoolean()))
        .thenReturn(new GeneratedDocumentService.GenerationResult(generated, null));
    var receiptService =
        new PaymentReceiptService(
            invoiceRepository,
            paymentEventRepository,
            invoiceLineRepository,
            generatedDocumentService,
            auditService);

    assertThat(receiptService.ensureReceipt(invoiceId, b.getId(), memberId)).isEqualTo(generatedId);

    ArgumentCaptor<Map<String, Object>> extraContext = ArgumentCaptor.forClass(Map.class);
    verify(generatedDocumentService)
        .generateDocument(
            any(),
            eq(invoiceId),
            anyBoolean(),
            anyBoolean(),
            anyList(),
            any(),
            anyString(),
            extraContext.capture(),
            anyMap(),
            anyBoolean());
    var receiptPayment = (Map<String, Object>) extraContext.getValue().get("payment");
    var invoiceMap = (Map<String, Object>) builder.buildContext(invoiceId, memberId).get("invoice");
    var payments = (List<Map<String, Object>>) invoiceMap.get("payments");

    assertThat(payments)
        .extracting(row -> row.get("id"))
        .containsExactly(a.getId(), b.getId(), c.getId());
    var tableRow = payments.get(1);
    assertThat(receiptPayment).containsEntry("number", "INV-0007/R1");
    var receiptWithoutNumber = new LinkedHashMap<>(receiptPayment);
    receiptWithoutNumber.remove("number");
    assertThat(receiptWithoutNumber).isEqualTo(tableRow);
    assertThat(tableRow).containsEntry("date", "2026-11-04");
    assertThat((BigDecimal) tableRow.get("balanceAfter")).isEqualByComparingTo("500.00");
  }

  @Test
  void methodLabels_coverEveryPaymentMethod() {
    assertThat(PaymentMethod.labelOf(PaymentMethod.EFT)).isEqualTo("EFT");
    assertThat(PaymentMethod.labelOf(PaymentMethod.CARD)).isEqualTo("Card");
    assertThat(PaymentMethod.labelOf(PaymentMethod.CASH)).isEqualTo("Cash");
    assertThat(PaymentMethod.labelOf(PaymentMethod.TRUST_TRANSFER)).isEqualTo("Trust transfer");
    assertThat(PaymentMethod.labelOf(PaymentMethod.XERO)).isEqualTo("Xero");
    assertThat(PaymentMethod.labelOf(PaymentMethod.OTHER)).isEqualTo("Other");
    assertThat(PaymentMethod.labelOf(null)).isEqualTo("Payment");
  }

  private Invoice paidInvoice(BigDecimal total, BigDecimal amountPaid) {
    var invoice =
        new Invoice(customerId, "ZAR", "Receipt Corp", "receipt@test.com", null, "Org", memberId);
    TestIds.withField(invoice, "total", total);
    TestIds.withField(invoice, "amountPaid", amountPaid);
    return invoice;
  }

  private PaymentEvent event(
      String amount, PaymentMethod method, String reference, Instant createdAt, String idSuffix) {
    var event =
        PaymentEvent.completed(
            invoiceId,
            "ZAR",
            "OPERATING",
            new PaymentPosting(
                new BigDecimal(amount),
                method != null ? method : PaymentMethod.OTHER,
                reference,
                "manual",
                null,
                null));
    if (method == null) {
      TestIds.withField(event, "paymentMethod", null); // pre-U15 row: NULL method
    }
    TestIds.withId(event, UUID.fromString("00000000-0000-0000-0000-00000000" + idSuffix));
    TestIds.withField(event, "createdAt", createdAt);
    return event;
  }
}
