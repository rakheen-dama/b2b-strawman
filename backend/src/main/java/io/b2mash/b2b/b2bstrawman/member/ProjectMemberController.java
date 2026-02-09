package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
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
  public ResponseEntity<?> addMember(
      @PathVariable UUID projectId, @Valid @RequestBody AddMemberRequest request) {
    if (!RequestScopes.MEMBER_ID.isBound()) {
      return ResponseEntity.of(memberContextMissing()).build();
    }
    UUID addedBy = RequestScopes.MEMBER_ID.get();

    var projectMember = projectMemberService.addMember(projectId, request.memberId(), addedBy);
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/members/" + projectMember.getMemberId()))
        .build();
  }

  @DeleteMapping("/{memberId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> removeMember(@PathVariable UUID projectId, @PathVariable UUID memberId) {
    if (!RequestScopes.MEMBER_ID.isBound()) {
      return ResponseEntity.of(memberContextMissing()).build();
    }
    UUID requestedBy = RequestScopes.MEMBER_ID.get();
    String orgRole = RequestScopes.ORG_ROLE.isBound() ? RequestScopes.ORG_ROLE.get() : null;

    projectMemberService.removeMember(projectId, memberId, requestedBy, orgRole);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{memberId}/role")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> transferLead(
      @PathVariable UUID projectId,
      @PathVariable UUID memberId,
      @Valid @RequestBody TransferLeadRequest request) {
    if (!RequestScopes.MEMBER_ID.isBound()) {
      return ResponseEntity.of(memberContextMissing()).build();
    }
    UUID currentLeadId = RequestScopes.MEMBER_ID.get();

    projectMemberService.transferLead(projectId, currentLeadId, memberId);
    return ResponseEntity.noContent().build();
  }

  private ProblemDetail memberContextMissing() {
    var problem = ProblemDetail.forStatus(500);
    problem.setTitle("Member context not available");
    problem.setDetail("Unable to resolve member identity for request");
    return problem;
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
