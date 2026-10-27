package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.seeder.ReconcilablePack;
import java.util.List;

/**
 * DTO record for deserializing request pack JSON files from the classpath. {@code reconcile} is the
 * optional ADR-356 block ({@code null} when absent).
 */
public record RequestPackDefinition(
    String packId,
    int version,
    String verticalProfile,
    String name,
    String description,
    List<RequestPackItemDefinition> items,
    List<PackReconcileEntry> reconcile)
    implements ReconcilablePack {

  public record RequestPackItemDefinition(
      String name,
      String description,
      String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {}
}
