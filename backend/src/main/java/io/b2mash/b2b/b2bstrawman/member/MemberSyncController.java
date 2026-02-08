package io.b2mash.b2b.b2bstrawman.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/members")
public class MemberSyncController {

  private static final Logger log = LoggerFactory.getLogger(MemberSyncController.class);

  private final MemberSyncService syncService;

  public MemberSyncController(MemberSyncService syncService) {
    this.syncService = syncService;
  }

  @PostMapping("/sync")
  public ResponseEntity<SyncMemberResponse> syncMember(
      @Valid @RequestBody SyncMemberRequest request) {
    log.info(
        "Received member sync: clerkOrgId={}, clerkUserId={}",
        request.clerkOrgId(),
        request.clerkUserId());

    var result =
        syncService.syncMember(
            request.clerkOrgId(),
            request.clerkUserId(),
            request.email(),
            request.name(),
            request.avatarUrl(),
            request.orgRole());

    var response =
        new SyncMemberResponse(
            result.memberId(), request.clerkUserId(), result.created() ? "created" : "updated");

    if (result.created()) {
      return ResponseEntity.created(URI.create("/internal/members/" + result.memberId()))
          .body(response);
    }

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{clerkUserId}")
  public ResponseEntity<?> deleteMember(
      @PathVariable String clerkUserId, @RequestParam String clerkOrgId) {
    log.info("Received member delete: clerkOrgId={}, clerkUserId={}", clerkOrgId, clerkUserId);

    boolean deleted = syncService.deleteMember(clerkOrgId, clerkUserId);
    if (deleted) {
      return ResponseEntity.noContent().build();
    }

    var problem = ProblemDetail.forStatus(404);
    problem.setTitle("Member not found");
    problem.setDetail("No member found with clerkUserId: " + clerkUserId);
    return ResponseEntity.of(problem).build();
  }

  public record SyncMemberRequest(
      @NotBlank(message = "clerkOrgId is required") String clerkOrgId,
      @NotBlank(message = "clerkUserId is required") String clerkUserId,
      @NotBlank(message = "email is required") String email,
      String name,
      String avatarUrl,
      @NotBlank(message = "orgRole is required") String orgRole) {}

  public record SyncMemberResponse(UUID memberId, String clerkUserId, String action) {}
}
