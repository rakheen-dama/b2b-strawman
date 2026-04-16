package io.b2mash.b2b.b2bstrawman.packs;

import java.util.UUID;

/**
 * API response DTO for a pack installation. Maps from {@link PackInstall} entity.
 *
 * @param id the install tracking row UUID
 * @param packId the unique pack identifier
 * @param packType the pack type (e.g. "DOCUMENT_TEMPLATE")
 * @param packVersion the semantic version string
 * @param packName the human-readable pack name
 * @param installedAt ISO-8601 timestamp of installation
 * @param installedByMemberId the member who performed the install, or null for system installs
 * @param itemCount the number of items in the pack
 */
public record PackInstallResponse(
    UUID id,
    String packId,
    String packType,
    String packVersion,
    String packName,
    String installedAt,
    UUID installedByMemberId,
    int itemCount) {

  /** Converts a {@link PackInstall} entity to an API response DTO. */
  public static PackInstallResponse from(PackInstall entity) {
    return new PackInstallResponse(
        entity.getId(),
        entity.getPackId(),
        entity.getPackType().name(),
        entity.getPackVersion(),
        entity.getPackName(),
        entity.getInstalledAt().toString(),
        entity.getInstalledByMemberId(),
        entity.getItemCount());
  }
}
