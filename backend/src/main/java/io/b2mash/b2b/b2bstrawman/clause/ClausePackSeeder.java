package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Seeds clause packs for newly provisioned tenants. */
@Service
public class ClausePackSeeder extends AbstractPackSeeder<ClausePackDefinition> {

  private static final Logger log = LoggerFactory.getLogger(ClausePackSeeder.class);
  private static final String PACK_LOCATION = "classpath:clause-packs/*/pack.json";

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
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
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
  protected String getPackId(ClausePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(ClausePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getClausePackStatus() == null) {
      return false;
    }
    return settings.getClausePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, ClausePackDefinition pack) {
    settings.recordClausePackApplication(pack.packId(), pack.version());
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
      var templateOpt =
          documentTemplateRepository.findByPackIdAndPackTemplateKey(
              assoc.templatePackId(), assoc.templateKey());

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
}
