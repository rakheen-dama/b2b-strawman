package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionPolicyService {

  private final RetentionPolicyRepository policyRepository;

  public RetentionPolicyService(RetentionPolicyRepository policyRepository) {
    this.policyRepository = policyRepository;
  }

  @Transactional(readOnly = true)
  public List<RetentionPolicy> listActive() {
    return policyRepository.findByActive(true);
  }

  @Transactional
  public RetentionPolicy create(
      String recordType, int retentionDays, String triggerEvent, String action) {
    if (policyRepository.existsByRecordTypeAndTriggerEvent(recordType, triggerEvent)) {
      throw new ResourceConflictException(
          "Policy already exists",
          "A retention policy for recordType="
              + recordType
              + " and triggerEvent="
              + triggerEvent
              + " already exists");
    }
    var policy = new RetentionPolicy(recordType, retentionDays, triggerEvent, action);
    return policyRepository.save(policy);
  }

  @Transactional
  public RetentionPolicy update(UUID id, int retentionDays, String action) {
    var policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy", id));
    policy.update(retentionDays, action);
    return policyRepository.save(policy);
  }

  @Transactional
  public void delete(UUID id) {
    if (!policyRepository.existsById(id)) {
      throw new ResourceNotFoundException("RetentionPolicy", id);
    }
    policyRepository.deleteById(id);
  }
}
