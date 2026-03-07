package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.BulkAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.BulkAllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.UpdateAllocationRequest;
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
public class ResourceAllocationController {

  private final ResourceAllocationService allocationService;

  public ResourceAllocationController(ResourceAllocationService allocationService) {
    this.allocationService = allocationService;
  }

  @GetMapping("/api/resource-allocations")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<AllocationResponse>> listAllocations(
      @RequestParam(required = false) UUID memberId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) LocalDate weekStart,
      @RequestParam(required = false) LocalDate weekEnd) {
    return ResponseEntity.ok(
        allocationService.listAllocations(memberId, projectId, weekStart, weekEnd));
  }

  @PostMapping("/api/resource-allocations")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AllocationResponse> createAllocation(
      @Valid @RequestBody CreateAllocationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(allocationService.createAllocation(request, RequestScopes.requireMemberId()));
  }

  @PutMapping("/api/resource-allocations/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<AllocationResponse> updateAllocation(
      @PathVariable UUID id, @Valid @RequestBody UpdateAllocationRequest request) {
    return ResponseEntity.ok(allocationService.updateAllocation(id, request));
  }

  @DeleteMapping("/api/resource-allocations/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteAllocation(@PathVariable UUID id) {
    allocationService.deleteAllocation(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/resource-allocations/bulk")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<BulkAllocationResponse> bulkUpsertAllocations(
      @Valid @RequestBody BulkAllocationRequest request) {
    return ResponseEntity.ok(
        allocationService.bulkUpsertAllocations(
            request.allocations(), RequestScopes.requireMemberId()));
  }
}
