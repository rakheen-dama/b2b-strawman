package io.b2mash.b2b.b2bstrawman.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
        "Received member sync: externalOrgId={}, externalUserId={}",
        request.externalOrgId(),
        request.externalUserId());

    var result =
        syncService.syncMember(
            request.externalOrgId(),
            request.externalUserId(),
            request.email(),
            request.name(),
            request.avatarUrl(),
            request.orgRole());

    var response =
        new SyncMemberResponse(
            result.memberId(), request.externalUserId(), result.created() ? "created" : "updated");

    if (result.created()) {
      return ResponseEntity.created(URI.create("/internal/members/" + result.memberId()))
          .body(response);
    }

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{externalUserId}")
  public ResponseEntity<Void> deleteMember(
      @PathVariable String externalUserId, @RequestParam String externalOrgId) {
    log.info(
        "Received member delete: externalOrgId={}, externalUserId={}",
        externalOrgId,
        externalUserId);
    syncService.deleteMember(externalOrgId, externalUserId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/stale")
  public ResponseEntity<List<StaleMemberResponse>> listStaleMembers(
      @RequestParam String externalOrgId) {
    log.info("Checking stale members for org: {}", externalOrgId);
    var staleMembers = syncService.findStaleMembers(externalOrgId);
    return ResponseEntity.ok(staleMembers);
  }

  public record SyncMemberRequest(
      @NotBlank(message = "externalOrgId is required") String externalOrgId,
      @NotBlank(message = "externalUserId is required") String externalUserId,
      @NotBlank(message = "email is required") String email,
      String name,
      String avatarUrl,
      @NotBlank(message = "orgRole is required") String orgRole) {}

  public record SyncMemberResponse(UUID memberId, String externalUserId, String action) {}

  public record StaleMemberResponse(
      UUID memberId, String externalUserId, String name, String email) {}
}
