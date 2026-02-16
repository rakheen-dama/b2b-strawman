package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;
import java.util.Map;

/** Nested record for a field definition within a compliance pack. */
public record CompliancePackFieldDefinition(
    String slug,
    String name,
    String fieldType,
    String entityType,
    String groupSlug,
    Map<String, Object> validation,
    List<Map<String, String>> options) {}
