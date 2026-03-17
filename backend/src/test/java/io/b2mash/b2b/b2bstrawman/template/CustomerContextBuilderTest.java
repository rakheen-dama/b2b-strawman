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
import java.math.BigDecimal;
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
    var customer = new Customer("Solo Customer", "solo@example.com", null, null, null, memberId);
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
        new Customer("Fields Customer", "fields@example.com", null, null, null, memberId);
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
    var customer = new Customer("Tagged Customer", "tag@example.com", null, null, null, memberId);
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
    var customer = new Customer("Logo Customer", "logo@example.com", null, null, null, memberId);
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
        new Customer("Invoice Customer", "invoice@example.com", null, null, null, memberId);
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
    var customer = new Customer("No Invoice", "no-inv@example.com", null, null, null, memberId);
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
  void throwsWhenCustomerNotFound() {
    when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(customerId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
