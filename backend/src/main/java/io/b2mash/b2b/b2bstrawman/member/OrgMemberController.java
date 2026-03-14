package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.AssignRoleRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.MemberCapabilitiesResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class OrgMemberController {

  private final MemberRepository memberRepository;
  private final OrgRoleService orgRoleService;

  public OrgMemberController(MemberRepository memberRepository, OrgRoleService orgRoleService) {
    this.memberRepository = memberRepository;
    this.orgRoleService = orgRoleService;
  }

  @GetMapping
  public ResponseEntity<List<OrgMemberResponse>> listOrgMembers() {
    var members = memberRepository.findAllWithRole().stream().map(OrgMemberResponse::from).toList();
    return ResponseEntity.ok(members);
  }

  @GetMapping("/{id}/capabilities")
  public ResponseEntity<MemberCapabilitiesResponse> getMemberCapabilities(@PathVariable UUID id) {
    return ResponseEntity.ok(orgRoleService.getMemberCapabilities(id));
  }

  @PutMapping("/{id}/role")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<MemberCapabilitiesResponse> assignRole(
      @PathVariable UUID id, @Valid @RequestBody AssignRoleRequest request) {
    return ResponseEntity.ok(orgRoleService.assignRole(id, request));
  }

  public record OrgMemberResponse(
      UUID id, String name, String email, String avatarUrl, String orgRole) {

    public static OrgMemberResponse from(Member member) {
      return new OrgMemberResponse(
          member.getId(),
          member.getName(),
          member.getEmail(),
          member.getAvatarUrl(),
          member.getRoleSlug());
    }
  }
}
