package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.TaxType;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    assertThat(projectMap.get("workType")).isEqualTo("engagement");
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
}
