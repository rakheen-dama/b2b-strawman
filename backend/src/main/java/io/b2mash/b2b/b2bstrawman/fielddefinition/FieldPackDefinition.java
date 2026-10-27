package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.seeder.ReconcilablePack;
import java.util.List;

/**
 * DTO record for deserializing field pack JSON files from the classpath. {@code reconcile} is the
 * optional ADR-356 block ({@code null} when absent).
 */
public record FieldPackDefinition(
    String packId,
    int version,
    String verticalProfile,
    String entityType,
    FieldPackGroup group,
    List<FieldPackField> fields,
    List<PackReconcileEntry> reconcile)
    implements ReconcilablePack {}
