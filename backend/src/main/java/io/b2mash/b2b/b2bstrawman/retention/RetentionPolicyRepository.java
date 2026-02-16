package io.b2mash.b2b.b2bstrawman.retention;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT rp FROM RetentionPolicy rp WHERE rp.id = :id")
  Optional<RetentionPolicy> findOneById(@Param("id") UUID id);

  /** Checks if a retention policy exists for the given record type and trigger event. */
  @Query(
      "SELECT COUNT(rp) > 0 FROM RetentionPolicy rp"
          + " WHERE rp.recordType = :recordType AND rp.triggerEvent = :triggerEvent")
  boolean existsByRecordTypeAndTriggerEvent(
      @Param("recordType") String recordType, @Param("triggerEvent") String triggerEvent);
}
