package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.tag.dto.CreateTagRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.tag.dto.UpdateTagRequest;
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
@RequestMapping("/api/tags")
public class TagController {

  private final TagService tagService;

  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

  @GetMapping
  public ResponseEntity<List<TagResponse>> list(@RequestParam(required = false) String search) {
    if (search != null && !search.isBlank()) {
      return ResponseEntity.ok(tagService.search(search));
    }
    return ResponseEntity.ok(tagService.listAll());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
    var response = tagService.create(request);
    return ResponseEntity.created(URI.create("/api/tags/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TagResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateTagRequest request) {
    return ResponseEntity.ok(tagService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    tagService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
