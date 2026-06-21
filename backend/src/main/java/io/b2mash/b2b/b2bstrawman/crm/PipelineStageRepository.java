package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link PipelineStage}. Schema-per-tenant — {@code findById} is tenant-isolated.
 */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

  /** Tenant-safe single fetch — throws {@link ResourceNotFoundException}, matching convention. */
  default PipelineStage findOneById(UUID id) {
    return findById(id).orElseThrow(() -> new ResourceNotFoundException("PipelineStage", id));
  }

  List<PipelineStage> findAllByOrderByPositionAsc();

  Optional<PipelineStage> findFirstByStageTypeAndArchivedFalseOrderByPositionAsc(
      StageType stageType);

  /** Counts non-archived stages of the given type (used to enforce the ≥1-of-each invariant). */
  long countByStageTypeAndArchivedFalse(StageType stageType);
}
