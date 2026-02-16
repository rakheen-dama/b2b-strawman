package io.b2mash.b2b.b2bstrawman.checklist;

import java.util.List;

/** Nested record for the checklist template section within a compliance pack. */
public record CompliancePackChecklistTemplate(
    String key,
    String name,
    String description,
    boolean autoInstantiate,
    List<CompliancePackChecklistItem> items) {}
