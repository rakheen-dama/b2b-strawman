package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.LeaveBlockResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.UpdateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LeaveBlockController {

  private final LeaveBlockService leaveBlockService;

  public LeaveBlockController(LeaveBlockService leaveBlockService) {
    this.leaveBlockService = leaveBlockService;
  }

  @GetMapping("/api/members/{memberId}/leave")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<LeaveBlockResponse>> listLeaveForMember(@PathVariable UUID memberId) {
    return ResponseEntity.ok(leaveBlockService.listLeaveForMember(memberId));
  }

  @PostMapping("/api/members/{memberId}/leave")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<LeaveBlockResponse> createLeaveBlock(
      @PathVariable UUID memberId, @Valid @RequestBody CreateLeaveRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(leaveBlockService.createLeaveBlock(memberId, request, createdBy));
  }

  @PutMapping("/api/members/{memberId}/leave/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<LeaveBlockResponse> updateLeaveBlock(
      @PathVariable UUID memberId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateLeaveRequest request) {
    return ResponseEntity.ok(leaveBlockService.updateLeaveBlock(memberId, id, request));
  }

  @DeleteMapping("/api/members/{memberId}/leave/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<Void> deleteLeaveBlock(@PathVariable UUID memberId, @PathVariable UUID id) {
    leaveBlockService.deleteLeaveBlock(memberId, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/leave")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<LeaveBlockResponse>> listAllLeave(
      @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
    return ResponseEntity.ok(leaveBlockService.listAllLeave(startDate, endDate));
  }
}
