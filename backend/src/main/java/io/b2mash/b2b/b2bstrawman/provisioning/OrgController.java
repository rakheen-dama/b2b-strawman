package io.b2mash.b2b.b2bstrawman.provisioning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orgs")
@PreAuthorize("@platformSecurityService.isPlatformAdmin()")
public class OrgController {

  private final OrgProvisioningService orgProvisioningService;

  public OrgController(OrgProvisioningService orgProvisioningService) {
    this.orgProvisioningService = orgProvisioningService;
  }

  @PostMapping
  public ResponseEntity<CreateOrgResponse> createOrg(@Valid @RequestBody CreateOrgRequest request) {
    var response = orgProvisioningService.createOrg(request);
    return ResponseEntity.created(URI.create("/api/orgs/" + response.orgId())).body(response);
  }

  public record CreateOrgRequest(
      @NotBlank(message = "Organization name is required") String name) {}

  public record CreateOrgResponse(String orgId, String slug) {}
}
