package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.provisioning.ProvisioningService.ProvisionResult;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orgs")
public class ProvisioningController {

  private final ProvisioningService provisioningService;

  public ProvisioningController(ProvisioningService provisioningService) {
    this.provisioningService = provisioningService;
  }

  @PostMapping("/provision")
  public ResponseEntity<ProvisionResponse> provision(
      @RequestBody ProvisionRequest request,
      @RequestHeader(value = "X-Svix-Id", required = false) String svixId) {
    ProvisionResult result =
        provisioningService.provisionOrganization(
            request.clerkOrgId(), request.orgName(), request.slug(), svixId);

    ProvisionResponse response =
        new ProvisionResponse(
            result.organization().getClerkOrgId(), result.organization().getProvisioningStatus());

    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(response);
  }

  @PostMapping("/update")
  public ResponseEntity<Void> updateOrg(
      @RequestBody OrgUpdateRequest request,
      @RequestHeader(value = "X-Svix-Id", required = false) String svixId) {
    boolean applied =
        provisioningService.updateOrganization(
            request.clerkOrgId(), request.orgName(), request.slug(), request.updatedAt(), svixId);
    return applied ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
  }

  public record ProvisionRequest(String clerkOrgId, String orgName, String slug) {}

  public record ProvisionResponse(String clerkOrgId, String status) {}

  public record OrgUpdateRequest(
      String clerkOrgId, String orgName, String slug, Instant updatedAt) {}
}
