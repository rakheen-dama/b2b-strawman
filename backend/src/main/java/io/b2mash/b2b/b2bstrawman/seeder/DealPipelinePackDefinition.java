package io.b2mash.b2b.b2bstrawman.seeder;

import java.util.List;

/** DTO record for deserializing deal-pipeline pack JSON files from the classpath (Phase 80). */
public record DealPipelinePackDefinition(
    String packId, String verticalProfile, int version, List<StageEntry> stages) {

  /** A single ordered pipeline stage within a pack. */
  public record StageEntry(
      String name, int position, int defaultProbabilityPct, String stageType) {}
}
