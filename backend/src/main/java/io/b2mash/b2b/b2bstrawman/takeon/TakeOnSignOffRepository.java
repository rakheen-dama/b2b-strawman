package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TakeOnSignOffRepository extends JpaRepository<TakeOnSignOff, UUID> {

  List<TakeOnSignOff> findByBatchIdAndRevokedAtIsNull(UUID batchId);
}
