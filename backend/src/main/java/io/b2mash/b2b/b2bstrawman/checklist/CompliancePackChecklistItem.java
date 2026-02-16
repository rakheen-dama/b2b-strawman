package io.b2mash.b2b.b2bstrawman.checklist;

/** Nested record for a checklist item within a compliance pack template. */
public record CompliancePackChecklistItem(
    String key,
    String name,
    String description,
    int sortOrder,
    boolean required,
    boolean requiresDocument,
    String requiredDocumentLabel,
    String dependsOnKey) {}
