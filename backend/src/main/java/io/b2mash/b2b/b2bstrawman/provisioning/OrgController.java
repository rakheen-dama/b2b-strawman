package io.b2mash.b2b.b2bstrawman.provisioning;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orgs")
public class OrgController {

  private final OrgProvisioningService orgProvisioningService;

  public OrgController(OrgProvisioningService orgProvisioningService) {
    this.orgProvisioningService = orgProvisioningService;
  }

  @PostMapping
  public ResponseEntity<OrgProvisioningService.CreateOrgResponse> createOrg(
      @Valid @RequestBody OrgProvisioningService.CreateOrgRequest request) {
    var response = orgProvisioningService.createOrg(request);
    return ResponseEntity.created(URI.create("/api/orgs/" + response.orgId())).body(response);
  }
}
