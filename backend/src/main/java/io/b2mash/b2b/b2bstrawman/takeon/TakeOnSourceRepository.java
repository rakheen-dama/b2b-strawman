package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TakeOnSourceRepository extends JpaRepository<TakeOnSource, UUID> {

  List<TakeOnSource> findByBatchIdOrderBySectionAsc(UUID batchId);

  Optional<TakeOnSource> findByIdAndBatchId(UUID id, UUID batchId);

  boolean existsByBatchIdAndSection(UUID batchId, TakeOnSection section);

  /** Purged files whose S3 delete has not succeeded yet (retried by the purge job). */
  List<TakeOnSource> findByPurgedAtIsNotNullAndS3KeyIsNotNull();

  /** The S3 object of a purged file is gone: forget its key. */
  @Modifying
  @Query(
      "update TakeOnSource s set s.s3Key = null"
          + " where s.id = :id and s.s3Key = :key and s.purgedAt is not null")
  int forgetStorageKey(@Param("id") UUID id, @Param("key") String key);
}
