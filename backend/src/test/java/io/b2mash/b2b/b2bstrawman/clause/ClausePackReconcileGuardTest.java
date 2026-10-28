package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Classpath guard for clause packs that reach existing tenants through {@code
 * clause_template_association} reconcile entries (ADR-356, Phase 88 626G).
 *
 * <p>A pack's {@code templateAssociations} declaration only reaches FRESH tenants; existing tenants
 * get an association only through a reconcile entry naming the same (template, clause) pair. So for
 * every pack whose {@code reconcile} block contains at least one {@value
 * ClausePackSeeder#ASSOCIATION_ENTITY} entry, the set of reconcile {@code (to, key)} pairs must
 * EQUAL the set of declared {@code (templateKey, slug)} pairs, with no duplicate entries — a slug
 * added to {@code templateAssociations} without a reconcile entry would otherwise silently never
 * reach existing tenants. (A reconcile pair that no declaration covers already fails loud at
 * reconcile time in {@code ClausePackSeeder.applyReconcileEntry}; this guard catches it at build
 * time too.)
 *
 * <p>Out of the rule: packs with {@code templateAssociations} but no association reconcile entries
 * — e.g. {@code legal-za-clauses} v1, whose associations shipped with the pack's first version, so
 * every tenant got them through the fresh-install path.
 *
 * <p>For {@code conveyancing-za-clauses} it also pins each declared association's {@code
 * clauseSlugs} to the {@code clauseBlock} slugs the referenced template embeds, in document order
 * (the order is the reconcile sort order).
 */
class ClausePackReconcileGuardTest {

  private static final String CLAUSE_PACKS = "classpath:clause-packs/*/pack.json";
  private static final String TEMPLATE_PACKS = "classpath:template-packs/*/pack.json";
  private static final String CONVEYANCING_PACK = "conveyancing-za-clauses";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();

  /** One (template, clause) association pair. */
  record Pair(String templateKey, String slug) {}

  @Test
  void everyClausePackWithAssociationReconcile_reconcilesExactlyItsDeclaredAssociations()
      throws IOException {
    List<ClausePackDefinition> inScope =
        loadClausePacks().stream().filter(p -> !reconcilePairs(p).isEmpty()).toList();

    assertThat(inScope)
        .as("clause packs with association reconcile entries")
        .extracting(ClausePackDefinition::packId)
        .contains(CONVEYANCING_PACK);
    for (var pack : inScope) {
      assertThat(violations(pack)).as("pack %s", pack.packId()).isEmpty();
    }
  }

  @Test
  void conveyancingAssociations_matchTheClauseBlocksTheirTemplatesEmbed() throws IOException {
    var pack = conveyancingPack();
    assertThat(pack.templateAssociations()).isNotEmpty();

    for (var assoc : pack.templateAssociations()) {
      assertThat(assoc.clauseSlugs())
          .as(
              "%s/%s clauseSlugs vs embedded clauseBlocks",
              assoc.templatePackId(), assoc.templateKey())
          .containsExactlyElementsOf(
              embeddedClauseSlugs(assoc.templatePackId(), assoc.templateKey()));
      assertThat(assoc.clauseSlugs())
          .as("%s requiredSlugs subset", assoc.templateKey())
          .containsAll(assoc.requiredSlugs() == null ? List.of() : assoc.requiredSlugs());
    }
  }

  @Test
  void guard_flagsADeclaredSlugWithoutAReconcileEntry() throws IOException {
    var pack = conveyancingPack();
    var first = pack.templateAssociations().getFirst();
    var widened = new ArrayList<>(first.clauseSlugs());
    widened.add("sectional-title-levies");
    var associations = new ArrayList<>(pack.templateAssociations());
    associations.set(
        0,
        new ClausePackDefinition.TemplateAssociation(
            first.templatePackId(), first.templateKey(), widened, first.requiredSlugs()));

    assertThat(violations(withAssociations(pack, associations)))
        .singleElement()
        .asString()
        .contains("declared but not reconciled")
        .contains("sectional-title-levies")
        .contains(first.templateKey());
  }

  @Test
  void guard_flagsAMissingUndeclaredOrDuplicateReconcileEntry() throws IOException {
    var pack = conveyancingPack();
    var dropped = pack.reconcile().getFirst();

    var missing = new ArrayList<>(pack.reconcile());
    missing.removeFirst();
    assertThat(violations(withReconcile(pack, missing)))
        .singleElement()
        .asString()
        .contains("declared but not reconciled")
        .contains(dropped.key());

    var undeclared = new ArrayList<>(pack.reconcile());
    undeclared.add(
        new PackReconcileEntry(
            ClausePackSeeder.ASSOCIATION_ENTITY,
            "voetstoots",
            ClausePackSeeder.ASSOCIATION_FIELD,
            null,
            "deed-of-transfer"));
    assertThat(violations(withReconcile(pack, undeclared)))
        .singleElement()
        .asString()
        .contains("reconciled but not declared")
        .contains("voetstoots");

    var duplicated = new ArrayList<>(pack.reconcile());
    duplicated.add(dropped);
    assertThat(violations(withReconcile(pack, duplicated)))
        .singleElement()
        .asString()
        .contains("duplicate reconcile entry")
        .contains(dropped.key());
  }

  // --- the rule ---

  /** Empty when the pack's association reconcile entries match its declarations exactly. */
  static List<String> violations(ClausePackDefinition pack) {
    List<Pair> reconciled = reconcilePairs(pack);
    Set<Pair> declared = declaredPairs(pack);
    var problems = new ArrayList<String>();
    var seen = new HashSet<Pair>();
    for (Pair pair : reconciled) {
      if (!seen.add(pair)) {
        problems.add("duplicate reconcile entry " + pair);
      }
    }
    for (Pair pair : declared) {
      if (!seen.contains(pair)) {
        problems.add("declared but not reconciled " + pair);
      }
    }
    for (Pair pair : seen) {
      if (!declared.contains(pair)) {
        problems.add("reconciled but not declared " + pair);
      }
    }
    return problems;
  }

  /**
   * The pack's association reconcile entries as (to, key) pairs, in file order (duplicates kept).
   */
  static List<Pair> reconcilePairs(ClausePackDefinition pack) {
    if (pack.reconcile() == null) {
      return List.of();
    }
    return pack.reconcile().stream()
        .filter(e -> ClausePackSeeder.ASSOCIATION_ENTITY.equals(e.entity()))
        .map(e -> new Pair(String.valueOf(e.to()), e.key()))
        .toList();
  }

  static Set<Pair> declaredPairs(ClausePackDefinition pack) {
    var pairs = new LinkedHashSet<Pair>();
    if (pack.templateAssociations() != null) {
      for (var assoc : pack.templateAssociations()) {
        for (String slug : assoc.clauseSlugs()) {
          pairs.add(new Pair(assoc.templateKey(), slug));
        }
      }
    }
    return pairs;
  }

  // --- loading ---

  private List<ClausePackDefinition> loadClausePacks() throws IOException {
    var packs = new ArrayList<ClausePackDefinition>();
    for (Resource resource : resolver.getResources(CLAUSE_PACKS)) {
      try (InputStream in = resource.getInputStream()) {
        packs.add(objectMapper.readValue(in, ClausePackDefinition.class));
      }
    }
    assertThat(packs).as("clause packs on the classpath").isNotEmpty();
    return packs;
  }

  private ClausePackDefinition conveyancingPack() throws IOException {
    return loadClausePacks().stream()
        .filter(p -> CONVEYANCING_PACK.equals(p.packId()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * {@code clauseBlock} {@code attrs.slug} values of a pack template's content, in document order.
   */
  private List<String> embeddedClauseSlugs(String templatePackId, String templateKey)
      throws IOException {
    for (Resource packResource : resolver.getResources(TEMPLATE_PACKS)) {
      JsonNode pack = readTree(packResource);
      if (!templatePackId.equals(pack.path("packId").asText())) {
        continue;
      }
      for (JsonNode template : pack.path("templates")) {
        if (templateKey.equals(template.path("templateKey").asText())) {
          var slugs = new ArrayList<String>();
          collectClauseSlugs(
              readTree(packResource.createRelative(template.path("contentFile").asText())), slugs);
          return slugs;
        }
      }
      throw new AssertionError(
          "template " + templateKey + " not in template pack " + templatePackId);
    }
    throw new AssertionError("template pack " + templatePackId + " not on the classpath");
  }

  private JsonNode readTree(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return objectMapper.readTree(in);
    }
  }

  private static void collectClauseSlugs(JsonNode node, List<String> slugs) {
    if ("clauseBlock".equals(node.path("type").asText())) {
      slugs.add(node.path("attrs").path("slug").asText());
    }
    for (JsonNode child : node.path("content")) {
      collectClauseSlugs(child, slugs);
    }
  }

  private static ClausePackDefinition withAssociations(
      ClausePackDefinition pack, List<ClausePackDefinition.TemplateAssociation> associations) {
    return new ClausePackDefinition(
        pack.packId(),
        pack.version(),
        pack.verticalProfile(),
        pack.name(),
        pack.description(),
        pack.clauses(),
        associations,
        pack.reconcile());
  }

  private static ClausePackDefinition withReconcile(
      ClausePackDefinition pack, List<PackReconcileEntry> reconcile) {
    return new ClausePackDefinition(
        pack.packId(),
        pack.version(),
        pack.verticalProfile(),
        pack.name(),
        pack.description(),
        pack.clauses(),
        pack.templateAssociations(),
        reconcile);
  }
}
