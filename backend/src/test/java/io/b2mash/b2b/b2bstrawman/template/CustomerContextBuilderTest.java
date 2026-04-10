package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
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
class CustomerContextBuilderTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerProjectRepository customerProjectRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private TemplateContextHelper contextHelper;

  @InjectMocks private CustomerContextBuilder builder;

  private final UUID customerId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient()
        .when(contextHelper.resolveDropdownLabels(any(), any(EntityType.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void supportsCustomerEntityType() {
    assertThat(builder.supports()).isEqualTo(TemplateEntityType.CUSTOMER);
  }

  @Test
  void buildContextWithProjects() {
    var customer = new Customer("Acme Corp", "acme@example.com", "555-0100", null, null, memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

    var projectId = UUID.randomUUID();
    var cp = new CustomerProject(customerId, projectId, memberId);
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of(cp));

    var project = new Project("Project Alpha", "desc", memberId);
    when(projectRepository.findAllById(List.of(projectId))).thenReturn(List.of(project));

    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId))
        .thenReturn(Map.of("name", "User", "email", "user@test.com"));

    var context = builder.buildContext(customerId, memberId);

    assertThat(context).containsKey("customer");
    assertThat(context).containsKey("projects");

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    assertThat(customerMap.get("name")).isEqualTo("Acme Corp");
    assertThat(customerMap.get("email")).isEqualTo("acme@example.com");

    @SuppressWarnings("unchecked")
    var projects = (List<Map<String, Object>>) context.get("projects");
    assertThat(projects).hasSize(1);
    assertThat(projects.getFirst().get("name")).isEqualTo("Project Alpha");
  }

  @Test
  void buildContextWithoutProjects() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Solo Customer", "solo@example.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var projects = (List<Map<String, Object>>) context.get("projects");
    assertThat(projects).isEmpty();
  }

  @Test
  void buildContextWithCustomFields() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Fields Customer", "fields@example.com", memberId);
    customer.setCustomFields(Map.of("industry", "Tech", "tier", "Gold"));
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");
    assertThat(customFields).containsEntry("industry", "Tech");
    assertThat(customFields).containsEntry("tier", "Gold");
  }

  @Test
  void buildContextWithTags() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Tagged Customer", "tag@example.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());

    when(contextHelper.buildTagsList("CUSTOMER", customerId))
        .thenReturn(List.of(Map.of("name", "VIP", "color", "#gold")));
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var tags = (List<Map<String, Object>>) context.get("tags");
    assertThat(tags).hasSize(1);
    assertThat(tags.getFirst().get("name")).isEqualTo("VIP");
  }

  @Test
  void buildContextWithLogoUrl() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Logo Customer", "logo@example.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());

    when(contextHelper.buildOrgContext())
        .thenReturn(Map.of("logoUrl", "https://s3.example.com/logo.png"));
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var orgMap = (Map<String, Object>) context.get("org");
    assertThat(orgMap.get("logoUrl")).isEqualTo("https://s3.example.com/logo.png");
  }

  @Test
  void buildContextWithInvoices() {
    var customer =
        TestCustomerFactory.createActiveCustomer(
            "Invoice Customer", "invoice@example.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());

    // Create a SENT invoice (outstanding) and a PAID invoice
    var sentInvoice =
        new Invoice(customerId, "ZAR", "Invoice Customer", null, null, "Org", memberId);
    sentInvoice.recalculateTotals(new BigDecimal("1000.00"), false, BigDecimal.ZERO, false);
    sentInvoice.approve("INV-001", memberId);
    sentInvoice.markSent();

    var paidInvoice =
        new Invoice(customerId, "ZAR", "Invoice Customer", null, null, "Org", memberId);
    paidInvoice.recalculateTotals(new BigDecimal("500.00"), false, BigDecimal.ZERO, false);
    paidInvoice.approve("INV-002", memberId);
    paidInvoice.markSent();
    paidInvoice.recordPayment("PAY-001");

    when(invoiceRepository.findByCustomerId(customerId))
        .thenReturn(List.of(sentInvoice, paidInvoice));
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    assertThat(context).containsKey("invoices");
    assertThat(context).containsKey("totalOutstanding");

    @SuppressWarnings("unchecked")
    var invoices = (List<Map<String, Object>>) context.get("invoices");
    assertThat(invoices).hasSize(2);

    // First invoice: SENT (outstanding)
    assertThat(invoices.get(0).get("invoiceNumber")).isEqualTo("INV-001");
    assertThat(invoices.get(0).get("status")).isEqualTo("SENT");
    assertThat(invoices.get(0).get("total")).isEqualTo(new BigDecimal("1000.00"));
    assertThat(invoices.get(0).get("currency")).isEqualTo("ZAR");

    // Second invoice: PAID (not outstanding)
    assertThat(invoices.get(1).get("invoiceNumber")).isEqualTo("INV-002");
    assertThat(invoices.get(1).get("status")).isEqualTo("PAID");

    // Total outstanding should only include SENT invoice
    assertThat(context.get("totalOutstanding")).isEqualTo(new BigDecimal("1000.00"));
  }

  @Test
  void buildContextWithNoInvoicesHasEmptyListAndZeroOutstanding() {
    var customer =
        TestCustomerFactory.createActiveCustomer("No Invoice", "no-inv@example.com", memberId);
    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var invoices = (List<Map<String, Object>>) context.get("invoices");
    assertThat(invoices).isEmpty();
    assertThat(context.get("totalOutstanding")).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void buildContextExposesPromotedStructuralFieldsAsDirectVariables() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Promoted Co", "p@example.com", memberId);
    customer.setTaxNumber("VAT-100");
    customer.setAddressLine1("1 Main St");
    customer.setAddressLine2("Suite 2");
    customer.setCity("Cape Town");
    customer.setStateProvince("WC");
    customer.setPostalCode("8001");
    customer.setCountry("ZA");
    customer.setContactName("Alice");
    customer.setContactEmail("alice@example.com");
    customer.setContactPhone("+27-21-555-0001");
    customer.setEntityType("PTY_LTD");
    customer.setFinancialYearEnd(LocalDate.of(2026, 2, 28));
    customer.setRegistrationNumber("2020/123456/07");

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");

    // Direct promoted variables (Epic 459).
    assertThat(customerMap.get("taxNumber")).isEqualTo("VAT-100");
    assertThat(customerMap.get("addressLine1")).isEqualTo("1 Main St");
    assertThat(customerMap.get("addressLine2")).isEqualTo("Suite 2");
    assertThat(customerMap.get("city")).isEqualTo("Cape Town");
    assertThat(customerMap.get("stateProvince")).isEqualTo("WC");
    assertThat(customerMap.get("postalCode")).isEqualTo("8001");
    assertThat(customerMap.get("country")).isEqualTo("ZA");
    assertThat(customerMap.get("contactName")).isEqualTo("Alice");
    assertThat(customerMap.get("contactEmail")).isEqualTo("alice@example.com");
    assertThat(customerMap.get("contactPhone")).isEqualTo("+27-21-555-0001");
    assertThat(customerMap.get("entityType")).isEqualTo("PTY_LTD");
    assertThat(customerMap.get("financialYearEnd")).isEqualTo("2026-02-28");
    assertThat(customerMap.get("registrationNumber")).isEqualTo("2020/123456/07");
  }

  @Test
  void buildContextInjectsBackwardCompatCustomFieldAliasesForPromotedFields() {
    var customer =
        TestCustomerFactory.createActiveCustomer("Alias Co", "alias@example.com", memberId);
    customer.setTaxNumber("VAT-42");
    customer.setAddressLine1("42 Alias Way");
    customer.setCity("Joburg");
    customer.setCountry("ZA");
    customer.setContactName("Bob");
    customer.setContactEmail("bob@example.com");
    customer.setContactPhone("+27-11-555-0042");
    customer.setEntityType("SOLE_PROP");
    customer.setRegistrationNumber("REG-42");
    customer.setFinancialYearEnd(LocalDate.of(2026, 2, 28));

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");

    // Backward-compat aliases so pre-Phase-63 templates still resolve.
    assertThat(customFields).containsEntry("tax_number", "VAT-42");
    assertThat(customFields).containsEntry("vat_number", "VAT-42");
    assertThat(customFields).containsEntry("address_line1", "42 Alias Way");
    assertThat(customFields).containsEntry("city", "Joburg");
    assertThat(customFields).containsEntry("country", "ZA");
    assertThat(customFields).containsEntry("primary_contact_name", "Bob");
    assertThat(customFields).containsEntry("primary_contact_email", "bob@example.com");
    assertThat(customFields).containsEntry("primary_contact_phone", "+27-11-555-0042");
    assertThat(customFields).containsEntry("phone", "+27-11-555-0042");
    assertThat(customFields).containsEntry("acct_entity_type", "SOLE_PROP");
    assertThat(customFields).containsEntry("client_type", "SOLE_PROP");
    assertThat(customFields).containsEntry("acct_company_registration_number", "REG-42");
    assertThat(customFields).containsEntry("registration_number", "REG-42");
    assertThat(customFields).containsEntry("registered_address", "42 Alias Way");
    assertThat(customFields).containsEntry("physical_address", "42 Alias Way");
    assertThat(customFields).containsEntry("financial_year_end", "2026-02-28");
  }

  @Test
  void buildContextPreservesLegacyJsonbValuesWhenStructuralColumnsAreNull() {
    // Pre-Phase-63 entities may carry promoted slugs only in the JSONB customFields blob.
    // When the structural getter returns null, the builder must leave the legacy JSONB value
    // untouched (not overwrite with null).
    var customer =
        TestCustomerFactory.createActiveCustomer("Legacy Co", "legacy@example.com", memberId);
    customer.setCustomFields(
        Map.of(
            "tax_number", "LEGACY-VAT",
            "vat_number", "LEGACY-VAT",
            "address_line1", "Old Address"));

    when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
    when(customerProjectRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
    when(contextHelper.buildTagsList("CUSTOMER", customerId)).thenReturn(List.of());
    when(contextHelper.buildOrgContext()).thenReturn(Map.of());
    when(contextHelper.buildGeneratedByMap(memberId)).thenReturn(Map.of("name", "Unknown"));

    var context = builder.buildContext(customerId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");

    assertThat(customFields).containsEntry("tax_number", "LEGACY-VAT");
    assertThat(customFields).containsEntry("vat_number", "LEGACY-VAT");
    assertThat(customFields).containsEntry("address_line1", "Old Address");
  }

  @Test
  void throwsWhenCustomerNotFound() {
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(customerId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
