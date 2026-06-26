package io.b2mash.b2b.b2bstrawman.correspondence;

import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only correspondence list endpoints consumed by the matter-detail "Correspondence" tab. These
 * are plain in-app REST — they mirror the documents-list endpoints' view-access (NOT MCP
 * capabilities; MCP caps gate MCP tools only). View-access + page-size clamping live in {@link
 * CorrespondenceService}; the controller is a pure HTTP adapter.
 */
@RestController
public class CorrespondenceController {

  private final CorrespondenceService correspondenceService;

  public CorrespondenceController(CorrespondenceService correspondenceService) {
    this.correspondenceService = correspondenceService;
  }

  @GetMapping("/api/projects/{projectId}/correspondence")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Page<CorrespondenceListResponse>> listProjectCorrespondence(
      @PathVariable UUID projectId, Pageable pageable, ActorContext actor) {
    return ResponseEntity.ok(correspondenceService.listForProject(projectId, actor, pageable));
  }

  @GetMapping("/api/customers/{customerId}/correspondence")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Page<CorrespondenceListResponse>> listCustomerCorrespondence(
      @PathVariable UUID customerId, Pageable pageable, ActorContext actor) {
    return ResponseEntity.ok(correspondenceService.listForCustomer(customerId, actor, pageable));
  }
}
