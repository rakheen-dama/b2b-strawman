package io.b2mash.b2b.b2bstrawman.verticals;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry.ProfileDefinition;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry.TaxDefault;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconciles a tenant's {@link OrgSettings} with the vertical-profile JSON on every application
 * start. Two concerns live here:
 *
 * <ul>
 *   <li><b>GAP-L-44</b> — merge {@code enabledModules} from the profile JSON into the tenant's
 *       org_settings so that modules added to the profile after provisioning become active
 *       automatically. Idempotent: only modules missing from the tenant row are appended.
 *   <li><b>GAP-L-27</b> — apply the profile's {@code taxDefaults} to the tenant's tax catalog. For
 *       legal-za the default tier seeded at V43 ({@code "Standard"}) is renamed to {@code "VAT —
 *       Standard"} and {@code org_settings.tax_label} is set to {@code "VAT"}. Idempotent: skips
 *       both side-effects once the target state is reached.
 * </ul>
 *
 * <p>Invoked from {@link io.b2mash.b2b.b2bstrawman.provisioning.PackReconciliationRunner} inside
 * the existing tenant loop. Uses {@link TenantTransactionHelper} so the correct {@code search_path}
 * is bound.
 */
@Service
public class VerticalProfileReconciliationSeeder {

  private static final Logger log =
      LoggerFactory.getLogger(VerticalProfileReconciliationSeeder.class);

  /** The base tier name inserted by tenant migration V43. Matched case-sensitively. */
  private static final String LEGACY_DEFAULT_TIER_NAME = "Standard";

  private final TenantTransactionHelper tenantTransactionHelper;
  private final VerticalProfileRegistry profileRegistry;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TaxRateRepository taxRateRepository;

  public VerticalProfileReconciliationSeeder(
      TenantTransactionHelper tenantTransactionHelper,
      VerticalProfileRegistry profileRegistry,
      OrgSettingsRepository orgSettingsRepository,
      TaxRateRepository taxRateRepository) {
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.profileRegistry = profileRegistry;
    this.orgSettingsRepository = orgSettingsRepository;
    this.taxRateRepository = taxRateRepository;
  }

  /**
   * Runs both reconciliations for the given tenant. A tenant without a vertical profile (or one
   * whose profile is not present in the registry) is a silent no-op.
   */
  public void reconcile(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doReconcile(t));
  }

  private void doReconcile(String tenantId) {
    var settingsOpt = orgSettingsRepository.findForCurrentTenant();
    if (settingsOpt.isEmpty()) {
      log.debug("Skipping profile reconciliation for tenant {} (no org_settings row)", tenantId);
      return;
    }
    OrgSettings settings = settingsOpt.get();
    String profileId = settings.getVerticalProfile();
    if (profileId == null) {
      return;
    }
    Optional<ProfileDefinition> profileOpt = profileRegistry.getProfile(profileId);
    if (profileOpt.isEmpty()) {
      log.debug(
          "Skipping profile reconciliation for tenant {} (profile '{}' not in registry)",
          tenantId,
          profileId);
      return;
    }
    ProfileDefinition profile = profileOpt.get();

    mergeEnabledModules(settings, profile, tenantId);
    applyTaxDefaults(settings, profile, tenantId);
  }

  // ── GAP-L-44: enabled-modules merge ───────────────────────────────────

  private void mergeEnabledModules(
      OrgSettings settings, ProfileDefinition profile, String tenantId) {
    List<String> profileModules = profile.enabledModules();
    if (profileModules == null || profileModules.isEmpty()) {
      return;
    }
    List<String> current = new ArrayList<>(settings.getEnabledModules());
    // LinkedHashSet preserves insertion order for stable audit diffs while providing contains().
    LinkedHashSet<String> merged = new LinkedHashSet<>(current);
    List<String> added = new ArrayList<>();
    for (String moduleId : profileModules) {
      if (merged.add(moduleId)) {
        added.add(moduleId);
      }
    }
    if (added.isEmpty()) {
      return;
    }
    settings.setEnabledModules(new ArrayList<>(merged));
    orgSettingsRepository.save(settings);
    log.info(
        "Reconciled enabled_modules for tenant {} (profile={}): added {}",
        tenantId,
        profile.profileId(),
        added);
  }

  // ── GAP-L-27: tax-default reconciliation ──────────────────────────────

  private void applyTaxDefaults(OrgSettings settings, ProfileDefinition profile, String tenantId) {
    List<TaxDefault> taxDefaults = profile.taxDefaults();
    if (taxDefaults == null || taxDefaults.isEmpty()) {
      return;
    }
    TaxDefault primary = resolvePrimary(taxDefaults);
    if (primary == null) {
      return;
    }

    // 1. tenant.org_settings.tax_label ← taxDefaults[default].name (e.g. "VAT")
    if (!primary.name().equals(settings.getTaxLabel())) {
      settings.setTaxLabel(primary.name());
      orgSettingsRepository.save(settings);
      log.info(
          "Reconciled tax_label for tenant {} (profile={}): {}",
          tenantId,
          profile.profileId(),
          primary.name());
    }

    // 2. tenant.tax_rates default row ← "<name> — <tier>" (only when it is still the legacy
    //    "Standard" value seeded by V43 — don't clobber owner-edited names).
    String targetName = primary.name() + " — " + LEGACY_DEFAULT_TIER_NAME; // "VAT — Standard"
    taxRateRepository
        .findByIsDefaultTrue()
        .filter(rate -> LEGACY_DEFAULT_TIER_NAME.equals(rate.getName()))
        .ifPresent(
            rate -> {
              BigDecimal profileRate = primary.rate();
              int sortOrder = rate.getSortOrder();
              rate.update(
                  targetName,
                  profileRate != null ? profileRate : rate.getRate(),
                  true,
                  rate.isExempt(),
                  sortOrder,
                  rate.isActive());
              taxRateRepository.save(rate);
              log.info(
                  "Reconciled default tax_rate name for tenant {} (profile={}): '{}' -> '{}'",
                  tenantId,
                  profile.profileId(),
                  LEGACY_DEFAULT_TIER_NAME,
                  targetName);
            });
  }

  /**
   * Picks the first {@code default=true} entry, falling back to the first entry in declaration
   * order. Returns {@code null} if the list is empty.
   */
  private static TaxDefault resolvePrimary(List<TaxDefault> taxDefaults) {
    TaxDefault first = null;
    for (TaxDefault td : taxDefaults) {
      if (first == null) {
        first = td;
      }
      if (td.isDefault()) {
        return td;
      }
    }
    return first;
  }

  // Visible-for-testing: expose the legacy tier name so the integration test can assert the
  // pre-reconciliation state deterministically.
  static String legacyDefaultTierName() {
    return LEGACY_DEFAULT_TIER_NAME;
  }
}
