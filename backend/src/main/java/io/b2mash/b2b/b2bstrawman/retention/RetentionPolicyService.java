package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.datarequest.JurisdictionDefaults;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionPolicyService {

  private static final Set<String> FINANCIAL_RECORD_TYPES = Set.of("CUSTOMER", "TIME_ENTRY");

  private final RetentionPolicyRepository policyRepository;
  private final OrgSettingsRepository orgSettingsRepository;

  public RetentionPolicyService(
      RetentionPolicyRepository policyRepository, OrgSettingsRepository orgSettingsRepository) {
    this.policyRepository = policyRepository;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional(readOnly = true)
  public List<RetentionPolicy> listActive() {
    return policyRepository.findByActive(true);
  }

  @Transactional
  public RetentionPolicy create(
      String recordType, int retentionDays, String triggerEvent, String action) {
    if (recordType == null || recordType.isBlank()) {
      throw new IllegalArgumentException("recordType must not be blank");
    }
    if (triggerEvent == null || triggerEvent.isBlank()) {
      throw new IllegalArgumentException("triggerEvent must not be blank");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action must not be blank");
    }
    if (retentionDays < 0) {
      throw new IllegalArgumentException("retentionDays must not be negative");
    }
    validateFinancialMinimum(recordType, retentionDays);
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
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action must not be blank");
    }
    if (retentionDays < 0) {
      throw new IllegalArgumentException("retentionDays must not be negative");
    }
    var policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy", id));
    validateFinancialMinimum(policy.getRecordType(), retentionDays);
    policy.update(retentionDays, action);
    return policyRepository.save(policy);
  }

  @Transactional
  public RetentionPolicy updateFromRequest(
      UUID id, Integer retentionDays, String action, Boolean enabled, String description) {
    var policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy", id));
    if (retentionDays != null) {
      if (retentionDays < 0) {
        throw new IllegalArgumentException("retentionDays must not be negative");
      }
      validateFinancialMinimumWithJurisdiction(policy.getRecordType(), retentionDays);
      policy.update(retentionDays, policy.getAction());
    }
    if (action != null) {
      if (action.isBlank()) {
        throw new IllegalArgumentException("action must not be blank");
      }
      policy.update(policy.getRetentionDays(), action);
    }
    if (enabled != null) {
      if (enabled) {
        policy.activate();
      } else {
        policy.deactivate();
      }
    }
    if (description != null) {
      policy.setDescription(description);
    }
    return policyRepository.save(policy);
  }

  @Transactional
  public void delete(UUID id) {
    if (!policyRepository.existsById(id)) {
      throw new ResourceNotFoundException("RetentionPolicy", id);
    }
    policyRepository.deleteById(id);
  }

  private void validateFinancialMinimumWithJurisdiction(String recordType, int retentionDays) {
    if (!FINANCIAL_RECORD_TYPES.contains(recordType)) {
      return;
    }
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    int financialRetentionMonths =
        (settings != null && settings.getFinancialRetentionMonths() != null)
            ? settings.getFinancialRetentionMonths()
            : 60;
    String jurisdiction = settings != null ? settings.getDataProtectionJurisdiction() : null;
    int minMonths = JurisdictionDefaults.getMinFinancialRetentionMonths(jurisdiction);
    int effectiveMinMonths = Math.max(financialRetentionMonths, minMonths);
    int minDays = effectiveMinMonths * 30;
    if (retentionDays < minDays) {
      String jurisdictionLabel = jurisdiction != null ? jurisdiction : "default";
      throw new InvalidStateException(
          "Retention period too short",
          "Retention period for financial records cannot be less than "
              + effectiveMinMonths
              + " months (jurisdiction: "
              + jurisdictionLabel
              + ").");
    }
  }

  private void validateFinancialMinimum(String recordType, int retentionDays) {
    if (!FINANCIAL_RECORD_TYPES.contains(recordType)) {
      return;
    }
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    int financialRetentionMonths =
        (settings != null && settings.getFinancialRetentionMonths() != null)
            ? settings.getFinancialRetentionMonths()
            : 60;
    String jurisdiction = settings != null ? settings.getDataProtectionJurisdiction() : null;
    int minMonths = JurisdictionDefaults.getMinFinancialRetentionMonths(jurisdiction);
    int effectiveMinMonths = Math.max(financialRetentionMonths, minMonths);
    int minDays = effectiveMinMonths * 30;
    if (retentionDays < minDays) {
      throw new InvalidStateException(
          "Retention period too short",
          "Financial record types require at least "
              + minDays
              + " days retention (configured financial minimum: "
              + effectiveMinMonths
              + " months)");
    }
  }
}
