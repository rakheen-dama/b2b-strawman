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

  static final String CONVEYANCING_PACK = "conveyancing-za-clauses";

  /** G88-F12: the conveyancing clauses whose party bindings v3 rebinds. */
  static final List<String> CONVEYANCING_REBOUND =
      List.of(
          "voetstoots",
          "occupation-date",
          "suspensive-bond",
          "transfer-duty-liability",
          "sectional-title-levies");

  /** pack id → the clause slugs whose bodies v2 rewords. */
  static final Map<String, List<String>> REWORDED =
      Map.of(
          LEGAL_PACK, List.of(FEE_CLAUSE), STANDARD_PACK, List.of("payment-terms", "fee-schedule"));

  /**
   * The shipped version of each reworded pack: {@code legal-za-clauses} v3 (G89-F6) re-describes
   * the trust-deposits clause; its bodies are unchanged since v2.
   */
  static final Map<String, String> CURRENT_VERSIONS = Map.of(LEGAL_PACK, "3", STANDARD_PACK, "2");

  /** G89-F6 / D12: the trust-deposits clause description, v2 (Attorneys Act) and v3 (LPA). */
  static final String TRUST_DEPOSITS = "legal-za-trust-deposits";

  static final String V2_TRUST_DEPOSITS_DESCRIPTION =
      "Trust account deposit requirements per Section 86 of the Attorneys Act";
  static final String V3_TRUST_DEPOSITS_DESCRIPTION =
      "Trust account deposit requirements per section 86 of the Legal Practice Act 28 of 2014";

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
                assertThat(ledgerVersion(packId))
                    .as(packId)
                    .isEqualTo(CURRENT_VERSIONS.get(packId));
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
   * Every previously shipped clause BODY version of the clause packs, oldest first, as the fixture
   * suffix ({@code fixtures/clause-packs/<pack>-<suffix>/pack.json}). Today only v1 (the {@code
   * legal-za-clauses} v3 bump re-describes a clause, its bodies are the v2 bodies); a later body
   * edit copies the current pack to a fixture and appends it here — a one-line change.
   */
  static final List<String> PRIOR_VERSIONS = List.of("v1");

  /**
   * G89-F6 / D12: a pristine v2 tenant's trust-deposits SYSTEM clause gets the Legal Practice Act
   * description on the v3 bump; its body is untouched.
   */
  @Test
  void pristineV2TrustDepositsDescription_isReconciledToTheLegalPracticeActWording() {
    var bodyHashBefore = setTrustDepositsDescriptionAndRewind(V2_TRUST_DEPOSITS_DESCRIPTION);

    clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          var clause = systemClause(TRUST_DEPOSITS);
          assertThat(clause.getDescription()).isEqualTo(V3_TRUST_DEPOSITS_DESCRIPTION);
          assertThat(ContentHashUtil.hashOf(clause.getBody())).isEqualTo(bodyHashBefore);
          assertThat(ledgerVersion(LEGAL_PACK)).isEqualTo("3");
          return null;
        });
  }

  /** A description that is neither the v2 nor the v3 wording is left alone (the one rule). */
  @Test
  void otherTrustDepositsDescription_isLeftAlone() {
    setTrustDepositsDescriptionAndRewind("Our own deposit wording");
    try {
      clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

      inTenantTx(
          () -> {
            assertThat(systemClause(TRUST_DEPOSITS).getDescription())
                .isEqualTo("Our own deposit wording");
            assertThat(ledgerVersion(LEGAL_PACK)).isEqualTo("3");
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            var clause = systemClause(TRUST_DEPOSITS);
            clause.update(
                clause.getTitle(),
                clause.getSlug(),
                V3_TRUST_DEPOSITS_DESCRIPTION,
                clause.getBody(),
                clause.getCategory());
            return clauseRepository.save(clause);
          });
    }
  }

  /** The shipped description entry: v2 wording → the description shipped on the classpath. */
  @Test
  void shippedTrustDepositsDescriptionEntry_fromIsV2_toIsTheShippedDescription() {
    var pack = readPack("clause-packs/" + LEGAL_PACK + "/pack.json");
    var entries =
        PackReconcileEntry.parse(LEGAL_PACK, pack.reconcile()).stream()
            .filter(e -> "clause".equals(e.entity()) && "description".equals(e.field()))
            .toList();
    assertThat(entries).extracting(PackReconcileEntry::key).containsExactly(TRUST_DEPOSITS);
    assertThat(entries.getFirst().from()).isEqualTo(V2_TRUST_DEPOSITS_DESCRIPTION);
    assertThat(entries.getFirst().to())
        .isEqualTo(V3_TRUST_DEPOSITS_DESCRIPTION)
        .isEqualTo(
            pack.clauses().stream()
                .filter(c -> TRUST_DEPOSITS.equals(c.slug()))
                .findFirst()
                .orElseThrow()
                .description());
  }

  /** Writes {@code description} onto the trust-deposits SYSTEM clause and rewinds 3 → 2. */
  private String setTrustDepositsDescriptionAndRewind(String description) {
    return inTenantTx(
        () -> {
          var clause = systemClause(TRUST_DEPOSITS);
          clause.update(
              clause.getTitle(),
              clause.getSlug(),
              description,
              clause.getBody(),
              clause.getCategory());
          clauseRepository.save(clause);
          assertThat(packInstallRepository.compareAndSetVersion(LEGAL_PACK, "3", "2"))
              .as("rewind %s 3 -> 2", LEGAL_PACK)
              .isEqualTo(1);
          return ContentHashUtil.hashOf(clause.getBody());
        });
  }

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
      assertThat(pack.version())
          .as(packId)
          .isEqualTo(Integer.parseInt(CURRENT_VERSIONS.get(packId)));
      var entries =
          PackReconcileEntry.parse(packId, pack.reconcile()).stream()
              .filter(e -> "clause".equals(e.entity()) && "body".equals(e.field()))
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
   * G88-F12: the {@code conveyancing-za-clauses} v3 block rebinds the contract parties of five
   * clauses ({@code customer.name} / {@code org.name} → the explicit seller / purchaser matter
   * fields; sentence text unchanged). A tenant at v2 (v1 and v2 bodies are identical — v2 only
   * added template associations) is reconciled to the shipped bodies; FICA and the clearance
   * clauses keep binding the firm.
   */
  @Test
  void conveyancingV2Bodies_areReconciledToV3_partiesBound() {
    inTenantTx(
        () -> {
          for (String slug : CONVEYANCING_REBOUND) {
            var clause = systemClause(slug);
            clause.update(
                clause.getTitle(),
                clause.getSlug(),
                clause.getDescription(),
                priorBody(CONVEYANCING_PACK, "v2", slug),
                clause.getCategory());
            clauseRepository.save(clause);
          }
          assertThat(packInstallRepository.compareAndSetVersion(CONVEYANCING_PACK, "3", "2"))
              .isEqualTo(1);
          return null;
        });

    clausePackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          for (String slug : CONVEYANCING_REBOUND) {
            String body = JSON.writeValueAsString(systemClause(slug).getBody());
            assertThat(ContentHashUtil.hashOf(systemClause(slug).getBody()))
                .as("%s carries the shipped body", slug)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(CONVEYANCING_PACK, slug)));
            assertThat(body).as(slug).doesNotContain("customer.name");
            assertThat(body).as(slug).contains("project.customFields.purchaser_name");
          }
          String voetstoots = JSON.writeValueAsString(systemClause("voetstoots").getBody());
          assertThat(voetstoots)
              .contains("project.customFields.seller_name")
              .doesNotContain("org.name");
          assertThat(JSON.writeValueAsString(systemClause("fica-compliance").getBody()))
              .contains("org.name");
          assertThat(ledgerVersion(CONVEYANCING_PACK)).isEqualTo("3");
          return null;
        });
  }

  /**
   * Cumulative-chain guard for {@code conveyancing-za-clauses} (its own history: v3 is the first
   * body edit, so every {@code from} is the v2 body): one {@code clause body} entry per rebound
   * clause, {@code to} = the shipped head.
   */
  @Test
  void conveyancingClauseBodyEntries_toIsHead_fromIsV2() {
    var pack = readPack("clause-packs/" + CONVEYANCING_PACK + "/pack.json");
    assertThat(pack.version()).isEqualTo(3);
    var entries =
        PackReconcileEntry.parse(CONVEYANCING_PACK, pack.reconcile()).stream()
            .filter(e -> "clause".equals(e.entity()))
            .toList();
    assertThat(entries).extracting(PackReconcileEntry::key).hasSameElementsAs(CONVEYANCING_REBOUND);
    for (PackReconcileEntry e : entries) {
      assertThat(e.field()).as(e.key()).isEqualTo("body");
      assertThat(e.to()).as(e.key()).isEqualTo(hashRef(shippedBody(CONVEYANCING_PACK, e.key())));
      assertThat(e.from())
          .as(e.key())
          .isEqualTo(hashRef(priorBody(CONVEYANCING_PACK, "v2", e.key())));
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
                  assertThat(
                          packInstallRepository.compareAndSetVersion(
                              packId, CURRENT_VERSIONS.get(packId), "1"))
                      .as("rewind %s %s -> 1", packId, CURRENT_VERSIONS.get(packId))
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
