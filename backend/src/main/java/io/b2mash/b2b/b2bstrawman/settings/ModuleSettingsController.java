package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsController.SettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.ModuleSettingsResponse;
import io.b2mash.b2b.b2bstrawman.settings.dto.UpdateModulesRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for horizontal module toggle endpoints (Settings → Features).
 *
 * <p>Vertical modules are managed via {@code PATCH /api/settings/vertical-profile} and are NOT
 * exposed here — they follow the profile selection rather than manual toggles.
 */
@RestController
@RequestMapping("/api/settings/modules")
public class ModuleSettingsController {

  private final OrgSettingsService orgSettingsService;

  public ModuleSettingsController(OrgSettingsService orgSettingsService) {
    this.orgSettingsService = orgSettingsService;
  }

  /** Returns the list of horizontal modules with their current enabled state. */
  @GetMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<ModuleSettingsResponse> getModules() {
    return ResponseEntity.ok(orgSettingsService.getHorizontalModuleSettings());
  }

  /**
   * Replaces the set of enabled horizontal modules for the current org. The request payload fully
   * replaces the horizontal subset; vertical modules are preserved.
   */
  @PutMapping
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<SettingsResponse> updateModules(
      @Valid @RequestBody UpdateModulesRequest request, ActorContext actor) {
    return ResponseEntity.ok(
        orgSettingsService.updateHorizontalModules(request.enabledModules(), actor));
  }
}
