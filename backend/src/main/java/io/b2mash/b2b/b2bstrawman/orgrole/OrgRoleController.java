package io.b2mash.b2b.b2bstrawman.orgrole;

import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.OrgRoleResponse;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.UpdateOrgRoleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/org-roles")
public class OrgRoleController {

  private final OrgRoleService orgRoleService;

  public OrgRoleController(OrgRoleService orgRoleService) {
    this.orgRoleService = orgRoleService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<OrgRoleResponse>> listRoles() {
    return ResponseEntity.ok(orgRoleService.listRoles());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<OrgRoleResponse> getRole(@PathVariable UUID id) {
    return ResponseEntity.ok(orgRoleService.getRole(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<OrgRoleResponse> createRole(
      @Valid @RequestBody CreateOrgRoleRequest request) {
    var response = orgRoleService.createRole(request);
    return ResponseEntity.created(URI.create("/api/org-roles/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<OrgRoleResponse> updateRole(
      @PathVariable UUID id, @Valid @RequestBody UpdateOrgRoleRequest request) {
    return ResponseEntity.ok(orgRoleService.updateRole(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
    orgRoleService.deleteRole(id);
    return ResponseEntity.noContent().build();
  }
}
