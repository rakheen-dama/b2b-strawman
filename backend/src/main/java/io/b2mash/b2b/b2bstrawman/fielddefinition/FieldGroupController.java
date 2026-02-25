package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.AddFieldToGroupRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldGroupRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldGroupResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.ReorderFieldsRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldGroupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/field-groups")
public class FieldGroupController {

  private final FieldGroupService fieldGroupService;

  public FieldGroupController(FieldGroupService fieldGroupService) {
    this.fieldGroupService = fieldGroupService;
  }

  @GetMapping
  public ResponseEntity<List<FieldGroupResponse>> list(@RequestParam EntityType entityType) {
    return ResponseEntity.ok(fieldGroupService.listByEntityType(entityType));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FieldGroupResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(fieldGroupService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldGroupResponse> create(
      @Valid @RequestBody CreateFieldGroupRequest request) {
    var response = fieldGroupService.create(request);
    return ResponseEntity.created(URI.create("/api/field-groups/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldGroupResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateFieldGroupRequest request) {
    return ResponseEntity.ok(fieldGroupService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    fieldGroupService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/auto-apply")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldGroupResponse> toggleAutoApply(
      @PathVariable UUID id, @Valid @RequestBody ToggleAutoApplyRequest request) {
    return ResponseEntity.ok(fieldGroupService.toggleAutoApply(id, request.autoApply()));
  }

  /** Request record for toggling auto-apply on a field group. */
  public record ToggleAutoApplyRequest(@NotNull Boolean autoApply) {}

  // --- Membership endpoints ---

  @PostMapping("/{id}/fields")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> addField(
      @PathVariable UUID id, @Valid @RequestBody AddFieldToGroupRequest request) {
    fieldGroupService.addFieldToGroup(id, request.fieldDefinitionId(), request.sortOrder());
    return ResponseEntity.created(
            URI.create("/api/field-groups/" + id + "/fields/" + request.fieldDefinitionId()))
        .build();
  }

  @DeleteMapping("/{id}/fields/{fieldId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> removeField(@PathVariable UUID id, @PathVariable UUID fieldId) {
    fieldGroupService.removeFieldFromGroup(id, fieldId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/fields/reorder")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> reorderFields(
      @PathVariable UUID id, @Valid @RequestBody ReorderFieldsRequest request) {
    fieldGroupService.reorderFields(id, request.fieldIds());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/fields")
  public ResponseEntity<List<FieldGroupMemberResponse>> getFields(@PathVariable UUID id) {
    var members = fieldGroupService.getGroupMembers(id);
    var response = members.stream().map(FieldGroupMemberResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  /** Response record for field group membership. */
  public record FieldGroupMemberResponse(
      UUID id, UUID fieldGroupId, UUID fieldDefinitionId, int sortOrder) {

    public static FieldGroupMemberResponse from(FieldGroupMember member) {
      return new FieldGroupMemberResponse(
          member.getId(),
          member.getFieldGroupId(),
          member.getFieldDefinitionId(),
          member.getSortOrder());
    }
  }
}
