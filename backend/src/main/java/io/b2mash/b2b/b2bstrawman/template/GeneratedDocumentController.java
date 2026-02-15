package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService.GeneratedDocumentListResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generated-documents")
public class GeneratedDocumentController {

  private final GeneratedDocumentService generatedDocumentService;
  private final S3PresignedUrlService s3PresignedUrlService;

  public GeneratedDocumentController(
      GeneratedDocumentService generatedDocumentService,
      S3PresignedUrlService s3PresignedUrlService) {
    this.generatedDocumentService = generatedDocumentService;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<GeneratedDocumentListResponse>> listGeneratedDocuments(
      @RequestParam TemplateEntityType entityType, @RequestParam UUID entityId) {
    var documents = generatedDocumentService.listByEntity(entityType, entityId);
    return ResponseEntity.ok(documents);
  }

  @GetMapping("/{id}/download")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> downloadGeneratedDocument(@PathVariable UUID id) {
    var generatedDoc = generatedDocumentService.getById(id);
    var presigned = s3PresignedUrlService.generateDownloadUrl(generatedDoc.getS3Key());
    return ResponseEntity.status(302).header(HttpHeaders.LOCATION, presigned.url()).build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteGeneratedDocument(@PathVariable UUID id) {
    String orgRole = RequestScopes.getOrgRole();
    generatedDocumentService.delete(id, orgRole);
    return ResponseEntity.noContent().build();
  }
}
