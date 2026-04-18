package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link MatterClosureLog} (Phase 67, Epic 489A). */
public interface MatterClosureLogRepository extends JpaRepository<MatterClosureLog, UUID> {

  /** All closure log rows for a project, most recent closure first. */
  List<MatterClosureLog> findByProjectIdOrderByClosedAtDesc(UUID projectId);

  /** Most recent closure log row for a project (used to link the closure letter, etc.). */
  Optional<MatterClosureLog> findTopByProjectIdOrderByClosedAtDesc(UUID projectId);
}
