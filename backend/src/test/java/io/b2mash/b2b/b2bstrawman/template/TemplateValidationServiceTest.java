package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.b2mash.b2b.b2bstrawman.template.TemplateValidationService.FieldValidationResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Phase 89 C1: {@link TemplateValidationService#validateRequiredFields} resolves a dotted {@code
 * field} path ({@code customFields.seller_name}) segment by segment through nested maps; a missing
 * map, a non-map segment, a null or a blank string reads as absent.
 */
class TemplateValidationServiceTest {

  private final TemplateValidationService service = new TemplateValidationService();

  private static Map<String, String> required(String entity, String field) {
    var entry = new HashMap<String, String>();
    entry.put("entity", entity);
    entry.put("field", field);
    return entry;
  }

  private static Map<String, Object> projectContext(Map<String, Object> project) {
    return Map.of("project", project);
  }

  private FieldValidationResult single(
      Map<String, String> requiredEntry, Map<String, Object> context) {
    var result = service.validateRequiredFields(List.of(requiredEntry), context);
    assertThat(result.fields()).hasSize(1);
    return result.fields().getFirst();
  }

  @Test
  void dottedPath_populated_isPresent() {
    var context = projectContext(Map.of("customFields", Map.of("seller_name", "Sipho Mokoena")));

    var result =
        service.validateRequiredFields(
            List.of(required("project", "customFields.seller_name")), context);

    assertThat(result.allPresent()).isTrue();
    assertThat(result.fields().getFirst().present()).isTrue();
    assertThat(result.fields().getFirst().reason()).isNull();
  }

  @Test
  void dottedPath_blankString_isAbsentWithTheFullPathInTheReason() {
    var field =
        single(
            required("project", "customFields.seller_name"),
            projectContext(Map.of("customFields", Map.of("seller_name", "  "))));

    assertThat(field.present()).isFalse();
    assertThat(field.reason()).isEqualTo("Field not populated: project.customFields.seller_name");
  }

  @Test
  void dottedPath_leafKeyMissing_isAbsent() {
    var field =
        single(
            required("project", "customFields.seller_name"),
            projectContext(Map.of("customFields", Map.of("purchaser_name", "Thandi Nkosi"))));

    assertThat(field.present()).isFalse();
    assertThat(field.reason()).isEqualTo("Field not populated: project.customFields.seller_name");
  }

  @Test
  void dottedPath_leafNull_isAbsent() {
    var customFields = new HashMap<String, Object>();
    customFields.put("seller_name", null);

    var field =
        single(
            required("project", "customFields.seller_name"),
            projectContext(Map.of("customFields", customFields)));

    assertThat(field.present()).isFalse();
  }

  @Test
  void dottedPath_intermediateMapMissing_isAbsent() {
    var field =
        single(
            required("project", "customFields.seller_name"),
            projectContext(Map.of("name", "Smith v Jones")));

    assertThat(field.present()).isFalse();
    assertThat(field.reason()).isEqualTo("Field not populated: project.customFields.seller_name");
  }

  @Test
  void dottedPath_intermediateNotAMap_isAbsent() {
    var field =
        single(
            required("project", "customFields.seller_name"),
            projectContext(Map.of("customFields", "not-a-map")));

    assertThat(field.present()).isFalse();
  }

  @Test
  void singleSegmentField_stillResolves() {
    var context = projectContext(Map.of("name", "Smith v Jones"));

    assertThat(single(required("project", "name"), context).present()).isTrue();
    assertThat(single(required("project", "description"), context).present()).isFalse();
  }

  @Test
  void entityMissingFromContext_isReportedAsEntityNotFound() {
    var field =
        single(required("project", "customFields.seller_name"), Map.of("customer", Map.of()));

    assertThat(field.present()).isFalse();
    assertThat(field.reason()).isEqualTo("Entity context not found: project");
  }

  @Test
  void malformedEntry_isReportedAsMalformed() {
    var field = single(required("project", null), projectContext(Map.of("name", "x")));

    assertThat(field.present()).isFalse();
    assertThat(field.reason()).isEqualTo("Malformed required field entry");
  }

  @Test
  void nullOrEmptyDeclaration_isAllPresentWithNoFields() {
    var context = projectContext(Map.of());

    var fromNull = service.validateRequiredFields(null, context);
    var fromEmpty = service.validateRequiredFields(List.of(), context);

    assertThat(fromNull.allPresent()).isTrue();
    assertThat(fromNull.fields()).isEmpty();
    assertThat(fromEmpty.allPresent()).isTrue();
    assertThat(fromEmpty.fields()).isEmpty();
  }

  @Test
  void mixedDeclaration_allPresentOnlyWhenEveryPartyIsNamed() {
    var context = projectContext(Map.of("customFields", Map.of("purchaser_name", "Thandi Nkosi")));

    var result =
        service.validateRequiredFields(
            List.of(
                required("project", "customFields.seller_name"),
                required("project", "customFields.purchaser_name")),
            context);

    assertThat(result.allPresent()).isFalse();
    assertThat(result.fields())
        .extracting(FieldValidationResult::field, FieldValidationResult::present)
        .containsExactly(
            tuple("customFields.seller_name", false), tuple("customFields.purchaser_name", true));
  }
}
