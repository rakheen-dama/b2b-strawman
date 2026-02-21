package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceItemResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.CompleteItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.InstantiateChecklistRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.SkipItemRequest;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final MemberRepository memberRepository;

  public ChecklistInstanceController(
      ChecklistInstanceService checklistInstanceService, MemberRepository memberRepository) {
    this.checklistInstanceService = checklistInstanceService;
    this.memberRepository = memberRepository;
  }

  @GetMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<ChecklistInstanceResponse>> listForCustomer(
      @PathVariable UUID customerId) {
    var responses = checklistInstanceService.getInstancesWithItemsForCustomer(customerId);
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/api/checklist-instances/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ChecklistInstanceResponse> getInstance(@PathVariable UUID id) {
    var response = checklistInstanceService.getInstanceWithItems(id);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/api/customers/{customerId}/checklists")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceResponse> instantiateChecklist(
      @PathVariable UUID customerId, @Valid @RequestBody InstantiateChecklistRequest request) {
    var response =
        checklistInstanceService.createFromTemplateWithItems(request.templateId(), customerId);
    return ResponseEntity.created(URI.create("/api/checklist-instances/" + response.id()))
        .body(response);
  }

  @PutMapping("/api/checklist-items/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> completeItem(
      @PathVariable UUID id, @Valid @RequestBody CompleteItemRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var item =
        checklistInstanceService.completeItem(id, request.notes(), request.documentId(), actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item, resolveItemNames(item)));
  }

  @PutMapping("/api/checklist-items/{id}/skip")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> skipItem(
      @PathVariable UUID id, @Valid @RequestBody SkipItemRequest request) {
    UUID actorId = RequestScopes.requireMemberId();
    var item = checklistInstanceService.skipItem(id, request.reason(), actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item, resolveItemNames(item)));
  }

  @PutMapping("/api/checklist-items/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistInstanceItemResponse> reopenItem(@PathVariable UUID id) {
    UUID actorId = RequestScopes.requireMemberId();
    var item = checklistInstanceService.reopenItem(id, actorId);
    return ResponseEntity.ok(ChecklistInstanceItemResponse.from(item, resolveItemNames(item)));
  }

  private Map<UUID, String> resolveItemNames(ChecklistInstanceItem item) {
    if (item.getCompletedBy() == null) return Map.of();
    return memberRepository.findAllById(List.of(item.getCompletedBy())).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }
}
