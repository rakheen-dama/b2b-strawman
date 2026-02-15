package io.b2mash.b2b.b2bstrawman.template;

/** DTO record for individual template entries within a template pack JSON file. */
public record TemplatePackTemplate(
    String templateKey,
    String name,
    String category,
    String primaryEntityType,
    String contentFile,
    String cssFile,
    String description,
    int sortOrder) {}
