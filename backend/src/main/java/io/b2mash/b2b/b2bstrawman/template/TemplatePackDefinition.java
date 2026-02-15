package io.b2mash.b2b.b2bstrawman.template;

import java.util.List;

/** DTO record for deserializing template pack JSON files from the classpath. */
public record TemplatePackDefinition(
    String packId,
    int version,
    String name,
    String description,
    List<TemplatePackTemplate> templates) {}
