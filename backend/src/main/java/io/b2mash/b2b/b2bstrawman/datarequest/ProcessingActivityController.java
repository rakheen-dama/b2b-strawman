package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/processing-activities")
public class ProcessingActivityController {

  private final ProcessingActivityService processingActivityService;

  public ProcessingActivityController(ProcessingActivityService processingActivityService) {
    this.processingActivityService = processingActivityService;
  }

  @GetMapping
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<Page<ProcessingActivity>> list(Pageable pageable) {
    return ResponseEntity.ok(processingActivityService.list(pageable));
  }

  @PostMapping
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<ProcessingActivity> create(
      @Valid @RequestBody ProcessingActivityRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(processingActivityService.create(request));
  }

  @PutMapping("/{id}")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<ProcessingActivity> update(
      @PathVariable UUID id, @Valid @RequestBody ProcessingActivityRequest request) {
    return ResponseEntity.ok(processingActivityService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    processingActivityService.delete(id);
    return ResponseEntity.noContent().build();
  }

  public record ProcessingActivityRequest(
      @NotBlank String category,
      @NotBlank String description,
      @NotBlank String legalBasis,
      @NotBlank String dataSubjects,
      @NotBlank String retentionPeriod,
      String recipients) {}
}
