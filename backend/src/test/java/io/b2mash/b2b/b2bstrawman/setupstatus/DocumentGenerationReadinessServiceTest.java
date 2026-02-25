package io.b2mash.b2b.b2bstrawman.setupstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateContextBuilder;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplateValidationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationReadinessServiceTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID INVOICE_ID = UUID.randomUUID();

  @Mock private DocumentTemplateRepository documentTemplateRepository;

  private TemplateContextBuilder projectBuilder;
  private TemplateContextBuilder customerBuilder;
  private TemplateContextBuilder invoiceBuilder;
  private DocumentGenerationReadinessService service;

  @BeforeEach
  void setUp() {
    projectBuilder = mock(TemplateContextBuilder.class);
    lenient().when(projectBuilder.supports()).thenReturn(TemplateEntityType.PROJECT);

    customerBuilder = mock(TemplateContextBuilder.class);
    lenient().when(customerBuilder.supports()).thenReturn(TemplateEntityType.CUSTOMER);

    invoiceBuilder = mock(TemplateContextBuilder.class);
    lenient().when(invoiceBuilder.supports()).thenReturn(TemplateEntityType.INVOICE);

    service =
        new DocumentGenerationReadinessService(
            documentTemplateRepository,
            List.of(projectBuilder, customerBuilder, invoiceBuilder),
            new TemplateValidationService());
  }

  @Test
  void checkReadiness_completeProjectContext_returnsReady() {
    var template = createProjectTemplate("Engagement Letter", "engagement-letter");

    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of(template));

    var context = new HashMap<String, Object>();
    context.put("project", Map.of("name", "Test Project"));
    context.put("customer", Map.of("name", "Acme Corp"));
    context.put("org", Map.of("defaultCurrency", "ZAR"));
    when(projectBuilder.buildContext(eq(PROJECT_ID), any())).thenReturn(context);

    var result = service.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().ready()).isTrue();
    assertThat(result.getFirst().missingFields()).isEmpty();
    assertThat(result.getFirst().templateName()).isEqualTo("Engagement Letter");
    assertThat(result.getFirst().templateSlug()).isEqualTo("engagement-letter");
  }

  @Test
  void checkReadiness_projectMissingCustomer_returnsNotReady() {
    var template = createProjectTemplate("Engagement Letter", "engagement-letter");

    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of(template));

    var context = new HashMap<String, Object>();
    context.put("project", Map.of("name", "Test Project"));
    context.put("customer", null);
    context.put("org", Map.of("defaultCurrency", "ZAR"));
    when(projectBuilder.buildContext(eq(PROJECT_ID), any())).thenReturn(context);

    var result = service.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().ready()).isFalse();
    assertThat(result.getFirst().missingFields()).containsExactly("Customer");
  }

  @Test
  void checkReadiness_noTemplatesForEntityType_returnsEmptyList() {
    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of());

    var result = service.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    assertThat(result).isEmpty();
  }

  @Test
  void checkReadiness_multipleTemplates_returnsMixedResults() {
    var template1 = createProjectTemplate("Ready Template", "ready-template");
    var template2 = createProjectTemplate("Also Ready", "also-ready");

    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of(template1, template2));

    var context = new HashMap<String, Object>();
    context.put("project", Map.of("name", "Test Project"));
    context.put("customer", Map.of("name", "Acme Corp"));
    context.put("org", Map.of("defaultCurrency", "ZAR"));
    when(projectBuilder.buildContext(eq(PROJECT_ID), any())).thenReturn(context);

    var result = service.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(TemplateReadiness::ready);
  }

  @Test
  void checkReadiness_sentinelUUID_usedInsteadOfNull() {
    var template = createProjectTemplate("Test Template", "test-template");

    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of(template));

    var context = new HashMap<String, Object>();
    context.put("project", Map.of("name", "Test"));
    context.put("customer", Map.of("name", "Acme"));
    context.put("org", Map.of("defaultCurrency", "ZAR"));
    when(projectBuilder.buildContext(eq(PROJECT_ID), any())).thenReturn(context);

    service.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    // Verify buildContext was called with a non-null memberId (the sentinel UUID)
    verify(projectBuilder).buildContext(eq(PROJECT_ID), any(UUID.class));
  }

  @Test
  void checkReadiness_noMatchingBuilder_returnsTemplatesAsNotReady() {
    // Create a service with no builders
    var serviceNoBuilders =
        new DocumentGenerationReadinessService(
            documentTemplateRepository, List.of(), new TemplateValidationService());

    var template = createProjectTemplate("Engagement Letter", "engagement-letter");
    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.PROJECT))
        .thenReturn(List.of(template));

    var result = serviceNoBuilders.checkReadiness(TemplateEntityType.PROJECT, PROJECT_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().ready()).isFalse();
    assertThat(result.getFirst().missingFields()).containsExactly("No context builder available");
  }

  @Test
  void checkReadiness_invoiceMissingLines_returnsNotReady() {
    var template =
        new DocumentTemplate(
            TemplateEntityType.INVOICE,
            "Invoice Template",
            "invoice-template",
            TemplateCategory.OTHER,
            "<html>invoice</html>");

    when(documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
            TemplateEntityType.INVOICE))
        .thenReturn(List.of(template));

    var context = new HashMap<String, Object>();
    context.put("invoice", Map.of("invoiceNumber", "INV-001"));
    context.put("customer", Map.of("name", "Acme"));
    context.put("lines", List.of()); // empty lines
    when(invoiceBuilder.buildContext(eq(INVOICE_ID), any())).thenReturn(context);

    var result = service.checkReadiness(TemplateEntityType.INVOICE, INVOICE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().ready()).isFalse();
    assertThat(result.getFirst().missingFields()).containsExactly("Invoice Lines");
  }

  private DocumentTemplate createProjectTemplate(String name, String slug) {
    return new DocumentTemplate(
        TemplateEntityType.PROJECT,
        name,
        slug,
        TemplateCategory.ENGAGEMENT_LETTER,
        "<html></html>");
  }
}
