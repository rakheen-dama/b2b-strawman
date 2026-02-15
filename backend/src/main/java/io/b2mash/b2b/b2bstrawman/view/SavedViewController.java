package io.b2mash.b2b.b2bstrawman.view;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
@RequestMapping("/api/views")
public class SavedViewController {

  private final SavedViewService savedViewService;

  public SavedViewController(SavedViewService savedViewService) {
    this.savedViewService = savedViewService;
  }

  @GetMapping
  public ResponseEntity<List<SavedViewResponse>> list(@RequestParam String entityType) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(savedViewService.listViews(entityType, memberId));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<SavedViewResponse> create(
      @Valid @RequestBody CreateSavedViewRequest request) {
    var response = savedViewService.create(request);
    return ResponseEntity.created(URI.create("/api/views/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  public ResponseEntity<SavedViewResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateSavedViewRequest request) {
    return ResponseEntity.ok(savedViewService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    savedViewService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
