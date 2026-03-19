package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnonymizationController {

  private final DataAnonymizationService dataAnonymizationService;

  public AnonymizationController(DataAnonymizationService dataAnonymizationService) {
    this.dataAnonymizationService = dataAnonymizationService;
  }

  @PostMapping("/api/customers/{customerId}/anonymize")
  @RequiresCapability("MANAGE_COMPLIANCE_DESTRUCTIVE")
  public ResponseEntity<DataAnonymizationService.AnonymizationResult> anonymizeCustomer(
      @PathVariable UUID customerId, @Valid @RequestBody AnonymizationRequest body) {
    return ResponseEntity.ok(
        dataAnonymizationService.anonymizeCustomer(
            customerId, body.confirmationName(), body.reason(), RequestScopes.requireMemberId()));
  }

  @GetMapping("/api/customers/{customerId}/anonymize/preview")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<AnonymizationPreview> previewAnonymization(@PathVariable UUID customerId) {
    return ResponseEntity.ok(dataAnonymizationService.previewAnonymization(customerId));
  }

  public record AnonymizationRequest(@NotBlank String confirmationName, String reason) {}
}
