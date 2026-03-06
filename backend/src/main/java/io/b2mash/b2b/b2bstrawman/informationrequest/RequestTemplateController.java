package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.CreateRequestTemplateRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.RequestTemplateResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.RequestTemplateDtos.UpdateRequestTemplateRequest;
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
@RequestMapping("/api/request-templates")
public class RequestTemplateController {

  private final RequestTemplateService requestTemplateService;

  public RequestTemplateController(RequestTemplateService requestTemplateService) {
    this.requestTemplateService = requestTemplateService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<RequestTemplateResponse>> listTemplates(
      @RequestParam(required = false) Boolean active) {
    return ResponseEntity.ok(requestTemplateService.listTemplates(active));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RequestTemplateResponse> getTemplate(@PathVariable UUID id) {
    return ResponseEntity.ok(requestTemplateService.getById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RequestTemplateResponse> createTemplate(
      @Valid @RequestBody CreateRequestTemplateRequest request) {
    var response = requestTemplateService.create(request);
    return ResponseEntity.created(URI.create("/api/request-templates/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RequestTemplateResponse> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateRequestTemplateRequest request) {
    return ResponseEntity.ok(requestTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deactivateTemplate(@PathVariable UUID id) {
    requestTemplateService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/duplicate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<RequestTemplateResponse> duplicateTemplate(@PathVariable UUID id) {
    var response = requestTemplateService.duplicate(id);
    return ResponseEntity.created(URI.create("/api/request-templates/" + response.id()))
        .body(response);
  }
}
