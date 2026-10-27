package io.b2mash.b2b.b2bstrawman.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.seeder.ReconcilablePack;
import java.util.List;

/**
 * DTO record for deserializing template pack JSON files from the classpath. {@code reconcile} is
 * the optional ADR-356 block ({@code null} when absent).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplatePackDefinition(
    String packId,
    int version,
    String verticalProfile,
    String name,
    String description,
    List<TemplatePackTemplate> templates,
    List<PackReconcileEntry> reconcile)
    implements ReconcilablePack {}
