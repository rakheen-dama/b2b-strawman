package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private S3PresignedUrlService s3PresignedUrlService;

  @InjectMocks private InvoiceContextBuilder builder;

  private final UUID invoiceId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();

  @Test
  void supportsInvoiceEntityType() {
    assertThat(builder.supports()).isEqualTo(TemplateEntityType.INVOICE);
  }

  @Test
  void buildContextWithProjectAndLines() {
    var invoice =
        new Invoice(customerId, "USD", "Acme Corp", "acme@test.com", null, "Test Org", memberId);
    when(invoiceRepository.findOneById(invoiceId)).thenReturn(Optional.of(invoice));

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

    var customer = new Customer("Acme Corp", "acme@test.com", null, null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));

    var project = new Project("Project X", "desc", memberId);
    when(projectRepository.findOneById(projectId)).thenReturn(Optional.of(project));

    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

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
  }

  @Test
  void buildContextWithoutProject() {
    var invoice =
        new Invoice(customerId, "ZAR", "Solo Corp", "solo@test.com", null, "Org", memberId);
    when(invoiceRepository.findOneById(invoiceId)).thenReturn(Optional.of(invoice));

    // Line with no projectId
    var line =
        new InvoiceLine(
            invoiceId, null, null, "Ad-hoc Work", BigDecimal.ONE, BigDecimal.valueOf(200), 1);
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId))
        .thenReturn(List.of(line));

    var customer = new Customer("Solo Corp", "solo@test.com", null, null, null, memberId);
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));

    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(invoiceId, memberId);

    assertThat(context.get("project")).isNull();
  }

  @Test
  void buildContextWithCustomerCustomFields() {
    var invoice =
        new Invoice(customerId, "EUR", "Fields Corp", "f@test.com", null, "Org", memberId);
    when(invoiceRepository.findOneById(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId)).thenReturn(List.of());

    var customer = new Customer("Fields Corp", "f@test.com", null, null, null, memberId);
    customer.setCustomFields(Map.of("vat_number", "VAT123"));
    when(customerRepository.findOneById(customerId)).thenReturn(Optional.of(customer));

    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(memberRepository.findOneById(memberId)).thenReturn(Optional.empty());

    var context = builder.buildContext(invoiceId, memberId);

    @SuppressWarnings("unchecked")
    var customerMap = (Map<String, Object>) context.get("customer");
    @SuppressWarnings("unchecked")
    var customFields = (Map<String, Object>) customerMap.get("customFields");
    assertThat(customFields).containsEntry("vat_number", "VAT123");
  }

  @Test
  void throwsWhenInvoiceNotFound() {
    when(invoiceRepository.findOneById(invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> builder.buildContext(invoiceId, memberId))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
