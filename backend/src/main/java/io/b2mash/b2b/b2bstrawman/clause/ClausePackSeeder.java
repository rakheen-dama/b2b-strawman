package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Seeds clause packs for newly provisioned tenants. */
@Service
public class ClausePackSeeder extends AbstractPackSeeder<ClausePackDefinition> {

  private static final String PACK_LOCATION = "classpath:clause-packs/*/pack.json";

  /** ADR-356 entity of the additive template-association upsert (Phase 88 626G). */
  static final String ASSOCIATION_ENTITY = "clause_template_association";

  /** {@code field} of an association entry: names the relation; {@code from} is unused. */
  static final String ASSOCIATION_FIELD = "association";

  private final ClauseRepository clauseRepository;
  private final TemplateClauseRepository templateClauseRepository;
  private final DocumentTemplateRepository documentTemplateRepository;

  public ClausePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      ClauseRepository clauseRepository,
      TemplateClauseRepository templateClauseRepository,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    super(
        resourceResolver,
        objectMapper,
        orgSettingsRepository,
        tenantTransactionHelper,
        packVersionGate);
    this.clauseRepository = clauseRepository;
    this.templateClauseRepository = templateClauseRepository;
    this.documentTemplateRepository = documentTemplateRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<ClausePackDefinition> getPackDefinitionType() {
    return ClausePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "clause";
  }

  @Override
  protected PackType getPackType() {
    return PackType.CLAUSE;
  }

  @Override
  protected String getPackId(ClausePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(ClausePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(ClausePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getPackStatus().getClausePackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getClausePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, ClausePackDefinition pack) {
    settings.getPackStatus().recordClausePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(ClausePackDefinition pack, Resource packResource, String tenantId) {
    // Seed clauses
    for (ClausePackDefinition.ClauseDefinition clauseDef : pack.clauses()) {
      var existing = clauseRepository.findBySlug(clauseDef.slug());
      if (existing.isPresent()) {
        log.warn(
            "Clause with slug '{}' already exists for tenant {} (source={}), skipping",
            clauseDef.slug(),
            tenantId,
            existing.get().getSource());
        continue;
      }

      // Body is now stored as JSONB Map directly
      @SuppressWarnings("unchecked")
      Map<String, Object> bodyMap =
          clauseDef.body() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
      var clause =
          Clause.createSystemClause(
              clauseDef.title(),
              clauseDef.slug(),
              bodyMap,
              clauseDef.category(),
              clauseDef.description(),
              pack.packId(),
              clauseDef.sortOrder());
      clauseRepository.save(clause);
    }

    // Create template-clause associations
    if (pack.templateAssociations() != null) {
      createTemplateAssociations(pack);
    }
  }

  private void createTemplateAssociations(ClausePackDefinition pack) {
    // Build a map of slug -> Clause for quick lookup
    var seededClauses =
        clauseRepository.findByPackIdAndSourceAndActiveTrue(pack.packId(), ClauseSource.SYSTEM);
    Map<String, Clause> clausesBySlug =
        seededClauses.stream().collect(Collectors.toMap(Clause::getSlug, Function.identity()));

    for (ClausePackDefinition.TemplateAssociation assoc : pack.templateAssociations()) {
      // Clone-safe: associations go on the pack-seeded template, never a firm clone.
      var templateOpt =
          documentTemplateRepository
              .findPackSeeded(assoc.templatePackId(), assoc.templateKey())
              .stream()
              .findFirst();

      if (templateOpt.isEmpty()) {
        log.warn(
            "Template not found for packId='{}' templateKey='{}', skipping associations",
            assoc.templatePackId(),
            assoc.templateKey());
        continue;
      }

      var template = templateOpt.get();
      int sortOrder = 0;

      for (String clauseSlug : assoc.clauseSlugs()) {
        var clause = clausesBySlug.get(clauseSlug);
        if (clause == null) {
          log.warn(
              "Clause with slug '{}' not found in pack '{}', skipping association",
              clauseSlug,
              pack.packId());
          continue;
        }

        if (templateClauseRepository.existsByTemplateIdAndClauseId(
            template.getId(), clause.getId())) {
          continue;
        }

        boolean required =
            assoc.requiredSlugs() != null && assoc.requiredSlugs().contains(clauseSlug);
        var templateClause =
            new TemplateClause(template.getId(), clause.getId(), sortOrder, required);
        templateClauseRepository.save(templateClause);
        sortOrder++;
      }
    }
  }

  /**
   * ADR-356 reconcile of {@value #ASSOCIATION_ENTITY} entries (Phase 88 626G, G-B18): {@code key}
   * is a clause slug of this pack and {@code to} the {@code templateKey} of one of this pack's
   * {@code templateAssociations}. That declaration supplies the template pack id, the {@code
   * required} flag and the sort order (the slug's index in its {@code clauseSlugs}).
   *
   * <p>Additive and skip-existing: a missing (template, clause) pair is inserted; an existing one
   * is never updated or deleted, so a firm's required / sort edits survive. The template is the
   * clone-safe pack-seeded row ({@code findPackSeeded}), so clones are never touched; the clause is
   * this pack's active SYSTEM row.
   *
   * <p>Failure modes follow the framework: an entry whose pair no {@code templateAssociations}
   * declaration covers (or more than one does) is a malformed pack and throws {@link
   * IllegalStateException}, like {@link PackReconcileEntry#parse}; an unsupported entity / field /
   * value type is logged at WARN and skipped (resolver convention); a missing template or clause
   * row is logged at INFO as "Reconcile skipped (no pack-seeded row)", and an existing row whose
   * {@code required} / sort order differ from the declaration as "Reconcile skipped (edited)", in
   * {@link PackReconcileEntry#applyAndLog}'s wording. Every association log line names the pair as
   * {@code '<slug>' -> '<templateKey>'}.
   */
  @Override
  protected void applyReconcileEntry(
      ClausePackDefinition pack, Resource packResource, PackReconcileEntry entry, String tenantId) {
    if (!ASSOCIATION_ENTITY.equals(entry.entity())
        || !ASSOCIATION_FIELD.equals(entry.field())
        || !(entry.to() instanceof String templateKey)) {
      log.warn(
          "Reconcile entry ignored: clause pack {} unsupported entity/field/value '{}'.{} (key {})"
              + " — only {} {} entries with a string 'to' are supported",
          pack.packId(),
          entry.entity(),
          entry.field(),
          entry.key(),
          ASSOCIATION_ENTITY,
          ASSOCIATION_FIELD);
      return;
    }
    String slug = entry.key();
    var assoc = declaredAssociation(pack, slug, templateKey);

    var templates = documentTemplateRepository.findPackSeeded(assoc.templatePackId(), templateKey);
    if (templates.size() > 1) {
      log.warn(
          "Reconcile entry ignored: tenant={} pack={} {} '{}' — template {}/{} has {} pack-seeded"
              + " rows",
          tenantId,
          pack.packId(),
          ASSOCIATION_ENTITY,
          slug,
          assoc.templatePackId(),
          templateKey,
          templates.size());
      return;
    }
    var clause =
        clauseRepository
            .findBySlug(slug)
            .filter(c -> pack.packId().equals(c.getPackId()))
            .filter(c -> c.getSource() == ClauseSource.SYSTEM && c.isActive());
    if (templates.isEmpty() || clause.isEmpty()) {
      log.info(
          "Reconcile skipped (no pack-seeded row): tenant={} pack={} {} '{}' -> '{}' field {}",
          tenantId,
          pack.packId(),
          ASSOCIATION_ENTITY,
          slug,
          templateKey,
          ASSOCIATION_FIELD);
      return;
    }

    var templateId = templates.getFirst().getId();
    boolean required = assoc.requiredSlugs() != null && assoc.requiredSlugs().contains(slug);
    int sortOrder = assoc.clauseSlugs().indexOf(slug);
    var existing =
        templateClauseRepository.findByTemplateIdAndClauseId(templateId, clause.get().getId());
    if (existing.isPresent()) {
      // Skip-existing: the row is never written, whether it matches the declaration or not.
      if (existing.get().isRequired() == required && existing.get().getSortOrder() == sortOrder) {
        log.debug(
            "Reconcile no-op: tenant={} pack={} {} '{}' -> '{}' field {} already current",
            tenantId,
            pack.packId(),
            ASSOCIATION_ENTITY,
            slug,
            templateKey,
            ASSOCIATION_FIELD);
      } else {
        log.info(
            "Reconcile skipped (edited): tenant={} pack={} {} '{}' -> '{}' field {} differs from"
                + " the shipped value — left alone",
            tenantId,
            pack.packId(),
            ASSOCIATION_ENTITY,
            slug,
            templateKey,
            ASSOCIATION_FIELD);
      }
      return;
    }
    templateClauseRepository.save(
        new TemplateClause(templateId, clause.get().getId(), sortOrder, required));
    log.info(
        "Reconcile applied: tenant={} pack={} {} '{}' -> '{}' field {} updated",
        tenantId,
        pack.packId(),
        ASSOCIATION_ENTITY,
        slug,
        templateKey,
        ASSOCIATION_FIELD);
  }

  /** The single {@code templateAssociations} declaration covering ({@code slug}, template). */
  private static ClausePackDefinition.TemplateAssociation declaredAssociation(
      ClausePackDefinition pack, String slug, String templateKey) {
    List<ClausePackDefinition.TemplateAssociation> declared =
        pack.templateAssociations() == null
            ? List.of()
            : pack.templateAssociations().stream()
                .filter(a -> templateKey.equals(a.templateKey()))
                .filter(a -> a.clauseSlugs() != null && a.clauseSlugs().contains(slug))
                .toList();
    if (declared.size() != 1) {
      throw new IllegalStateException(
          "Pack "
              + pack.packId()
              + " reconcile "
              + ASSOCIATION_ENTITY
              + " '"
              + slug
              + "' -> "
              + templateKey
              + ": expected exactly one templateAssociations declaration, found "
              + declared.size());
    }
    return declared.getFirst();
  }
}
