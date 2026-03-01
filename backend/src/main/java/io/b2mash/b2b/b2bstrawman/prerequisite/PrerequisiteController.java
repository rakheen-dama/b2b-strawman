package io.b2mash.b2b.b2bstrawman.prerequisite;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.prerequisite.dto.PrerequisiteCheckResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prerequisites")
public class PrerequisiteController {

  private final PrerequisiteService prerequisiteService;

  public PrerequisiteController(PrerequisiteService prerequisiteService) {
    this.prerequisiteService = prerequisiteService;
  }

  @GetMapping("/check")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PrerequisiteCheckResponse> check(
      @RequestParam PrerequisiteContext context,
      @RequestParam EntityType entityType,
      @RequestParam UUID entityId) {
    return ResponseEntity.ok(
        PrerequisiteCheckResponse.from(
            prerequisiteService.checkForContext(context, entityType, entityId)));
  }
}
