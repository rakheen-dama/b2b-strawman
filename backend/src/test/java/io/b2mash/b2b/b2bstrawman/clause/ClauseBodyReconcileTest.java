package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Hibernate test of the ADR-356 {@code clause} {@code body} resolver (628C, U21 terminology,
 * ruling R-1): the {@code legal-za-clauses} and {@code standard-clauses} v2 blocks take a pristine
 * v1 SYSTEM clause body (whole-body {@code sha256:} equality) to the shipped body through the
 * startup path {@code ClausePackSeeder.seedPacksForTenant} → version gate → {@code
 * resolveReconcileTarget}. Ownership ({@code pack_id} + {@code source = SYSTEM} + active) is pinned
 * by the mocked-repository cases {@code clonedOrInactiveRowUnderThePackSlug_*}, because a real
 * clone can never share the SYSTEM clause's slug ({@code clauses.slug} is unique).
 *
 * <p>The v1 bodies come from fixtures copied from git ({@code fe579bdc9}) before the packs were
 * edited ({@code fixtures/clause-packs/*-v1/pack.json}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClauseBodyReconcileTest {

  private static final String ORG_ID = "org_clause_body_reconcile_test";
  static final String LEGAL_PACK = "legal-za-clauses";
  static final String STANDARD_PACK = "standard-clauses";
  static final String FEE_CLAUSE = "legal-za-fees-hourly";

  /** pack id → the clause slugs whose bodies v2 rewords. */
  static final Map<String, List<String>> REWORDED =
      Map.of(
          LEGAL_PACK, List.of(FEE_CLAUSE), STANDARD_PACK, List.of("payment-terms", "fee-schedule"));

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ClausePackSeeder clausePackSeeder;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Clause Body Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void pristineV1Bodies_areReconciledToV2() {
    simulateV1(true);

    clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          REWORDED.forEach(
              (packId, slugs) -> {
                for (String slug : slugs) {
                  assertThat(ContentHashUtil.hashOf(systemClause(slug).getBody()))
                      .as("%s carries the shipped body", slug)
                      .isEqualTo(ContentHashUtil.hashOf(shippedBody(packId, slug)));
                }
                assertThat(ledgerVersion(packId)).as(packId).isEqualTo("2");
              });
          assertThat(systemClause(FEE_CLAUSE).getBody().toString())
              .contains("Fee notes shall be rendered monthly")
              .contains("the date of the fee note")
              .doesNotContain("Invoices");
          return null;
        });
  }

  /**
   * A firm clone under its own slug carrying the pack id and the v1 body survives byte-identical
   * while the SYSTEM clause is reconciled. Resolution is by the pack's clause slug, so the clone is
   * never even looked up — this does NOT exercise the {@code source == SYSTEM} filter; that is
   * {@link #clonedOrInactiveRowUnderThePackSlug_isNeverATarget()}.
   */
  @Test
  void cloneUnderAnotherSlugWithTheV1Body_isNeverTouched() {
    simulateV1(true);
    UUID cloneId =
        inTenantTx(
            () -> {
              var clone = Clause.cloneFrom(systemClause(FEE_CLAUSE), FEE_CLAUSE + "-firm-copy");
              assertThat(clone.getPackId()).isEqualTo(LEGAL_PACK);
              return clauseRepository.save(clone).getId();
            });
    try {
      clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

      inTenantTx(
          () -> {
            var clone = clauseRepository.findById(cloneId).orElseThrow();
            assertThat(ContentHashUtil.hashOf(clone.getBody()))
                .isEqualTo(ContentHashUtil.hashOf(v1Body(LEGAL_PACK, FEE_CLAUSE)));
            assertThat(ContentHashUtil.hashOf(systemClause(FEE_CLAUSE).getBody()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(LEGAL_PACK, FEE_CLAUSE)));
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            clauseRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  @Test
  void equalVersion_isANoOp() {
    simulateV1(false);

    clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          assertThat(ContentHashUtil.hashOf(systemClause(FEE_CLAUSE).getBody()))
              .isEqualTo(ContentHashUtil.hashOf(v1Body(LEGAL_PACK, FEE_CLAUSE)));
          return null;
        });
    // restore the shipped bodies for sibling tests
    simulateV1(true);
    clausePackSeeder.seedPacksForTenant(schema, ORG_ID);
  }

  @Test
  void unsupportedOrMismatchedBodyEntries_warnAndNeverThrow() {
    var pack = readPack("clause-packs/" + LEGAL_PACK + "/pack.json");
    String v1 = hashRef(v1Body(LEGAL_PACK, FEE_CLAUSE));
    var entries =
        List.of(
            new PackReconcileEntry(
                "clause", FEE_CLAUSE, "body", v1, PackReconcileEntry.HASH_PREFIX + "0".repeat(64)),
            new PackReconcileEntry(
                "clause",
                "no-such-clause",
                "body",
                v1,
                hashRef(shippedBody(LEGAL_PACK, FEE_CLAUSE))),
            new PackReconcileEntry("clause", FEE_CLAUSE, "body", "plain", "not a hash"),
            new PackReconcileEntry("clause", FEE_CLAUSE, "title", "a", "b"));
    simulateV1(false);
    try {
      var logs =
          captureLogs(
              () ->
                  inTenantTx(
                      () -> {
                        for (var entry : entries) {
                          clausePackSeeder.applyReconcileEntry(
                              pack,
                              new ClassPathResource("clause-packs/" + LEGAL_PACK + "/pack.json"),
                              entry,
                              schema);
                        }
                        return null;
                      }));

      assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(4);
      inTenantTx(
          () -> {
            assertThat(ContentHashUtil.hashOf(systemClause(FEE_CLAUSE).getBody()))
                .as("no unsupported entry writes the body")
                .isEqualTo(ContentHashUtil.hashOf(v1Body(LEGAL_PACK, FEE_CLAUSE)));
            return null;
          });
    } finally {
      // restore the shipped bodies for sibling tests
      simulateV1(true);
      clausePackSeeder.seedPacksForTenant(schema, ORG_ID);
    }
  }

  /**
   * Every previously shipped version of the clause packs, oldest first, as the fixture suffix
   * ({@code fixtures/clause-packs/<pack>-<suffix>/pack.json}). Today only v1; a v3 body edit copies
   * the v2 pack to a {@code -v2} fixture and appends it here — a one-line change.
   */
  static final List<String> PRIOR_VERSIONS = List.of("v1");

  /**
   * Guard (no tenant needed) for the cumulative-chain convention ({@code backend/CLAUDE.md} › Pack
   * reconcile contract): each shipped {@code clause} entry targets a reworded clause of its pack;
   * every {@code to} is the head — the {@code sha256:} of the body shipped on the classpath —
   * because the resolver WARN-skips any other {@code to}; every {@code from} is the hash of the
   * body of SOME prior version in {@link #PRIOR_VERSIONS}. When a body is re-edited, re-point every
   * older entry's {@code to} to the new head and add one entry whose {@code from} is the previous
   * head. A body edit without re-computing {@code to} fails here, not at a tenant's boot.
   */
  @Test
  void shippedClauseBodyEntries_everyToIsHead_everyFromIsAPriorVersion() {
    for (var reworded : REWORDED.entrySet()) {
      String packId = reworded.getKey();
      var pack = readPack("clause-packs/" + packId + "/pack.json");
      assertThat(pack.version()).as(packId).isEqualTo(PRIOR_VERSIONS.size() + 1);
      var entries =
          PackReconcileEntry.parse(packId, pack.reconcile()).stream()
              .filter(e -> "clause".equals(e.entity()))
              .toList();
      assertThat(entries)
          .extracting(PackReconcileEntry::key)
          .hasSameElementsAs(reworded.getValue());
      for (PackReconcileEntry e : entries) {
        assertThat(e.field()).as(e.key()).isEqualTo("body");
        assertThat(e.to())
            .as("%s to is the head", e.key())
            .isEqualTo(hashRef(shippedBody(packId, e.key())));
        assertThat(e.from())
            .as("%s from is a prior version's body", e.key())
            .isIn(
                PRIOR_VERSIONS.stream().map(v -> hashRef(priorBody(packId, v, e.key()))).toList());
        assertThat(e.to()).as(e.key()).isNotEqualTo(e.from());
      }
    }
  }

  /**
   * Mocked repository: the row {@code findBySlug(FEE_CLAUSE)} returns carries this pack's {@code
   * pack_id}, the pack slug and the v1 body (so the one rule WOULD apply), but is a CLONED or an
   * inactive SYSTEM row — never a target, nothing written. The active SYSTEM row is the positive
   * control. Removing the {@code source == SYSTEM && isActive()} filter in {@code
   * ClausePackSeeder.resolveReconcileTarget} turns this red.
   */
  @Test
  void clonedOrInactiveRowUnderThePackSlug_isNeverATarget() {
    var pack = readPack("clause-packs/" + LEGAL_PACK + "/pack.json");
    var entry =
        PackReconcileEntry.parse(LEGAL_PACK, pack.reconcile()).stream()
            .filter(e -> "clause".equals(e.entity()) && FEE_CLAUSE.equals(e.key()))
            .findFirst()
            .orElseThrow();
    var resource = new ClassPathResource("clause-packs/" + LEGAL_PACK + "/pack.json");

    var system = systemRow();
    var clone = Clause.cloneFrom(system, FEE_CLAUSE);
    assertThat(clone.getSource()).isEqualTo(ClauseSource.CLONED);
    assertThat(clone.getPackId()).isEqualTo(LEGAL_PACK);
    var inactive = systemRow();
    inactive.deactivate();

    for (var row : List.of(clone, inactive)) {
      var repository = mock(ClauseRepository.class);
      when(repository.findBySlug(FEE_CLAUSE)).thenReturn(Optional.of(row));
      var seeder = seederWith(repository);

      assertThat(seeder.resolveReconcileTarget(pack, entry, resource))
          .as("%s active=%s", row.getSource(), row.isActive())
          .isEmpty();
      var logs = captureLogs(() -> seeder.reconcileInstalledPack(pack, resource, "t"));
      assertThat(ContentHashUtil.hashOf(row.getBody()))
          .as("%s active=%s keeps the v1 body", row.getSource(), row.isActive())
          .isEqualTo(ContentHashUtil.hashOf(v1Body(LEGAL_PACK, FEE_CLAUSE)));
      assertThat(logs)
          .anyMatch(
              m ->
                  m.startsWith("Reconcile skipped (no pack-seeded row)")
                      && m.contains("'" + FEE_CLAUSE + "' field body"));
    }

    // positive control: the active SYSTEM row is resolved and reconciled
    var repository = mock(ClauseRepository.class);
    when(repository.findBySlug(FEE_CLAUSE)).thenReturn(Optional.of(system));
    seederWith(repository).reconcileInstalledPack(pack, resource, "t");
    assertThat(ContentHashUtil.hashOf(system.getBody()))
        .isEqualTo(ContentHashUtil.hashOf(shippedBody(LEGAL_PACK, FEE_CLAUSE)));
  }

  // --- helpers ---

  /** An unpersisted active SYSTEM {@code legal-za-fees-hourly} clause carrying the v1 body. */
  private static Clause systemRow() {
    return Clause.createSystemClause(
        "Fees (hourly)", FEE_CLAUSE, v1Body(LEGAL_PACK, FEE_CLAUSE), "Fees", null, LEGAL_PACK, 0);
  }

  private static ClausePackSeeder seederWith(ClauseRepository repository) {
    return new ClausePackSeeder(
        mock(ResourcePatternResolver.class),
        JSON,
        repository,
        mock(TemplateClauseRepository.class),
        mock(DocumentTemplateRepository.class),
        mock(OrgSettingsRepository.class),
        mock(TenantTransactionHelper.class),
        mock(PackVersionGate.class));
  }

  /** Writes the v1 bodies back onto the SYSTEM clauses; when {@code rewind}, ledgers 2 → 1. */
  private void simulateV1(boolean rewind) {
    inTenantTx(
        () -> {
          REWORDED.forEach(
              (packId, slugs) -> {
                for (String slug : slugs) {
                  var clause = systemClause(slug);
                  clause.update(
                      clause.getTitle(),
                      clause.getSlug(),
                      clause.getDescription(),
                      v1Body(packId, slug),
                      clause.getCategory());
                  clauseRepository.save(clause);
                }
                if (rewind) {
                  assertThat(packInstallRepository.compareAndSetVersion(packId, "2", "1"))
                      .as("rewind %s 2 -> 1", packId)
                      .isEqualTo(1);
                }
              });
          return null;
        });
  }

  private Clause systemClause(String slug) {
    var clause = clauseRepository.findBySlug(slug).orElseThrow();
    assertThat(clause.getSource()).isEqualTo(ClauseSource.SYSTEM);
    return clause;
  }

  private String ledgerVersion(String packId) {
    return packInstallRepository.findByPackId(packId).orElseThrow().getPackVersion();
  }

  static Map<String, Object> v1Body(String packId, String slug) {
    return priorBody(packId, "v1", slug);
  }

  static Map<String, Object> priorBody(String packId, String version, String slug) {
    return body(readPack("fixtures/clause-packs/" + packId + "-" + version + "/pack.json"), slug);
  }

  static Map<String, Object> shippedBody(String packId, String slug) {
    return body(readPack("clause-packs/" + packId + "/pack.json"), slug);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> body(ClausePackDefinition pack, String slug) {
    return (Map<String, Object>)
        pack.clauses().stream()
            .filter(c -> slug.equals(c.slug()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(pack.packId() + " has no clause " + slug))
            .body();
  }

  private static ClausePackDefinition readPack(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JSON.readValue(in, ClausePackDefinition.class);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  private static String hashRef(Map<String, Object> body) {
    return PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(body);
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(ClausePackSeeder.class);
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    try {
      action.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
