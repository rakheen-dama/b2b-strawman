package io.b2mash.b2b.b2bstrawman.provisioning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orgs")
public class ProvisioningController {

  private static final Logger log = LoggerFactory.getLogger(ProvisioningController.class);

  private final TenantProvisioningService provisioningService;

  public ProvisioningController(TenantProvisioningService provisioningService) {
    this.provisioningService = provisioningService;
  }

  @PostMapping("/provision")
  public ResponseEntity<ProvisioningResponse> provisionTenant(
      @Valid @RequestBody ProvisioningRequest request) {
    log.info("Received provisioning request for org {}", request.externalOrgId());

    var result = provisioningService.provisionTenant(request.externalOrgId(), request.orgName());

    if (result.alreadyProvisioned()) {
      return ResponseEntity.status(409)
          .body(
              new ProvisioningResponse(
                  result.schemaName(), "Tenant already provisioned", "COMPLETED"));
    }

    return ResponseEntity.created(URI.create("/internal/orgs/" + request.externalOrgId()))
        .body(
            new ProvisioningResponse(
                result.schemaName(), "Tenant provisioned successfully", "COMPLETED"));
  }

  public record ProvisioningRequest(
      @NotBlank(message = "externalOrgId is required") String externalOrgId,
      @NotBlank(message = "orgName is required") String orgName) {}

  public record ProvisioningResponse(String schemaName, String message, String status) {}
}
