package io.b2mash.b2b.b2bstrawman.retention;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {

  List<RetentionPolicy> findByActive(boolean active);

  boolean existsByRecordTypeAndTriggerEvent(String recordType, String triggerEvent);

  Optional<RetentionPolicy> findByRecordTypeAndTriggerEvent(String recordType, String triggerEvent);
}
