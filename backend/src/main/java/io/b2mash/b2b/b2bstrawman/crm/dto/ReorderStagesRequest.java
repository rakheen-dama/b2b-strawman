package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Bulk reorder request (Phase 80, slice 578B). Per the architecture (§ pipeline-stages endpoints),
 * reorder is a single {@code PUT /api/pipeline/stages/reorder} carrying the full desired ordering
 * as an array of {@code {id, position}} pairs — the drag-reorder UI (579B) sends one request, not
 * O(n) sequential calls.
 *
 * @param positions the desired ordering (non-empty); each element pins a stage to a position
 */
public record ReorderStagesRequest(
    @NotEmpty(message = "positions must not be empty") @Valid List<StagePosition> positions) {

  /**
   * One stage's target position.
   *
   * @param id stage id (required)
   * @param position target ordinal position (required, >= 0)
   */
  public record StagePosition(
      @NotNull(message = "id is required") UUID id,
      @NotNull(message = "position is required") @Min(value = 0, message = "position must be >= 0")
          Integer position) {}
}
