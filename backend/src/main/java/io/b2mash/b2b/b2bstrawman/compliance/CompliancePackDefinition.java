package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.List;

/** DTO record for deserializing compliance pack JSON files from the classpath. */
public record CompliancePackDefinition(
    String packId,
    String name,
    String description,
    String version,
    String jurisdiction,
    String customerType,
    CompliancePackChecklistTemplate checklistTemplate,
    List<CompliancePackFieldDefinition> fieldDefinitions,
    List<CompliancePackRetentionOverride> retentionOverrides) {

  public record CompliancePackChecklistTemplate(
      String name, String slug, boolean autoInstantiate, List<CompliancePackItem> items) {}

  public record CompliancePackItem(
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      String dependsOnItemKey) {}

  public record CompliancePackFieldDefinition(
      String fieldKey,
      String label,
      String fieldType,
      boolean required,
      List<String> options,
      String groupName) {}

  public record CompliancePackRetentionOverride(
      String recordType, String triggerEvent, int retentionDays, String action) {}
}
