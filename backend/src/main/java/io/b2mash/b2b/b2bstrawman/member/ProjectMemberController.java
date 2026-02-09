package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
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
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

  private final ProjectMemberService projectMemberService;

  public ProjectMemberController(ProjectMemberService projectMemberService) {
    this.projectMemberService = projectMemberService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectMemberResponse>> listMembers(@PathVariable UUID projectId) {
    var members =
        projectMemberService.listProjectMembers(projectId).stream()
            .map(ProjectMemberResponse::from)
            .toList();
    return ResponseEntity.ok(members);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> addMember(
      @PathVariable UUID projectId, @Valid @RequestBody AddMemberRequest request) {
    UUID addedBy = RequestScopes.requireMemberId();

    var projectMember = projectMemberService.addMember(projectId, request.memberId(), addedBy);
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/members/" + projectMember.getMemberId()))
        .build();
  }

  @DeleteMapping("/{memberId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID projectId, @PathVariable UUID memberId) {
    UUID requestedBy = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    projectMemberService.removeMember(projectId, memberId, requestedBy, orgRole);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{memberId}/role")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> transferLead(
      @PathVariable UUID projectId,
      @PathVariable UUID memberId,
      @Valid @RequestBody TransferLeadRequest request) {
    UUID currentLeadId = RequestScopes.requireMemberId();

    projectMemberService.transferLead(projectId, currentLeadId, memberId);
    return ResponseEntity.noContent().build();
  }

  public record AddMemberRequest(@NotNull(message = "memberId is required") UUID memberId) {}

  public record TransferLeadRequest(@NotNull(message = "role is required") String role) {}

  public record ProjectMemberResponse(
      UUID id,
      UUID memberId,
      String name,
      String email,
      String avatarUrl,
      String projectRole,
      Instant createdAt) {

    public static ProjectMemberResponse from(ProjectMemberInfo info) {
      return new ProjectMemberResponse(
          info.id(),
          info.memberId(),
          info.name(),
          info.email(),
          info.avatarUrl(),
          info.projectRole(),
          info.createdAt());
    }
  }
}
