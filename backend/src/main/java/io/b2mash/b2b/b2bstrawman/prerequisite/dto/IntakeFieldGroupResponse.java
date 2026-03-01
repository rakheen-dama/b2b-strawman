package io.b2mash.b2b.b2bstrawman.prerequisite.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionService.IntakeFieldGroup;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record IntakeFieldGroupResponse(List<GroupResponse> groups) {

  public static IntakeFieldGroupResponse from(List<IntakeFieldGroup> groups) {
    return new IntakeFieldGroupResponse(groups.stream().map(GroupResponse::from).toList());
  }

  public record GroupResponse(UUID id, String name, String slug, List<IntakeFieldResponse> fields) {

    public static GroupResponse from(IntakeFieldGroup group) {
      return new GroupResponse(
          group.id(),
          group.name(),
          group.slug(),
          group.fields().stream().map(IntakeFieldResponse::from).toList());
    }
  }

  public record IntakeFieldResponse(
      UUID id,
      String name,
      String slug,
      String fieldType,
      boolean required,
      String description,
      List<Map<String, String>> options,
      Map<String, Object> defaultValue,
      List<String> requiredForContexts) {

    public static IntakeFieldResponse from(FieldDefinition fd) {
      return new IntakeFieldResponse(
          fd.getId(),
          fd.getName(),
          fd.getSlug(),
          fd.getFieldType().name(),
          fd.isRequired(),
          fd.getDescription(),
          fd.getOptions(),
          fd.getDefaultValue(),
          fd.getRequiredForContexts());
    }
  }
}
