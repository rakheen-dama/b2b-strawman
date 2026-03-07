package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.CreateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.MemberCapacityResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.UpdateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/{memberId}/capacity")
public class MemberCapacityController {

  private final CapacityService capacityService;

  public MemberCapacityController(CapacityService capacityService) {
    this.capacityService = capacityService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<MemberCapacityResponse>> listCapacity(@PathVariable UUID memberId) {
    return ResponseEntity.ok(capacityService.listCapacityRecords(memberId));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<MemberCapacityResponse> createCapacity(
      @PathVariable UUID memberId, @Valid @RequestBody CreateCapacityRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(capacityService.createCapacityRecord(memberId, request, createdBy));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<MemberCapacityResponse> updateCapacity(
      @PathVariable UUID memberId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCapacityRequest request) {
    return ResponseEntity.ok(capacityService.updateCapacityRecord(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteCapacity(@PathVariable UUID memberId, @PathVariable UUID id) {
    capacityService.deleteCapacityRecord(id);
    return ResponseEntity.noContent().build();
  }
}
