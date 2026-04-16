package io.b2mash.b2b.b2bstrawman.packs;

import java.util.List;

/**
 * Defines the contract for installing and uninstalling content packs within a tenant schema. Each
 * implementation handles a specific {@link PackType} (e.g., document templates, automation
 * templates).
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>{@link #install} is idempotent -- calling it twice for the same packId is a no-op.
 *   <li>{@link #uninstall} MUST call {@link #checkUninstallable} first and throw {@code
 *       ResourceConflictException} if the pack cannot be safely removed.
 *   <li>Uninstall is all-or-nothing: if any content has been edited, referenced, or cloned, the
 *       entire uninstall is blocked.
 * </ul>
 */
public interface PackInstaller {

  /** Returns the pack type this installer handles. */
  PackType type();

  /**
   * Returns the catalog of available packs for this type, loaded from the classpath. The returned
   * entries do not include install-state enrichment (installed/installedAt); that is handled by
   * {@code PackCatalogService}.
   */
  List<PackCatalogEntry> availablePacks();

  /**
   * Installs a content pack for the given tenant. Creates a {@link PackInstall} tracking row and
   * delegates to the underlying seeder to create domain entities. Sets {@code sourcePackInstallId}
   * and {@code contentHash} on each created entity.
   *
   * <p>Idempotent: if a PackInstall already exists for the given packId, this method is a no-op.
   *
   * @param packId the unique pack identifier (e.g., "common")
   * @param tenantId the tenant schema name
   * @param memberId the ID of the member performing the install
   */
  void install(String packId, String tenantId, String memberId);

  /**
   * Checks whether a pack can be safely uninstalled. Returns an {@link UninstallCheck} with gate
   * results. Blocking reasons may include content modifications, generated document references, or
   * clone references.
   *
   * @param packId the unique pack identifier
   * @param tenantId the tenant schema name
   * @return check result with {@code canUninstall} flag and optional blocking reason
   */
  UninstallCheck checkUninstallable(String packId, String tenantId);

  /**
   * Uninstalls a content pack by deleting all content rows created by the install and the
   * PackInstall tracking row. MUST call {@link #checkUninstallable} first and throw {@code
   * ResourceConflictException} if blocked.
   *
   * @param packId the unique pack identifier
   * @param tenantId the tenant schema name
   * @param memberId the ID of the member performing the uninstall
   */
  void uninstall(String packId, String tenantId, String memberId);
}
