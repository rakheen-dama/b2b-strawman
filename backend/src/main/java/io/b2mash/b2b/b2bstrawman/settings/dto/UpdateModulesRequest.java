package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request to update the set of enabled horizontal modules for the current org.
 *
 * <p>The payload fully replaces the current set of enabled horizontal modules. Vertical modules are
 * preserved by the service layer and MUST NOT appear in this list — they are managed via the
 * vertical profile.
 *
 * <p>An empty list is valid and disables all horizontal modules. Vertical module IDs or unknown
 * module IDs are rejected with HTTP 400.
 */
public record UpdateModulesRequest(@NotNull List<@NotBlank String> enabledModules) {}
