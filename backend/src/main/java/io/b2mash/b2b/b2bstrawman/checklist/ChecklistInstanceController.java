package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceItemResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.CompleteItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.InstantiateChecklistRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.SkipItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChecklistInstanceController {

  private final ChecklistInstanceService checklistInstanceService;
  private final ChecklistInstanceItemRepository instanceItemRepository;

  public ChecklistInstanceController(
      ChecklistInstanceService checklistInstanceService,
      ChecklistInstanceItemRepository instanceItemRepository) {
    this.checklistInstanceService = checklistInstanceService;
    this.instanceItemRepository = instanceItemRepository;
  }

  @GetMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<ChecklistInstanceResponse>> listForCustomer(
      @PathVariable UUID customerId) {
    var instances = checklistInstanceService.getInstancesForCustomer(customerId);
    var responses =
        instances.stream()
            .map(
                instance -> {
                  var items =
                      instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
                  return ChecklistInstanceResponse.from(instance, items);
                })
            .toList();
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/api/checklist-instances/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ChecklistInstanceResponse> getInstance(@PathVariable UUID id) {
    var instance = checklistInstanceService.getInstance(id);
    var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
    return ResponseEntity.ok(ChecklistInstanceResponse.from(instance, items));
  }

  @PostMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceResponse> instantiateChecklist(
      @PathVariable UUID customerId, @RequestBody InstantiateChecklistRequest request) {
    var instance = checklistInstanceService.createFromTemplate(request.templateId(), customerId);
    var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
    return ResponseEntity.created(URI.create("/api/checklist-instances/" + instance.getId()))
        .body(ChecklistInstanceResponse.from(instance, items));
  }

  @PutMapping("/api/checklist-items/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> completeItem(
      @PathVariable UUID id, @RequestBody CompleteItemRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var item =
        checklistInstanceService.completeItem(id, request.notes(), request.documentId(), actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item));
  }

  @PutMapping("/api/checklist-items/{id}/skip")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> skipItem(
      @PathVariable UUID id, @RequestBody SkipItemRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var item = checklistInstanceService.skipItem(id, request.reason(), actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item));
  }

  @PutMapping("/api/checklist-items/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> reopenItem(@PathVariable UUID id) {
    UUID actorId = RequestScopes.requireMemberId();
    var item = checklistInstanceService.reopenItem(id, actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item));
  }
}
