package io.b2mash.b2b.b2bstrawman.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** DTO record for deserializing template pack JSON files from the classpath. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplatePackDefinition(
    String packId,
    int version,
    String verticalProfile,
    String name,
    String description,
    List<TemplatePackTemplate> templates) {}
