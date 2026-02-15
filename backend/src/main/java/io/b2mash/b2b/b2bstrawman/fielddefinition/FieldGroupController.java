package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldGroupRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldGroupResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldGroupRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
}
