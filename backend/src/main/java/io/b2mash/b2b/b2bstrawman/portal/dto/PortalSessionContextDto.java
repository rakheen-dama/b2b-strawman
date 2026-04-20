package io.b2mash.b2b.b2bstrawman.portal.dto;

import java.util.List;

/**
 * Response payload for {@code GET /portal/session/context}.
 *
 * <p>Bundles per-tenant vertical profile identity, enabled module IDs, derived terminology key, and
 * branding into a single payload so the customer portal avoids waterfall fetches on cold load.
 *
 * <p>Fields are nullable where the underlying tenant data is absent (e.g. no logo configured ->
 * {@code logoUrl} null; generic/unset profile -> {@code tenantProfile} null and {@code
 * terminologyKey} empty string).
 */
public record PortalSessionContextDto(
    String tenantProfile,
    List<String> enabledModules,
    String terminologyKey,
    String brandColor,
    String orgName,
    String logoUrl) {}
