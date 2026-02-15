package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;

/** DTO record for deserializing field pack JSON files from the classpath. */
public record FieldPackDefinition(
    String packId,
    int version,
    String entityType,
    FieldPackGroup group,
    List<FieldPackField> fields) {}
