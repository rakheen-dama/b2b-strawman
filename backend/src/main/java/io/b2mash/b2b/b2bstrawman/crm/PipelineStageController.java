package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.crm.dto.CreateStageRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.ReorderStageRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.StageDto;
import io.b2mash.b2b.b2bstrawman.crm.dto.UpdateStageRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP adapter for {@link PipelineStage} configuration (Phase 80, slice 578B). Exposes the
 * existing {@link PipelineStageService} so the stage-config UI (579B) has endpoints to call. Each
 * method delegates to exactly one service call and wraps the result in a {@link StageDto} / {@link
 * ResponseEntity}. Reads gate on {@code VIEW_DEALS}; writes gate on {@code MANAGE_PIPELINE}.
 *
 * <p>No try/catch — service exceptions ({@code ResourceNotFoundException} → 404, {@code
 * InvalidStateException} last-of-type guard → 400, {@code ResourceConflictException} from the
 * DeleteGuard → 409) are mapped globally.
 */
@RestController
public class PipelineStageController {

  private final PipelineStageService pipelineStageService;

  public PipelineStageController(PipelineStageService pipelineStageService) {
    this.pipelineStageService = pipelineStageService;
  }

  @GetMapping("/api/pipeline/stages")
  @RequiresCapability("VIEW_DEALS")
  public ResponseEntity<List<StageDto>> listStages() {
    return ResponseEntity.ok(
        pipelineStageService.listStages().stream().map(StageDto::from).toList());
  }

  @PostMapping("/api/pipeline/stages")
  @RequiresCapability("MANAGE_PIPELINE")
  public ResponseEntity<StageDto> createStage(@Valid @RequestBody CreateStageRequest request) {
    var stage =
        pipelineStageService.createStage(
            request.name(),
            request.position(),
            request.defaultProbabilityPct(),
            request.stageType(),
            RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/pipeline/stages/" + stage.getId()))
        .body(StageDto.from(stage));
  }

  @PutMapping("/api/pipeline/stages/{id}")
  @RequiresCapability("MANAGE_PIPELINE")
  public ResponseEntity<StageDto> updateStage(
      @PathVariable UUID id, @Valid @RequestBody UpdateStageRequest request) {
    var stage =
        pipelineStageService.updateStage(
            id, request.name(), request.defaultProbabilityPct(), request.stageType());
    return ResponseEntity.ok(StageDto.from(stage));
  }

  @PostMapping("/api/pipeline/stages/{id}/reorder")
  @RequiresCapability("MANAGE_PIPELINE")
  public ResponseEntity<StageDto> reorderStage(
      @PathVariable UUID id, @Valid @RequestBody ReorderStageRequest request) {
    var stage = pipelineStageService.reorderStage(id, request.newPosition());
    return ResponseEntity.ok(StageDto.from(stage));
  }

  @PostMapping("/api/pipeline/stages/{id}/archive")
  @RequiresCapability("MANAGE_PIPELINE")
  public ResponseEntity<StageDto> archiveStage(@PathVariable UUID id) {
    var stage = pipelineStageService.archiveStage(id);
    return ResponseEntity.ok(StageDto.from(stage));
  }

  @DeleteMapping("/api/pipeline/stages/{id}")
  @RequiresCapability("MANAGE_PIPELINE")
  public ResponseEntity<Void> deleteStage(@PathVariable UUID id) {
    pipelineStageService.deleteStage(id);
    return ResponseEntity.noContent().build();
  }
}
