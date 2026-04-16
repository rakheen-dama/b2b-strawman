package io.b2mash.b2b.b2bstrawman.packs;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the pack catalog across all registered {@link PackInstaller} implementations and
 * enriches entries with install-state from the database.
 */
@Service
public class PackCatalogService {

  private static final Logger log = LoggerFactory.getLogger(PackCatalogService.class);

  private final List<PackInstaller> installers;
  private final Map<PackType, PackInstaller> installersByType;
  private final PackInstallRepository packInstallRepository;
  private final OrgSettingsRepository orgSettingsRepository;

  public PackCatalogService(
      List<PackInstaller> installers,
      PackInstallRepository packInstallRepository,
      OrgSettingsRepository orgSettingsRepository) {
    this.installers = installers;
    this.packInstallRepository = packInstallRepository;
    this.orgSettingsRepository = orgSettingsRepository;

    // Build O(1) lookup map; fail fast at boot time if two installers register for same type
    this.installersByType =
        installers.stream()
            .collect(
                Collectors.toMap(
                    PackInstaller::type,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException(
                          "Duplicate PackInstaller for type "
                              + a.type()
                              + ": "
                              + a.getClass().getSimpleName()
                              + " and "
                              + b.getClass().getSimpleName());
                    }));
  }

  /**
   * Returns the full pack catalog, optionally filtered by the tenant's vertical profile.
   *
   * @param showAll if true, returns all packs regardless of profile; if false, filters to
   *     profile-matched and universal (verticalProfile == null) packs
   * @return list of catalog entries enriched with install state
   */
  @Transactional(readOnly = true)
  public List<PackCatalogEntry> listCatalog(boolean showAll) {
    String tenantProfile = resolveTenantProfile();

    List<PackCatalogEntry> allEntries = new ArrayList<>();
    for (PackInstaller installer : installers) {
      allEntries.addAll(installer.availablePacks());
    }

    // Profile filtering
    List<PackCatalogEntry> filtered;
    if (showAll) {
      filtered = allEntries;
    } else {
      filtered =
          allEntries.stream()
              .filter(
                  entry ->
                      entry.verticalProfile() == null
                          || entry.verticalProfile().equals(tenantProfile))
              .toList();
    }

    // Install-state enrichment
    return enrichWithInstallState(filtered);
  }

  /**
   * Returns all installed packs for the current tenant, enriched as catalog entries.
   *
   * @return list of installed pack catalog entries
   */
  @Transactional(readOnly = true)
  public List<PackCatalogEntry> listInstalled() {
    List<PackInstall> installs = packInstallRepository.findAll();
    List<PackCatalogEntry> result = new ArrayList<>();

    for (PackInstall install : installs) {
      // Try to find the catalog entry from the installers for full metadata
      PackCatalogEntry catalogEntry = findCatalogEntryInternal(install.getPackId());
      if (catalogEntry != null) {
        result.add(
            new PackCatalogEntry(
                catalogEntry.packId(),
                catalogEntry.name(),
                catalogEntry.description(),
                catalogEntry.version(),
                catalogEntry.type(),
                catalogEntry.verticalProfile(),
                catalogEntry.itemCount(),
                true,
                install.getInstalledAt().toString()));
      } else {
        // Fallback: build from PackInstall data (pack may have been removed from classpath)
        result.add(
            new PackCatalogEntry(
                install.getPackId(),
                install.getPackName(),
                null,
                install.getPackVersion(),
                install.getPackType(),
                null,
                install.getItemCount(),
                true,
                install.getInstalledAt().toString()));
      }
    }

    return result;
  }

  /**
   * Returns pack IDs matching a given vertical profile and pack type.
   *
   * @param verticalProfile the vertical profile to filter by
   * @param type the pack type to filter by
   * @return list of matching pack IDs
   */
  public List<String> getPackIdsForProfile(String verticalProfile, PackType type) {
    PackInstaller installer = installersByType.get(type);
    if (installer == null) {
      return List.of();
    }
    return installer.availablePacks().stream()
        .filter(
            entry ->
                entry.verticalProfile() != null && entry.verticalProfile().equals(verticalProfile))
        .map(PackCatalogEntry::packId)
        .toList();
  }

  /**
   * Finds a single catalog entry by packId across all installers.
   *
   * @param packId the unique pack identifier
   * @return the matching catalog entry, or null if not found
   */
  public PackCatalogEntry findCatalogEntry(String packId) {
    return findCatalogEntryInternal(packId);
  }

  /** Returns the installer registry map for use by PackInstallService. */
  Map<PackType, PackInstaller> getInstallersByType() {
    return installersByType;
  }

  private PackCatalogEntry findCatalogEntryInternal(String packId) {
    for (PackInstaller installer : installers) {
      for (PackCatalogEntry entry : installer.availablePacks()) {
        if (entry.packId().equals(packId)) {
          return entry;
        }
      }
    }
    return null;
  }

  private List<PackCatalogEntry> enrichWithInstallState(List<PackCatalogEntry> entries) {
    return entries.stream()
        .map(
            entry -> {
              var install = packInstallRepository.findByPackId(entry.packId());
              if (install.isPresent()) {
                return new PackCatalogEntry(
                    entry.packId(),
                    entry.name(),
                    entry.description(),
                    entry.version(),
                    entry.type(),
                    entry.verticalProfile(),
                    entry.itemCount(),
                    true,
                    install.get().getInstalledAt().toString());
              }
              return entry;
            })
        .toList();
  }

  private String resolveTenantProfile() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(settings -> settings.getVerticalProfile())
        .orElse(null);
  }
}
