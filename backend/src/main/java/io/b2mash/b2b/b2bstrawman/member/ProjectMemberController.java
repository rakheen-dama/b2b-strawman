package io.b2mash.b2b.b2bstrawman.member;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matters/{matterId}/members")
public class ProjectMemberController {

  private final ProjectMemberService projectMemberService;

  public ProjectMemberController(ProjectMemberService projectMemberService) {
    this.projectMemberService = projectMemberService;
  }

  @GetMapping
  public ResponseEntity<List<MatterMemberResponse>> listMembers(@PathVariable UUID matterId) {
    var members =
        projectMemberService.listProjectMembers(matterId).stream()
            .map(MatterMemberResponse::from)
            .toList();
    return ResponseEntity.ok(members);
  }

  @PostMapping
  public ResponseEntity<Void> addMember(
      @PathVariable UUID matterId,
      @Valid @RequestBody AddMemberRequest request,
      ActorContext actor) {
    var projectMember = projectMemberService.addMember(matterId, request.memberId(), actor);
    return ResponseEntity.created(
            URI.create("/api/matters/" + matterId + "/members/" + projectMember.getMemberId()))
        .build();
  }

  @DeleteMapping("/{memberId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID matterId, @PathVariable UUID memberId, ActorContext actor) {
    projectMemberService.removeMember(matterId, memberId, actor);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{memberId}/role")
  public ResponseEntity<Void> transferLead(
      @PathVariable UUID matterId,
      @PathVariable UUID memberId,
      @Valid @RequestBody TransferLeadRequest request,
      ActorContext actor) {
    projectMemberService.transferLead(matterId, actor, memberId);
    return ResponseEntity.noContent().build();
  }

  public record AddMemberRequest(@NotNull(message = "memberId is required") UUID memberId) {}

  public record TransferLeadRequest(@NotNull(message = "role is required") String role) {}

  public record MatterMemberResponse(
      UUID id,
      UUID memberId,
      String name,
      String email,
      String avatarUrl,
      String matterRole,
      String orgRole,
      Instant createdAt) {

    public static MatterMemberResponse from(ProjectMemberInfo info) {
      return new MatterMemberResponse(
          info.id(),
          info.memberId(),
          info.name(),
          info.email(),
          info.avatarUrl(),
          info.projectRole(),
          info.orgRole(),
          info.createdAt());
    }
  }
}
