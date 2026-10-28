package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.PackStatusSettings;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for pack seeders that load JSON pack definitions from the classpath and seed
 * entity-specific data for tenants. Handles the common flow of classpath scanning, JSON
 * deserialization, OrgSettings tracking (idempotency), and tenant transaction scoping.
 *
 * <p>Subclasses implement the template methods to specify:
 *
 * <ul>
 *   <li>Where to find pack files on the classpath
 *   <li>How to deserialize JSON into a pack definition type
 *   <li>How to track applied packs in OrgSettings (read and write)
 *   <li>How to create domain entities from a pack definition
 * </ul>
 *
 * <p><b>Reconcile (ADR-356).</b> A pack already applied to a tenant is never re-applied; changes to
 * it reach existing tenants only through its {@code reconcile} block ({@link PackReconcileEntry},
 * the one rule). For seeders with a seeder-managed {@link #getPackType()} (CLAUSE, FIELD, REPORT,
 * REQUEST) the hook {@link #reconcileExistingPack} runs only when the {@code pack_install} version
 * gate opens ({@link PackVersionGate#backfillAndTryOpen}: recorded version lower than the classpath
 * version); this base class writes those ledger rows on apply and backfills them for pre-625E
 * tenants from the OrgSettings pack-status lists. Installer-managed types (DOCUMENT_TEMPLATE,
 * AUTOMATION_TEMPLATE) are gated by their {@code PackInstaller.reconcile}, which calls {@link
 * #reconcileInstalledPack}. Untyped seeders ({@code getPackType() == null}) keep the legacy ungated
 * hook call on every pass.
 *
 * @param <D> the pack definition type (e.g., TemplatePackDefinition, ClausePackDefinition)
 */
public abstract class AbstractPackSeeder<D> {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final PackVersionGate packVersionGate;

  protected AbstractPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.packVersionGate = packVersionGate;
  }

  /** Classpath resource pattern for pack JSON files (e.g., "classpath:field-packs/*.json"). */
  protected abstract String getPackResourcePattern();

  /** The Java class to deserialize each pack JSON file into. */
  protected abstract Class<D> getPackDefinitionType();

  /** Human-readable name for this pack type, used in log messages (e.g., "field", "template"). */
  protected abstract String getPackTypeName();

  /** Extract the pack identifier from a deserialized pack definition. */
  protected abstract String getPackId(D pack);

  /** Extract the pack version from a deserialized pack definition (as a string for display). */
  protected abstract String getPackVersion(D pack);

  /** Check whether a pack has already been applied, using the OrgSettings status list. */
  protected abstract boolean isPackAlreadyApplied(OrgSettings settings, String packId);

  /** Record a successful pack application in OrgSettings. */
  protected abstract void recordPackApplication(OrgSettings settings, D pack);

  /**
   * Apply the pack definition to create domain entities. Called once per pack that hasn't been
   * applied yet.
   *
   * @param pack the deserialized pack definition
   * @param packResource the classpath Resource the pack was loaded from (useful for loading
   *     relative files)
   * @param tenantId the tenant schema name
   */
  protected abstract void applyPack(D pack, Resource packResource, String tenantId);

  /**
   * ADR-356 {@code pack_install} type of this seeder's packs, or {@code null} for seeders outside
   * the reconcile framework (compliance, compliance-template, project-template): their hook keeps
   * running on every pass, ungated. Installer-managed types are gated by their {@code
   * PackInstaller.reconcile}, which calls {@link #reconcileInstalledPack}.
   */
  protected PackType getPackType() {
    return null;
  }

  /** {@code pack_install.pack_name} for ledger rows this seeder writes. Default: the pack id. */
  protected String getPackName(D pack) {
    return getPackId(pack);
  }

  /** The pack's validated {@code reconcile} block (empty when absent). */
  protected List<PackReconcileEntry> getReconcileEntries(D pack) {
    return pack instanceof ReconcilablePack r
        ? PackReconcileEntry.parse(getPackId(pack), r.reconcile())
        : List.of();
  }

  /**
   * Extension point (625F / 626D / 628C): the pack-seeded row an entry targets. MUST NOT return
   * clones or tenant-authored rows. Empty → the entry is skipped and logged. Default: no entity is
   * supported.
   *
   * <p>The returned target writes the new value only. For a {@code sha256:} {@code to} (Tiptap
   * body), {@link PackReconcileEntry.Target#apply} receives the hash <em>string</em>: it must
   * ignore it, load the new body from {@code packResource} and write it; {@code currentValue()}
   * returns the body JSON. Targets must NOT compute or write content hashes: for installer-managed
   * types (templates, automations) the {@code PackInstaller} re-pins the stored content hash of
   * rows that were pristine before the hook; seeder-managed types have no install-time content
   * hash.
   *
   * @param pack the current pack definition from the classpath
   * @param entry the reconcile entry
   * @param packResource the classpath Resource the pack was loaded from (for relative body files)
   */
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      D pack, PackReconcileEntry entry, Resource packResource) {
    return Optional.empty();
  }

  /**
   * Reconcile an already-applied pack (ADR-356). Seeder-managed types: called only when the version
   * gate opens (recorded {@code pack_install.pack_version} &lt; {@code pack.version}), in the same
   * transaction that advanced the version. Untyped seeders: every pass. The default applies the
   * pack's {@code reconcile} block entry by entry under the one rule.
   *
   * @param pack the current pack definition from the classpath
   * @param packResource the classpath Resource the pack was loaded from
   * @param tenantId the tenant schema name
   */
  protected void reconcileExistingPack(D pack, Resource packResource, String tenantId) {
    for (PackReconcileEntry entry : getReconcileEntries(pack)) {
      applyReconcileEntry(pack, packResource, entry, tenantId);
    }
  }

  /**
   * Per-entry reconcile step. Default: resolve the target and apply the one rule with logging.
   * Override for entities that are not a one-rule field update (626G: additive {@code
   * clause_template_association} upserts).
   */
  protected void applyReconcileEntry(
      D pack, Resource packResource, PackReconcileEntry entry, String tenantId) {
    entry.applyAndLog(
        resolveReconcileTarget(pack, entry, packResource), log, tenantId, getPackId(pack));
  }

  /**
   * Public delegate for the {@code PackInstaller}s of installer-managed types. The caller has
   * already opened the version gate and owns the tenant transaction.
   */
  public void reconcileInstalledPack(D pack, Resource packResource, String tenantId) {
    reconcileExistingPack(pack, packResource, tenantId);
  }

  /**
   * Extract the vertical profile from a deserialized pack definition. Returns null for universal
   * packs that apply to all tenants.
   */
  protected abstract String getVerticalProfile(D pack);

  /**
   * Seeds all available packs for the given tenant. Entry point called by provisioning and
   * reconciliation code. Delegates to {@link TenantTransactionHelper} to run within the correct
   * tenant transaction scope.
   */
  public void seedPacksForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeedPacks(t));
  }

  private void doSeedPacks(String tenantId) {
    List<LoadedPack<D>> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No {} packs found on classpath for tenant {}", getPackTypeName(), tenantId);
      return;
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    for (LoadedPack<D> loaded : packs) {
      D pack = loaded.definition();
      String packId = getPackId(pack);

      // Vertical profile filtering: skip packs targeting a different vertical
      String tenantProfile = settings.getVerticalProfile();
      String packProfile = getVerticalProfile(pack);
      if (packProfile != null && !packProfile.equals(tenantProfile)) {
        log.debug(
            "Skipping {} pack {} (profile {} != tenant profile {})",
            getPackTypeName(),
            packId,
            packProfile,
            tenantProfile);
        continue;
      }

      // Installed packs are never re-applied: changes reach existing tenants only through the
      // ADR-356 version-gated reconcile block (a column added to a pack-seeded entity still needs
      // a V-migration UPDATE, e.g. tenant V2 — see db/BASELINE.md §5).
      if (isPackAlreadyApplied(settings, packId)) {
        reconcileAppliedPack(loaded, settings, tenantId);
        continue;
      }

      applyPack(pack, loaded.resource(), tenantId);
      recordPackApplication(settings, pack);
      PackType type = getPackType();
      if (type != null && !type.isInstallerManaged()) {
        packVersionGate.recordIfAbsent(type, packId, getPackVersion(pack), getPackName(pack));
      }
      log.info(
          "Applied {} pack {} v{} for tenant {}",
          getPackTypeName(),
          packId,
          getPackVersion(pack),
          tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private void reconcileAppliedPack(LoadedPack<D> loaded, OrgSettings settings, String tenantId) {
    D pack = loaded.definition();
    String packId = getPackId(pack);
    PackType type = getPackType();
    if (type == null) {
      // Outside the framework: unchanged, ungated (CompliancePackSeeder's autoInstantiate sync).
      log.info(
          "{} pack {} already applied for tenant {}, reconciling settings",
          capitalize(getPackTypeName()),
          packId,
          tenantId);
      reconcileExistingPack(pack, loaded.resource(), tenantId);
      return;
    }
    if (type.isInstallerManaged()) {
      log.debug(
          "{} pack {} already applied for tenant {}; reconcile is gated by its PackInstaller",
          capitalize(getPackTypeName()),
          packId,
          tenantId);
      return;
    }
    // OQ #14 backfill (tenants seeded before 625E) + version gate: one entry point.
    String appliedVersion = appliedVersion(settings, type, packId).orElse(null);
    if (packVersionGate.backfillAndTryOpen(
        type, packId, appliedVersion, getPackVersion(pack), getPackName(pack), tenantId)) {
      reconcileExistingPack(pack, loaded.resource(), tenantId);
      // Re-record the reconciled version in the OrgSettings pack-status list (the ADR-356 backfill
      // source), as PackInstallService does for installer-managed types after a reconcile.
      recordPackApplication(settings, pack);
    }
  }

  /** The OrgSettings pack-status list that backs {@code type} — the ADR-356 backfill source. */
  private static Optional<String> appliedVersion(
      OrgSettings settings, PackType type, String packId) {
    var status = settings.getPackStatus();
    List<Map<String, Object>> entries =
        switch (type) {
          case DOCUMENT_TEMPLATE -> status.getTemplatePackStatus();
          case AUTOMATION_TEMPLATE -> status.getAutomationPackStatus();
          case CLAUSE -> status.getClausePackStatus();
          case FIELD -> status.getFieldPackStatus();
          case REPORT -> status.getReportPackStatus();
          case REQUEST -> status.getRequestPackStatus();
        };
    return PackStatusSettings.highestAppliedVersion(entries, packId);
  }

  protected List<LoadedPack<D>> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(getPackResourcePattern());
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  var definition =
                      objectMapper.readValue(resource.getInputStream(), getPackDefinitionType());
                  return new LoadedPack<>(definition, resource);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse " + getPackTypeName() + " pack: " + resource.getFilename(),
                      e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for {} packs at {}", getPackTypeName(), getPackResourcePattern(), e);
      return List.of();
    }
  }

  /** Provides access to the ObjectMapper for subclasses that need additional JSON parsing. */
  protected ObjectMapper objectMapper() {
    return objectMapper;
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * A pack definition paired with the classpath Resource it was loaded from. Subclasses that need
   * to load relative files (e.g., CSS or content JSON alongside pack.json) can use the resource.
   */
  public record LoadedPack<D>(D definition, Resource resource) {}
}
