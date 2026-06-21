package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.crm.PipelineStage;
import io.b2mash.b2b.b2bstrawman.crm.PipelineStageRepository;
import io.b2mash.b2b.b2bstrawman.crm.StageType;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds the per-tenant sales pipeline (a set of {@link PipelineStage} rows) from deal-pipeline pack
 * JSON definitions (Phase 80, §11.6f). Each tenant gets EXACTLY ONE pipeline:
 *
 * <ul>
 *   <li>A tenant whose vertical profile matches a profile-specific pack gets that pack.
 *   <li>Any other tenant (profile-less, or a profile with no specific pack) gets the universal
 *       {@code default} pack ({@code verticalProfile: null}).
 * </ul>
 *
 * <p>The base {@link AbstractPackSeeder} would apply a universal ({@code verticalProfile == null})
 * pack to EVERY tenant — so to keep one-pipeline-per-tenant we treat the universal default pack as
 * "already applied" (via {@link #isPackAlreadyApplied}) for any tenant that has a profile-specific
 * deal-pipeline pack. The base class then skips it entirely — neither {@link #applyPack} nor {@link
 * #recordPackApplication} runs for the default pack — so the idempotency ledger stays accurate (the
 * default packId is never recorded for a profiled tenant). Idempotent across re-runs: the base
 * class records the applied packId in OrgSettings and skips on re-run.
 */
@Service
public class DealPipelinePackSeeder extends AbstractPackSeeder<DealPipelinePackDefinition> {

  private static final String PACK_LOCATION = "classpath:deal-pipeline-packs/*.json";

  private final PipelineStageRepository pipelineStageRepository;

  public DealPipelinePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PipelineStageRepository pipelineStageRepository) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.pipelineStageRepository = pipelineStageRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<DealPipelinePackDefinition> getPackDefinitionType() {
    return DealPipelinePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "deal-pipeline";
  }

  @Override
  protected String getPackId(DealPipelinePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(DealPipelinePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(DealPipelinePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    // One-pipeline-per-tenant: treat the universal default pack as already-applied for any tenant
    // that has a profile-specific deal-pipeline pack. The base class then skips it entirely (no
    // applyPack, no recordPackApplication) so the default packId is never falsely recorded.
    if (isDefaultPack(packId) && profileSpecificPackExists(settings.getVerticalProfile())) {
      return true;
    }

    if (settings.getPackStatus().getDealPipelinePackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getDealPipelinePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, DealPipelinePackDefinition pack) {
    settings.getPackStatus().recordDealPipelinePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(
      DealPipelinePackDefinition pack, Resource packResource, String tenantId) {
    for (DealPipelinePackDefinition.StageEntry entry : pack.stages()) {
      var stage =
          new PipelineStage(
              entry.name(),
              entry.position(),
              entry.defaultProbabilityPct(),
              StageType.valueOf(entry.stageType()),
              null);
      pipelineStageRepository.save(stage);
    }
    log.debug(
        "Seeded {} pipeline stages from pack {} for tenant {}",
        pack.stages().size(),
        pack.packId(),
        tenantId);
  }

  /** A pack is the universal default when it declares no vertical profile. */
  private boolean isDefaultPack(String packId) {
    return loadPacks().stream()
        .map(LoadedPack::definition)
        .filter(def -> packId.equals(def.packId()))
        .anyMatch(def -> def.verticalProfile() == null);
  }

  /**
   * True if any deal-pipeline pack on the classpath targets the given tenant vertical profile.
   * Derived from the passed-in settings profile — no extra repository round-trip.
   */
  private boolean profileSpecificPackExists(String tenantProfile) {
    if (tenantProfile == null) {
      return false;
    }
    return loadPacks().stream()
        .map(loaded -> loaded.definition().verticalProfile())
        .anyMatch(tenantProfile::equals);
  }
}
