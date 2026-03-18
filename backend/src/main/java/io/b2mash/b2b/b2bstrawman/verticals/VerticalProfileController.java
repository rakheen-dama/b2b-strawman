package io.b2mash.b2b.b2bstrawman.verticals;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileService.ModuleWithStatus;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileService.ProfileSummary;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for vertical profile and module discovery endpoints. */
@RestController
@RequestMapping("/api")
public class VerticalProfileController {

  private final VerticalProfileService verticalProfileService;

  public VerticalProfileController(VerticalProfileService verticalProfileService) {
    this.verticalProfileService = verticalProfileService;
  }

  /** Returns summaries of all available vertical profiles. */
  @GetMapping("/profiles")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<ProfileSummary>> getProfiles() {
    return ResponseEntity.ok(verticalProfileService.getProfileSummaries());
  }

  /** Returns all known modules with their enabled status for the current tenant. */
  @GetMapping("/modules")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<ModuleWithStatus>> getModules() {
    return ResponseEntity.ok(verticalProfileService.getModulesWithStatus());
  }
}
