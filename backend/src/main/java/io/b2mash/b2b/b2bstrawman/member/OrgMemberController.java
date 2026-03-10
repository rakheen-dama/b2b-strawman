package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.MemberCapabilitiesResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<OrgMemberResponse>> listOrgMembers() {
    var members = memberRepository.findAll().stream().map(OrgMemberResponse::from).toList();
    return ResponseEntity.ok(members);
  }

  @GetMapping("/{id}/capabilities")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MemberCapabilitiesResponse> getMemberCapabilities(@PathVariable UUID id) {
    return ResponseEntity.ok(orgRoleService.getMemberCapabilities(id));
  }

  public record OrgMemberResponse(
      UUID id, String name, String email, String avatarUrl, String orgRole) {

    public static OrgMemberResponse from(Member member) {
      return new OrgMemberResponse(
          member.getId(),
          member.getName(),
          member.getEmail(),
          member.getAvatarUrl(),
          member.getOrgRole());
    }
  }
}
