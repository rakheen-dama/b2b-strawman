package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {
  Optional<RetentionPolicy> findByRecordTypeAndTriggerEvent(String recordType, String triggerEvent);
}
