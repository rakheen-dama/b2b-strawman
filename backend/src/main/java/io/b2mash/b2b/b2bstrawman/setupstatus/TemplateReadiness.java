package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.List;
import java.util.UUID;

public record TemplateReadiness(
    UUID templateId,
    String templateName,
    String templateSlug,
    boolean ready,
    List<String> missingFields) {}
