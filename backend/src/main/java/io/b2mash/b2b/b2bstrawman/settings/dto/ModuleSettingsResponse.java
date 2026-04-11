package io.b2mash.b2b.b2bstrawman.settings.dto;

import java.util.List;

/**
 * Response shape for {@code GET /api/settings/modules}. Lists the three horizontal modules with
 * their current enabled state for the requesting tenant.
 *
 * <p>Vertical modules are intentionally excluded — they are managed via the vertical profile and
 * are not user-toggleable.
 */
public record ModuleSettingsResponse(List<ModuleStatus> modules) {

  /** Status of a single horizontal module for the current tenant. */
  public record ModuleStatus(String id, String name, String description, boolean enabled) {}
}
