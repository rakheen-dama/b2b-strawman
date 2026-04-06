package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates KYC identity verification by resolving the tenant's configured adapter, invoking the
 * verification, updating the checklist item with results, and emitting audit events.
 */
@Service
public class KycVerificationService {

  private static final Logger log = LoggerFactory.getLogger(KycVerificationService.class);

  private final IntegrationRegistry integrationRegistry;
  private final ChecklistInstanceItemRepository checklistInstanceItemRepository;
  private final ChecklistInstanceRepository checklistInstanceRepository;
  private final AuditService auditService;
  private final OrgIntegrationRepository orgIntegrationRepository;

  public KycVerificationService(
      IntegrationRegistry integrationRegistry,
      ChecklistInstanceItemRepository checklistInstanceItemRepository,
      ChecklistInstanceRepository checklistInstanceRepository,
      AuditService auditService,
      OrgIntegrationRepository orgIntegrationRepository) {
    this.integrationRegistry = integrationRegistry;
    this.checklistInstanceItemRepository = checklistInstanceItemRepository;
    this.checklistInstanceRepository = checklistInstanceRepository;
    this.auditService = auditService;
    this.orgIntegrationRepository = orgIntegrationRepository;
  }

  /**
   * Verifies a person's identity for a checklist item. Updates the checklist item with verification
   * results and auto-completes it on VERIFIED status.
   *
   * @param customerId the customer whose checklist item is being verified
   * @param checklistInstanceItemId the checklist item to update with verification results
   * @param request the verification request containing identity details
   * @param consentAcknowledged whether POPIA consent has been acknowledged (must be true)
   * @param actorMemberId the member performing the verification
   * @return the verification result from the provider
   * @throws InvalidStateException if consent is not acknowledged
   * @throws ResourceNotFoundException if the checklist item is not found
   */
  @Transactional
  public KycVerificationResult verifyIdentity(
      UUID customerId,
      UUID checklistInstanceItemId,
      KycVerificationRequest request,
      boolean consentAcknowledged,
      UUID actorMemberId) {

    // 1. Validate POPIA consent
    if (!consentAcknowledged) {
      throw new InvalidStateException(
          "Consent required",
          "POPIA consent must be acknowledged before performing KYC verification");
    }

    // 2. Find the checklist item
    var item =
        checklistInstanceItemRepository
            .findById(checklistInstanceItemId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "ChecklistInstanceItem", checklistInstanceItemId));

    // 2b. Verify checklist item belongs to the specified customer
    var instance =
        checklistInstanceRepository
            .findById(item.getInstanceId())
            .orElseThrow(
                () -> new ResourceNotFoundException("ChecklistInstance", item.getInstanceId()));
    if (!instance.getCustomerId().equals(customerId)) {
      throw new ResourceNotFoundException("ChecklistInstanceItem", checklistInstanceItemId);
    }

    // 3. Resolve the KYC adapter for the tenant
    var adapter =
        integrationRegistry.resolve(IntegrationDomain.KYC_VERIFICATION, KycVerificationPort.class);

    // 4. Emit initiated audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("kyc_verification.initiated")
            .entityType("checklist_instance_item")
            .entityId(checklistInstanceItemId)
            .details(
                Map.of(
                    "customerId", customerId.toString(),
                    "provider", adapter.providerId(),
                    "idDocumentType",
                        request.idDocumentType() != null ? request.idDocumentType() : ""))
            .build());

    // 5. Call the adapter
    KycVerificationResult result;
    try {
      result = adapter.verify(request);
    } catch (Exception e) {
      log.error(
          "KYC verification failed for item={}: {}", checklistInstanceItemId, e.getMessage(), e);

      // Emit failed audit event
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("kyc_verification.failed")
              .entityType("checklist_instance_item")
              .entityId(checklistInstanceItemId)
              .details(
                  Map.of(
                      "customerId", customerId.toString(),
                      "provider", adapter.providerId(),
                      "error", e.getMessage() != null ? e.getMessage() : "Unknown error"))
              .build());

      return new KycVerificationResult(
          KycVerificationStatus.ERROR,
          adapter.providerId(),
          null,
          "ADAPTER_EXCEPTION",
          e.getMessage(),
          null,
          Map.of());
    }

    // 6. Update checklist item verification columns (unless ERROR)
    if (result.status() != KycVerificationStatus.ERROR) {
      // Record POPIA consent in verification metadata
      var metadata = new LinkedHashMap<String, Object>();
      if (result.metadata() != null) {
        metadata.putAll(result.metadata());
      }
      metadata.put("consent_acknowledged_at", Instant.now().toString());
      metadata.put("consent_acknowledged_by", actorMemberId.toString());

      // Apply all verification fields atomically (bumps updatedAt)
      item.applyVerificationResult(
          result.providerName(),
          result.providerReference(),
          result.status().name(),
          result.verifiedAt(),
          metadata);

      // 7. If VERIFIED: auto-complete the checklist item
      if (result.status() == KycVerificationStatus.VERIFIED) {
        item.complete(actorMemberId, "KYC verification passed — auto-completed", null);
      }

      checklistInstanceItemRepository.save(item);
    }

    // 8. Emit completed/failed audit event
    var auditEventType =
        result.status() == KycVerificationStatus.ERROR
            ? "kyc_verification.failed"
            : "kyc_verification.completed";
    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("customerId", customerId.toString());
    auditDetails.put("provider", result.providerName());
    auditDetails.put("status", result.status().name());
    if (result.providerReference() != null) {
      auditDetails.put("providerReference", result.providerReference());
    }
    if (result.reasonCode() != null) {
      auditDetails.put("reasonCode", result.reasonCode());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType(auditEventType)
            .entityType("checklist_instance_item")
            .entityId(checklistInstanceItemId)
            .details(Map.copyOf(auditDetails))
            .build());

    return result;
  }

  /**
   * Retrieves a previous KYC verification result by provider reference.
   *
   * @param reference the provider reference to look up
   * @return the verification result reconstructed from checklist item data
   * @throws ResourceNotFoundException if no checklist item has that reference
   */
  @Transactional(readOnly = true)
  public KycVerificationResult getResult(String reference) {
    var item =
        checklistInstanceItemRepository
            .findByVerificationReference(reference)
            .orElseThrow(() -> new ResourceNotFoundException("KYC verification result", reference));

    return new KycVerificationResult(
        item.getVerificationStatus() != null
            ? KycVerificationStatus.valueOf(item.getVerificationStatus())
            : KycVerificationStatus.ERROR,
        item.getVerificationProvider(),
        item.getVerificationReference(),
        null,
        null,
        item.getVerifiedAt(),
        item.getVerificationMetadata() != null ? item.getVerificationMetadata() : Map.of());
  }

  /**
   * Returns whether a KYC integration is configured and enabled for the current tenant.
   *
   * @return status response with configured flag and provider name
   */
  @Transactional(readOnly = true)
  public KycIntegrationStatusResponse getKycIntegrationStatus() {
    return orgIntegrationRepository
        .findByDomain(IntegrationDomain.KYC_VERIFICATION)
        .filter(integration -> integration.isEnabled())
        .map(integration -> new KycIntegrationStatusResponse(true, integration.getProviderSlug()))
        .orElse(new KycIntegrationStatusResponse(false, null));
  }
}
