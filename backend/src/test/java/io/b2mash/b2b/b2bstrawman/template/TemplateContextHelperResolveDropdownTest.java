package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateContextHelperResolveDropdownTest {

  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private EntityTagRepository entityTagRepository;
  @Mock private TagRepository tagRepository;
  @Mock private StorageService storageService;
  @Mock private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;

  @InjectMocks private TemplateContextHelper helper;

  @Test
  void resolveDropdownLabelsReplacesValueWithLabel() {
    var fd =
        new FieldDefinition(
            EntityType.PROJECT, "Engagement Type", "engagement_type", FieldType.DROPDOWN);
    fd.setOptions(
        List.of(
            Map.of("value", "MONTHLY_BOOKKEEPING", "label", "Monthly Bookkeeping"),
            Map.of("value", "ANNUAL_AUDIT", "label", "Annual Audit")));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    var customFields =
        Map.<String, Object>of("engagement_type", "MONTHLY_BOOKKEEPING", "case_number", "CN-001");

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.PROJECT);

    assertThat(resolved.get("engagement_type")).isEqualTo("Monthly Bookkeeping");
    assertThat(resolved.get("case_number")).isEqualTo("CN-001");
  }

  @Test
  void resolveDropdownLabelsReturnsNullMapAsIs() {
    var result = helper.resolveDropdownLabels(null, EntityType.PROJECT);
    assertThat(result).isNull();
  }

  @Test
  void resolveDropdownLabelsReturnsEmptyMapAsIs() {
    var result = helper.resolveDropdownLabels(Map.of(), EntityType.PROJECT);
    assertThat(result).isEmpty();
  }

  @Test
  void resolveDropdownLabelsKeepsValueWhenNoMatchingOption() {
    var fd =
        new FieldDefinition(EntityType.CUSTOMER, "Entity Type", "entity_type", FieldType.DROPDOWN);
    fd.setOptions(
        List.of(
            Map.of("value", "COMPANY", "label", "Company"),
            Map.of("value", "TRUST", "label", "Trust")));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.CUSTOMER))
        .thenReturn(List.of(fd));

    var customFields = Map.<String, Object>of("entity_type", "UNKNOWN_TYPE");

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.CUSTOMER);

    assertThat(resolved.get("entity_type")).isEqualTo("UNKNOWN_TYPE");
  }

  @Test
  void resolveDropdownLabelsIgnoresNonDropdownFields() {
    var textField =
        new FieldDefinition(EntityType.PROJECT, "Case Number", "case_number", FieldType.TEXT);
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(textField));

    var customFields = Map.<String, Object>of("case_number", "CN-001");

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.PROJECT);

    assertThat(resolved.get("case_number")).isEqualTo("CN-001");
  }

  @Test
  void resolveDropdownLabelsHandlesMultipleDropdownFields() {
    var engagementField =
        new FieldDefinition(
            EntityType.PROJECT, "Engagement Type", "engagement_type", FieldType.DROPDOWN);
    engagementField.setOptions(
        List.of(Map.of("value", "MONTHLY_BOOKKEEPING", "label", "Monthly Bookkeeping")));

    var complexityField =
        new FieldDefinition(EntityType.PROJECT, "Complexity", "complexity", FieldType.DROPDOWN);
    complexityField.setOptions(
        List.of(Map.of("value", "HIGH", "label", "High"), Map.of("value", "LOW", "label", "Low")));

    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(engagementField, complexityField));

    var customFields =
        Map.<String, Object>of(
            "engagement_type", "MONTHLY_BOOKKEEPING",
            "complexity", "HIGH");

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.PROJECT);

    assertThat(resolved.get("engagement_type")).isEqualTo("Monthly Bookkeeping");
    assertThat(resolved.get("complexity")).isEqualTo("High");
  }

  @Test
  void resolveDropdownLabelsSkipsDropdownFieldsWithNullOptions() {
    var fd = new FieldDefinition(EntityType.PROJECT, "Category", "category", FieldType.DROPDOWN);
    // options is null by default
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    var customFields = Map.<String, Object>of("category", "SOME_VALUE");

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.PROJECT);

    assertThat(resolved.get("category")).isEqualTo("SOME_VALUE");
  }

  @Test
  void resolveDropdownLabelsDoesNotResolveNonStringValues() {
    var fd = new FieldDefinition(EntityType.PROJECT, "Status", "status", FieldType.DROPDOWN);
    fd.setOptions(List.of(Map.of("value", "1", "label", "Active")));
    when(fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
            EntityType.PROJECT))
        .thenReturn(List.of(fd));

    var customFields = Map.<String, Object>of("status", 1);

    var resolved = helper.resolveDropdownLabels(customFields, EntityType.PROJECT);

    assertThat(resolved.get("status")).isEqualTo(1);
  }
}
