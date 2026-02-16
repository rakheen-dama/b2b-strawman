package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;

/** DTO record for deserializing compliance pack JSON files from the classpath. */
public record CompliancePackDefinition(
    String packId,
    int version,
    String name,
    String description,
    String jurisdiction,
    String customerType,
    CompliancePackChecklistTemplate checklistTemplate,
    List<CompliancePackFieldDefinition> fieldDefinitions,
    List<CompliancePackRetentionOverride> retentionOverrides) {}
