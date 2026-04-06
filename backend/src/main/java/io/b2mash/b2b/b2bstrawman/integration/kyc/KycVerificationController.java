package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KycVerificationController {

  private final KycVerificationService kycVerificationService;

  public KycVerificationController(KycVerificationService kycVerificationService) {
    this.kycVerificationService = kycVerificationService;
  }

  @PostMapping("/kyc/verify")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<KycVerifyResponse> verify(@Valid @RequestBody KycVerifyRequest request) {
    return ResponseEntity.ok(
        KycVerifyResponse.from(
            kycVerificationService.verifyIdentity(
                request.customerId(),
                request.checklistInstanceItemId(),
                new KycVerificationRequest(
                    request.idNumber(), request.fullName(), null, request.idDocumentType()),
                request.consentAcknowledged(),
                RequestScopes.requireMemberId())));
  }

  @GetMapping("/kyc/result/{reference}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<KycVerificationResult> getResult(@PathVariable String reference) {
    return ResponseEntity.ok(kycVerificationService.getResult(reference));
  }

  @GetMapping("/integrations/kyc/status")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<KycIntegrationStatusResponse> getKycStatus() {
    return ResponseEntity.ok(kycVerificationService.getKycIntegrationStatus());
  }

  // --- DTOs ---

  public record KycVerifyRequest(
      @NotNull(message = "customerId is required") UUID customerId,
      @NotNull(message = "checklistInstanceItemId is required") UUID checklistInstanceItemId,
      @NotBlank(message = "idNumber is required") String idNumber,
      @NotBlank(message = "fullName is required") String fullName,
      String idDocumentType,
      boolean consentAcknowledged) {}

  public record KycVerifyResponse(
      String status,
      String providerName,
      String providerReference,
      String reasonCode,
      String reasonDescription,
      Instant verifiedAt,
      boolean checklistItemUpdated) {

    public static KycVerifyResponse from(KycVerificationResult result) {
      return new KycVerifyResponse(
          result.status().name(),
          result.providerName(),
          result.providerReference(),
          result.reasonCode(),
          result.reasonDescription(),
          result.verifiedAt(),
          result.status() != KycVerificationStatus.ERROR);
    }
  }
}
