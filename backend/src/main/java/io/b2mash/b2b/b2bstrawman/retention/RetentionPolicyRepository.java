package io.b2mash.b2b.b2bstrawman.retention;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

  List<RetentionPolicy> findByActive(boolean active);

  boolean existsByRecordTypeAndTriggerEvent(String recordType, String triggerEvent);

  Optional<RetentionPolicy> findByRecordTypeAndTriggerEvent(String recordType, String triggerEvent);

  /**
   * Atomic insert-if-absent for a tenant-wide retention policy. Uses Postgres {@code ON CONFLICT
   * ... DO NOTHING} against the {@code uq_retention_policies_type_trigger} unique constraint so
   * concurrent callers (e.g. two simultaneous matter closures on the same tenant) cannot both
   * succeed past the existence check and then race to flush conflicting inserts. Returns the number
   * of rows inserted (0 when the row already exists, 1 when newly inserted).
   *
   * <p>Used by {@code MatterClosureService.ensureMatterRetentionPolicy} for GAP-L-96 / ADR-249.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO retention_policies (record_type, retention_days, trigger_event, action) "
              + "VALUES (:recordType, :retentionDays, :triggerEvent, :action) "
              + "ON CONFLICT ON CONSTRAINT uq_retention_policies_type_trigger DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("recordType") String recordType,
      @Param("retentionDays") int retentionDays,
      @Param("triggerEvent") String triggerEvent,
      @Param("action") String action);
}
