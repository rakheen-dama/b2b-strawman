package io.b2mash.b2b.b2bstrawman.clause;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Seeds clause packs for newly provisioned tenants. */
@Service
public class ClausePackSeeder {

  private static final Logger log = LoggerFactory.getLogger(ClausePackSeeder.class);
  private static final String PACK_LOCATION = "classpath:clause-packs/*/pack.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final ClauseRepository clauseRepository;
  private final TemplateClauseRepository templateClauseRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public ClausePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      ClauseRepository clauseRepository,
      TemplateClauseRepository templateClauseRepository,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.clauseRepository = clauseRepository;
    this.templateClauseRepository = templateClauseRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /** Seeds all discovered clause packs for the given tenant. */
  public void seedPacksForTenant(String tenantId, String orgId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .run(() -> transactionTemplate.executeWithoutResult(tx -> doSeedPacks(tenantId)));
  }

  private void doSeedPacks(String tenantId) {
    List<PackWithResource> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No clause packs found on classpath for tenant {}", tenantId);
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

    for (PackWithResource packEntry : packs) {
      ClausePackDefinition pack = packEntry.definition();
      if (isPackAlreadyApplied(settings, pack.packId())) {
        log.info("Clause pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack, tenantId);
      settings.recordClausePackApplication(pack.packId(), pack.version());
      log.info("Applied clause pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<PackWithResource> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  var definition =
                      objectMapper.readValue(resource.getInputStream(), ClausePackDefinition.class);
                  return new PackWithResource(definition, resource);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse clause pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for clause packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getClausePackStatus() == null) {
      return false;
    }
    return settings.getClausePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  private void applyPack(ClausePackDefinition pack, String tenantId) {
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

      var clause =
          Clause.createSystemClause(
              clauseDef.title(),
              clauseDef.slug(),
              clauseDef.body(),
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

  private record PackWithResource(ClausePackDefinition definition, Resource packResource) {}
}
