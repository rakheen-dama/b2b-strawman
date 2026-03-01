package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldDefinitionRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.IntakeFieldGroupResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.PatchFieldDefinitionRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldDefinitionRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/field-definitions")
public class FieldDefinitionController {

  private final FieldDefinitionService fieldDefinitionService;

  public FieldDefinitionController(FieldDefinitionService fieldDefinitionService) {
    this.fieldDefinitionService = fieldDefinitionService;
  }

  @GetMapping
  public ResponseEntity<List<FieldDefinitionResponse>> list(@RequestParam EntityType entityType) {
    return ResponseEntity.ok(fieldDefinitionService.listByEntityType(entityType));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FieldDefinitionResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(fieldDefinitionService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldDefinitionResponse> create(
      @Valid @RequestBody CreateFieldDefinitionRequest request) {
    var response = fieldDefinitionService.create(request);
    return ResponseEntity.created(URI.create("/api/field-definitions/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldDefinitionResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateFieldDefinitionRequest request) {
    return ResponseEntity.ok(fieldDefinitionService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    fieldDefinitionService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/intake")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<IntakeFieldGroupResponse> getIntakeFields(
      @RequestParam EntityType entityType) {
    return ResponseEntity.ok(fieldDefinitionService.getIntakeFieldGroupResponse(entityType));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<FieldDefinitionResponse> patchRequiredForContexts(
      @PathVariable UUID id, @Valid @RequestBody PatchFieldDefinitionRequest request) {
    return ResponseEntity.ok(fieldDefinitionService.updateRequiredForContexts(id, request));
  }
}
