package io.b2mash.b2b.b2bstrawman.packs;

/**
 * Catalog metadata DTO for a content pack. Used to present available and installed packs to the
 * user.
 *
 * @param packId unique identifier for the pack (e.g. "common", "legal-za")
 * @param name human-readable pack name
 * @param description short description of the pack contents
 * @param version semantic version string
 * @param type the pack type (DOCUMENT_TEMPLATE or AUTOMATION_TEMPLATE)
 * @param verticalProfile vertical profile this pack targets, or null for generic packs
 * @param itemCount number of items (templates/rules) in the pack
 * @param installed whether this pack is currently installed in the tenant
 * @param installedAt ISO-8601 timestamp of installation, or null if not installed
 */
public record PackCatalogEntry(
    String packId,
    String name,
    String description,
    String version,
    PackType type,
    String verticalProfile,
    int itemCount,
    boolean installed,
    String installedAt) {}
