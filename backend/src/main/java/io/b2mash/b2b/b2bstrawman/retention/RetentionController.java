package io.b2mash.b2b.b2bstrawman.retention;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetentionController {

  private final RetentionPolicyService policyService;
  private final RetentionService retentionService;

  public RetentionController(
      RetentionPolicyService policyService, RetentionService retentionService) {
    this.policyService = policyService;
    this.retentionService = retentionService;
  }

  @GetMapping("/api/retention-policies")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<List<PolicyResponse>> listPolicies() {
    var policies = policyService.listActive().stream().map(PolicyResponse::from).toList();
    return ResponseEntity.ok(policies);
  }

  @PostMapping("/api/retention-policies")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody CreatePolicyRequest body) {
    var policy =
        policyService.create(
            body.recordType(), body.retentionDays(), body.triggerEvent(), body.action());
    return ResponseEntity.created(URI.create("/api/retention-policies/" + policy.getId()))
        .body(PolicyResponse.from(policy));
  }

  @PutMapping("/api/retention-policies/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<PolicyResponse> updatePolicy(
      @PathVariable UUID id, @Valid @RequestBody UpdatePolicyRequest body) {
    var policy = policyService.update(id, body.retentionDays(), body.action());
    return ResponseEntity.ok(PolicyResponse.from(policy));
  }

  @DeleteMapping("/api/retention-policies/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
    policyService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/retention-policies/check")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<RetentionCheckResult> runCheck() {
    var result = retentionService.runCheck();
    return ResponseEntity.ok(result);
  }

  @PostMapping("/api/retention-policies/purge")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<RetentionService.PurgeResult> executePurge(
      @Valid @RequestBody PurgeRequest body) {
    var result = retentionService.executePurge(body.recordType(), body.recordIds());
    return ResponseEntity.ok(result);
  }

  // --- Settings endpoints at /api/settings/retention-policies ---

  @GetMapping("/api/settings/retention-policies")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<List<SettingsPolicyResponse>> listSettingsPolicies() {
    return ResponseEntity.ok(policyService.listSettingsPolicies());
  }

  @PutMapping("/api/settings/retention-policies/{id}")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<SettingsPolicyResponse> updateSettingsPolicy(
      @PathVariable UUID id, @Valid @RequestBody RetentionPolicyUpdateRequest body) {
    var policy =
        policyService.updateFromRequest(
            id, body.retentionDays(), body.action(), body.enabled(), body.description());
    return ResponseEntity.ok(SettingsPolicyResponse.from(policy));
  }

  @PostMapping("/api/settings/retention-policies/evaluate")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<RetentionEvaluationResult> evaluatePolicies() {
    return ResponseEntity.ok(retentionService.evaluateForPreview());
  }

  @PostMapping("/api/settings/retention-policies/execute")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<RetentionService.ExecuteResult> executeRetention() {
    return ResponseEntity.ok(retentionService.executeAllPending());
  }

  // DTOs
  public record CreatePolicyRequest(
      @NotBlank String recordType,
      @PositiveOrZero int retentionDays,
      @NotBlank String triggerEvent,
      @NotBlank String action) {}

  public record UpdatePolicyRequest(@PositiveOrZero int retentionDays, @NotBlank String action) {}

  public record PurgeRequest(
      @NotBlank String recordType, @NotNull @NotEmpty List<UUID> recordIds) {}

  public record PolicyResponse(
      UUID id,
      String recordType,
      int retentionDays,
      String triggerEvent,
      String action,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {

    public static PolicyResponse from(RetentionPolicy p) {
      return new PolicyResponse(
          p.getId(),
          p.getRecordType(),
          p.getRetentionDays(),
          p.getTriggerEvent(),
          p.getAction(),
          p.isActive(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }

  public record SettingsPolicyResponse(
      UUID id,
      String recordType,
      int retentionDays,
      String triggerEvent,
      String action,
      boolean active,
      String description,
      Instant lastEvaluatedAt,
      Instant createdAt,
      Instant updatedAt) {

    public static SettingsPolicyResponse from(RetentionPolicy p) {
      return new SettingsPolicyResponse(
          p.getId(),
          p.getRecordType(),
          p.getRetentionDays(),
          p.getTriggerEvent(),
          p.getAction(),
          p.isActive(),
          p.getDescription(),
          p.getLastEvaluatedAt(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }

  public record RetentionPolicyUpdateRequest(
      @Positive Integer retentionDays, String action, Boolean enabled, String description) {}
}
