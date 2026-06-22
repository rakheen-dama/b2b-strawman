package io.b2mash.b2b.b2bstrawman.crm.dto;

import io.b2mash.b2b.b2bstrawman.crm.PipelineStage;
import io.b2mash.b2b.b2bstrawman.crm.StageType;
import java.util.UUID;

/**
 * Read-model view of a {@link PipelineStage} (Phase 80, §11.4).
 *
 * @param id stage id
 * @param name stage name
 * @param position ordinal position in the pipeline
 * @param defaultProbabilityPct default probability for OPEN deals in this stage
 * @param stageType OPEN / WON / LOST
 * @param archived whether the stage is archived
 */
public record StageDto(
    UUID id,
    String name,
    int position,
    int defaultProbabilityPct,
    StageType stageType,
    boolean archived) {

  public static StageDto from(PipelineStage stage) {
    return new StageDto(
        stage.getId(),
        stage.getName(),
        stage.getPosition(),
        stage.getDefaultProbabilityPct(),
        stage.getStageType(),
        stage.isArchived());
  }
}
