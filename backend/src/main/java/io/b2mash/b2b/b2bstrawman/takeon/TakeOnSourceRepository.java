package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TakeOnSourceRepository extends JpaRepository<TakeOnSource, UUID> {

  List<TakeOnSource> findByBatchIdOrderBySectionAsc(UUID batchId);

  Optional<TakeOnSource> findByIdAndBatchId(UUID id, UUID batchId);

  boolean existsByBatchIdAndSection(UUID batchId, TakeOnSection section);
}
