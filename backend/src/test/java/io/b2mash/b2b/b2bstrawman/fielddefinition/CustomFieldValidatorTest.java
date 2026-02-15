package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFieldValidatorTest {

  @Mock private FieldDefinitionRepository fieldDefinitionRepository;
  @Mock private FieldGroupMemberRepository fieldGroupMemberRepository;

  @InjectMocks private CustomFieldValidator validator;

  @Test
  void validTextFieldPasses() {
    var fd = createFieldDef("court", FieldType.TEXT, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    var result = validator.validate(EntityType.PROJECT, Map.of("court", "High Court"), null);

    assertThat(result).containsEntry("court", "High Court");
  }

  @Test
  void textFieldWithPatternValidation() {
    var fd = createFieldDef("code", FieldType.TEXT, false);
    fd.setValidation(Map.of("pattern", "^[A-Z]{3}$"));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result = validator.validate(EntityType.PROJECT, Map.of("code", "ABC"), null);
    assertThat(result).containsEntry("code", "ABC");

    // Invalid
    assertThatThrownBy(() -> validator.validate(EntityType.PROJECT, Map.of("code", "abc123"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void numberFieldRejectsString() {
    var fd = createFieldDef("amount", FieldType.NUMBER, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    assertThatThrownBy(
            () -> validator.validate(EntityType.PROJECT, Map.of("amount", "not-a-number"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void dropdownFieldRejectsUnknownOption() {
    var fd = createFieldDef("priority_level", FieldType.DROPDOWN, false);
    fd.setOptions(
        List.of(
            Map.of("value", "low", "label", "Low"),
            Map.of("value", "medium", "label", "Medium"),
            Map.of("value", "high", "label", "High")));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result = validator.validate(EntityType.PROJECT, Map.of("priority_level", "high"), null);
    assertThat(result).containsEntry("priority_level", "high");

    // Invalid
    assertThatThrownBy(
            () ->
                validator.validate(EntityType.PROJECT, Map.of("priority_level", "critical"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void currencyFieldRequiresAmountAndCurrency() {
    var fd = createFieldDef("fee", FieldType.CURRENCY, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result =
        validator.validate(
            EntityType.PROJECT, Map.of("fee", Map.of("amount", 100.50, "currency", "ZAR")), null);
    assertThat(result).containsKey("fee");

    // Missing currency
    assertThatThrownBy(
            () ->
                validator.validate(
                    EntityType.PROJECT, Map.of("fee", Map.of("amount", 100.50)), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void requiredFieldInAppliedGroupThrowsWhenMissing() {
    var fd = createFieldDef("court", FieldType.TEXT, true);
    UUID groupId = UUID.randomUUID();
    var member = new FieldGroupMember(groupId, fd.getId(), 0);

    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));
    when(fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId))
        .thenReturn(List.of(member));

    // Should throw because required field "court" is missing
    assertThatThrownBy(() -> validator.validate(EntityType.PROJECT, Map.of(), List.of(groupId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void unknownKeysAreStripped() {
    var fd = createFieldDef("court", FieldType.TEXT, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    var result =
        validator.validate(
            EntityType.PROJECT,
            Map.of("court", "High Court", "unknown_key", "should be stripped"),
            null);

    assertThat(result).containsKey("court");
    assertThat(result).doesNotContainKey("unknown_key");
  }

  @Test
  void emptyInputPassesWhenNoRequiredFields() {
    var result = validator.validate(EntityType.PROJECT, Map.of(), null);

    assertThat(result).isEmpty();
  }

  @Test
  void urlFieldRejectsInvalidFormat() {
    var fd = createFieldDef("website", FieldType.URL, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result =
        validator.validate(EntityType.PROJECT, Map.of("website", "https://example.com"), null);
    assertThat(result).containsEntry("website", "https://example.com");

    // Invalid
    assertThatThrownBy(
            () -> validator.validate(EntityType.PROJECT, Map.of("website", "not-a-url"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void dateFieldRejectsInvalidDateString() {
    var fd = createFieldDef("start_date", FieldType.DATE, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result = validator.validate(EntityType.PROJECT, Map.of("start_date", "2025-01-15"), null);
    assertThat(result).containsEntry("start_date", "2025-01-15");

    // Invalid
    assertThatThrownBy(
            () -> validator.validate(EntityType.PROJECT, Map.of("start_date", "not-a-date"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void booleanFieldRejectsNonBoolean() {
    var fd = createFieldDef("is_urgent", FieldType.BOOLEAN, false);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    // Valid
    var result = validator.validate(EntityType.PROJECT, Map.of("is_urgent", true), null);
    assertThat(result).containsEntry("is_urgent", true);

    // Invalid
    assertThatThrownBy(
            () -> validator.validate(EntityType.PROJECT, Map.of("is_urgent", "yes"), null))
        .isInstanceOf(InvalidStateException.class);
  }

  // --- Helper ---

  private FieldDefinition createFieldDef(String slug, FieldType fieldType, boolean required) {
    var fd = new FieldDefinition(EntityType.PROJECT, slug, slug, fieldType);
    // Use reflection to set the id since it's normally auto-generated
    try {
      var idField = FieldDefinition.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(fd, UUID.randomUUID());
      var reqField = FieldDefinition.class.getDeclaredField("required");
      reqField.setAccessible(true);
      reqField.set(fd, required);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return fd;
  }
}
