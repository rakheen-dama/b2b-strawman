package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
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
class TemplateVariableAnalyzerTest {

  @Mock private ClauseRepository clauseRepository;

  @InjectMocks private TemplateVariableAnalyzer analyzer;

  @Test
  void emptyDocReturnsEmptyKeys() {
    Map<String, Object> doc = Map.of("type", "doc");
    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).isEmpty();
  }

  @Test
  void nullDocReturnsEmptyKeys() {
    var keys = analyzer.extractVariableKeys(null);
    assertThat(keys).isEmpty();
  }

  @Test
  void textOnlyDocReturnsEmptyKeys() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.of("type", "text", "text", "Hello world")))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).isEmpty();
  }

  @Test
  void extractsVariableKeys() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of("type", "variable", "attrs", Map.of("key", "customer.name")),
                        Map.of("type", "text", "text", " signed on "),
                        Map.of("type", "variable", "attrs", Map.of("key", "project.startDate"))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).containsExactlyInAnyOrder("customer.name", "project.startDate");
  }

  @Test
  void extractsCustomFieldVariableKeys() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "variable",
                            "attrs",
                            Map.of("key", "customer.customFields.tax_number")),
                        Map.of(
                            "type",
                            "variable",
                            "attrs",
                            Map.of("key", "project.customFields.budget_code"))))));

    var slugs = analyzer.extractCustomFieldSlugs(doc);
    assertThat(slugs).containsKey("customer");
    assertThat(slugs.get("customer")).containsExactly("tax_number");
    assertThat(slugs).containsKey("project");
    assertThat(slugs.get("project")).containsExactly("budget_code");
  }

  @Test
  void extractsVariablesFromClauseBlocks() {
    UUID clauseId = UUID.randomUUID();
    Map<String, Object> clauseBody =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "variable",
                            "attrs",
                            Map.of("key", "customer.customFields.vat_number"))))));

    var clause = new Clause("Test Clause", "test-clause", clauseBody, "general");
    when(clauseRepository.findById(clauseId)).thenReturn(Optional.of(clause));

    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "clauseBlock",
                    "attrs",
                    Map.of("clauseId", clauseId.toString(), "slug", "test-clause")),
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.of("type", "variable", "attrs", Map.of("key", "customer.name"))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).containsExactlyInAnyOrder("customer.name", "customer.customFields.vat_number");
  }

  @Test
  void extractsVariablesFromLoopTableColumns() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "loopTable",
                    "attrs",
                    Map.of(
                        "dataSource",
                        "invoice.lineItems",
                        "columns",
                        List.of(
                            Map.of("header", "Description", "key", "description"),
                            Map.of("header", "Amount", "key", "amount"))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys)
        .containsExactlyInAnyOrder("invoice.lineItems.description", "invoice.lineItems.amount");
  }

  @Test
  void skipsBlankAndNullVariableKeys() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of("type", "variable", "attrs", Map.of("key", "")),
                        Map.of("type", "variable", "attrs", Map.of()),
                        Map.of("type", "variable", "attrs", Map.of("key", "customer.name"))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).containsExactly("customer.name");
  }

  @Test
  void deduplicatesVariableKeys() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(
                        Map.of("type", "variable", "attrs", Map.of("key", "customer.name")),
                        Map.of("type", "variable", "attrs", Map.of("key", "customer.name"))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).containsExactly("customer.name");
  }

  @Test
  void shouldExtractFieldKeyFromConditionalBlock() {
    Map<String, Object> doc =
        Map.of(
            "type",
            "doc",
            "content",
            List.of(
                Map.of(
                    "type",
                    "conditionalBlock",
                    "attrs",
                    Map.of(
                        "fieldKey",
                        "customer.customFields.tax_type",
                        "operator",
                        "eq",
                        "value",
                        "vat"),
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "paragraph",
                            "content",
                            List.of(Map.of("type", "text", "text", "VAT content")))))));

    var keys = analyzer.extractVariableKeys(doc);
    assertThat(keys).contains("customer.customFields.tax_type");

    var slugs = analyzer.extractCustomFieldSlugs(doc);
    assertThat(slugs).containsKey("customer");
    assertThat(slugs.get("customer")).contains("tax_type");
  }
}
