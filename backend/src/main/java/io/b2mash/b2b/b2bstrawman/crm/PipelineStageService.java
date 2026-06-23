package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.exception.DeleteGuard;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuration service for {@link PipelineStage} (Phase 80, §11.2.1). Owns the pipeline
 * invariants:
 *
 * <ul>
 *   <li>The pipeline must always retain at least one non-archived stage of EACH {@link StageType}
 *       (OPEN, WON, LOST). Removing/archiving the last of a type is blocked.
 *   <li>A stage that still has deals cannot be deleted — it must be archived instead (DeleteGuard).
 * </ul>
 *
 * <p>The {@code firstOpenStage()}/{@code firstWonStage()}/{@code firstLostStage()} helpers are used
 * by intake / transition logic in later epics (574A / 575A).
 */
@Service
public class PipelineStageService {

  private final PipelineStageRepository pipelineStageRepository;
  private final DealRepository dealRepository;

  public PipelineStageService(
      PipelineStageRepository pipelineStageRepository, DealRepository dealRepository) {
    this.pipelineStageRepository = pipelineStageRepository;
    this.dealRepository = dealRepository;
  }

  @Transactional(readOnly = true)
  public List<PipelineStage> listStages() {
    return pipelineStageRepository.findAllByOrderByPositionAsc();
  }

  /** Creates a new stage at the given position. */
  @Transactional
  public PipelineStage createStage(
      String name, int position, int defaultProbabilityPct, StageType stageType, UUID createdBy) {
    var stage = new PipelineStage(name, position, defaultProbabilityPct, stageType, createdBy);
    return pipelineStageRepository.save(stage);
  }

  @Transactional
  public PipelineStage renameStage(UUID stageId, String name) {
    var stage = pipelineStageRepository.findOneById(stageId);
    stage.rename(name);
    return pipelineStageRepository.save(stage);
  }

  @Transactional
  public PipelineStage changeDefaultProbability(UUID stageId, int defaultProbabilityPct) {
    var stage = pipelineStageRepository.findOneById(stageId);
    stage.changeDefaultProbability(defaultProbabilityPct);
    return pipelineStageRepository.save(stage);
  }

  /**
   * Changes a stage's type. Blocked if it would leave the pipeline without a non-archived stage of
   * the stage's CURRENT type.
   */
  @Transactional
  public PipelineStage changeStageType(UUID stageId, StageType newType) {
    var stage = pipelineStageRepository.findOneById(stageId);
    applyTypeChange(stage, newType);
    return pipelineStageRepository.save(stage);
  }

  /**
   * Applies a stage-type change to an already-loaded stage, enforcing the last-active-of-type guard
   * first. Shared by {@link #changeStageType} and {@link #updateStage} so the guard logic lives in
   * one place and cannot drift. No-op fields are still written, but the guard only fires when the
   * type actually changes.
   */
  private void applyTypeChange(PipelineStage stage, StageType newType) {
    if (stage.getStageType() != newType) {
      requireNotLastActiveOfType(stage, "change the type of");
    }
    stage.changeStageType(newType);
  }

  /**
   * Consolidated edit used by the HTTP layer (slice 578B). Composes the three granular mutators
   * (type, name, probability) into one transactional call so the controller stays thin and every
   * existing invariant is preserved. The type change runs first so its last-active-of-type guard
   * fires before any field is mutated — a guard failure rolls back the whole edit (single
   * transaction) and never leaves a half-applied rename.
   */
  @Transactional
  public PipelineStage updateStage(
      UUID stageId, String name, int defaultProbabilityPct, StageType newType) {
    var stage = pipelineStageRepository.findOneById(stageId);
    applyTypeChange(stage, newType);
    stage.rename(name);
    stage.changeDefaultProbability(defaultProbabilityPct);
    return pipelineStageRepository.save(stage);
  }

  /**
   * Repositions a single stage. Retained for internal/transition use; the HTTP layer uses the bulk
   * {@link #reorderStages} path instead (slice 578B).
   */
  @Transactional
  public PipelineStage reorderStage(UUID stageId, int newPosition) {
    var stage = pipelineStageRepository.findOneById(stageId);
    stage.changePosition(newPosition);
    return pipelineStageRepository.save(stage);
  }

  /**
   * Bulk-reorders stages to the client's desired ordering (slice 578B). Each {@link
   * StagePositionCommand} pins a stage to a position; positions are assigned directly (per
   * architecture §11.2.1, position is NOT a hard DB-unique constraint — the UI guarantees a
   * conflict-free ordering, so no shifting is performed here). Returns all stages ordered by
   * position, mirroring {@link #listStages}.
   */
  @Transactional
  public List<PipelineStage> reorderStages(List<StagePositionCommand> positions) {
    for (var command : positions) {
      var stage = pipelineStageRepository.findOneById(command.stageId());
      stage.changePosition(command.position());
      pipelineStageRepository.save(stage);
    }
    return pipelineStageRepository.findAllByOrderByPositionAsc();
  }

  /** Command for a single stage's target position in a bulk reorder. */
  public record StagePositionCommand(UUID stageId, int position) {}

  /**
   * Archives a stage. Blocked if it is the last non-archived stage of its {@link StageType}
   * (preserving the always-≥1-of-each-type invariant).
   */
  @Transactional
  public PipelineStage archiveStage(UUID stageId) {
    var stage = pipelineStageRepository.findOneById(stageId);
    requireNotLastActiveOfType(stage, "archive");
    stage.archive();
    return pipelineStageRepository.save(stage);
  }

  /**
   * Deletes a stage. Blocked (DeleteGuard) if any deal references it — the caller must archive
   * instead. Also blocked if it is the last non-archived stage of its type.
   */
  @Transactional
  public void deleteStage(UUID stageId) {
    var stage = pipelineStageRepository.findOneById(stageId);
    DeleteGuard.forEntity("pipeline stage", stageId, "delete")
        .checkNotExists(
            "attached deals",
            () -> dealRepository.existsByStageId(stageId),
            "Archive the stage instead, or move its deals to another stage.")
        .execute();
    // An archived stage does not count toward the active-of-type invariant, so deleting it can
    // never leave the pipeline without an active stage of its type — skip the last-active guard.
    if (!stage.isArchived()) {
      requireNotLastActiveOfType(stage, "delete");
    }
    pipelineStageRepository.delete(stage);
  }

  // --- Transition helpers (used by 574A / 575A) ---

  @Transactional(readOnly = true)
  public PipelineStage firstOpenStage() {
    return requireStageOfType(StageType.OPEN);
  }

  @Transactional(readOnly = true)
  public PipelineStage firstWonStage() {
    return requireStageOfType(StageType.WON);
  }

  @Transactional(readOnly = true)
  public PipelineStage firstLostStage() {
    return requireStageOfType(StageType.LOST);
  }

  // --- Invariant helpers ---

  private PipelineStage requireStageOfType(StageType type) {
    return pipelineStageRepository
        .findFirstByStageTypeAndArchivedFalseOrderByPositionAsc(type)
        .orElseThrow(
            () ->
                new InvalidStateException(
                    "Pipeline misconfigured", "No active " + type + " stage exists"));
  }

  /**
   * Throws if {@code stage} is the only non-archived stage of its type — the pipeline must always
   * keep at least one active OPEN, WON, and LOST stage.
   */
  private void requireNotLastActiveOfType(PipelineStage stage, String action) {
    long activeOfType =
        pipelineStageRepository.countByStageTypeAndArchivedFalse(stage.getStageType());
    if (activeOfType <= 1) {
      throw new InvalidStateException(
          "Pipeline invariant violated",
          "Cannot %s the last active %s stage — every pipeline must keep at least one %s stage."
              .formatted(action, stage.getStageType(), stage.getStageType()));
    }
  }
}
