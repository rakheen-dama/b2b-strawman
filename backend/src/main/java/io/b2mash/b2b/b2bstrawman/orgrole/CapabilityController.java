package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.MyCapabilitiesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CapabilityController {

  private final OrgRoleService orgRoleService;

  public CapabilityController(OrgRoleService orgRoleService) {
    this.orgRoleService = orgRoleService;
  }

  @GetMapping("/capabilities")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MyCapabilitiesResponse> getMyCapabilities() {
    return ResponseEntity.ok(orgRoleService.getMyCapabilities(RequestScopes.requireMemberId()));
  }
}
