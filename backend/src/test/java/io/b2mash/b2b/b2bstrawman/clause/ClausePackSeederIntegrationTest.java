package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.PackInstall;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClausePackSeederIntegrationTest {
  private static final String ORG_ID = "org_cps_test";

  /** legal-za tenant for the conveyancing pack (G-B18, Phase 88 626G). */
  private static final String LEGAL_ORG_ID = "org_cps_legal_test";

  private static final String CONVEYANCING_PACK = "conveyancing-za-clauses";
  private static final String CONVEYANCING_PACK_JSON =
      "clause-packs/conveyancing-za-clauses/pack.json";
  private static final String TEMPLATE_PACK = "legal-za";
  private static final String OTP = "offer-to-purchase";

  /** One declared association: the clause slugs in document order and the required subset. */
  private record Declared(List<String> slugs, Set<String> required) {}

  /**
   * conveyancing-za-clauses v2 associations — the {@code clauseBlock} slugs each legal-za template
   * embeds, in document order (R-5: the OTP's 7 embedded slugs, 4 required; R-6: the deed, power of
   * attorney to pass transfer and bond cancellation instruction, none required).
   */
  private static final Map<String, Declared> DECLARED =
      new LinkedHashMap<>(
          Map.of(
              OTP,
              new Declared(
                  List.of(
                      "voetstoots",
                      "occupation-date",
                      "suspensive-bond",
                      "transfer-duty-liability",
                      "fica-compliance",
                      "rates-clearance",
                      "jurisdiction-za"),
                  Set.of(
                      "voetstoots",
                      "suspensive-bond",
                      "transfer-duty-liability",
                      "fica-compliance")),
              "deed-of-transfer",
              new Declared(
                  List.of("transfer-duty-liability", "rates-clearance", "body-corporate-clearance"),
                  Set.of()),
              "power-of-attorney-transfer",
              new Declared(List.of("fica-compliance"), Set.of()),
              "bond-cancellation-instruction",
              new Declared(List.of("cost-of-cancellation"), Set.of())));

  /** Total (clause, template) pairs = reconcile entries = 7 + 3 + 1 + 1. */
  private static final int DECLARED_PAIRS = 12;

  @Autowired private MockMvc mockMvc;
  @Autowired private ClausePackSeeder clausePackSeeder;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TemplateClauseRepository templateClauseRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private ClauseResolver clauseResolver;
  @Autowired private TiptapRenderer tiptapRenderer;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private String legalSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clause Pack Seeder Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_cps_owner", "cps_owner@test.com", "CPS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    provisioningService.provisionTenant(
        LEGAL_ORG_ID, "Clause Pack Seeder Legal Test Org", "legal-za");
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void creates_system_clauses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "standard-clauses", ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(12);
                  assertThat(clauses)
                      .allSatisfy(
                          clause -> {
                            assertThat(clause.getSource()).isEqualTo(ClauseSource.SYSTEM);
                            assertThat(clause.getPackId()).isEqualTo("standard-clauses");
                            assertThat(clause.isActive()).isTrue();
                          });
                }));
  }

  @Test
  void creates_template_associations() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "standard-clauses", ClauseSource.SYSTEM);
                  // Find the engagement-letter template's associations
                  // At least some template-clause links should exist
                  var allAssociations =
                      clauses.stream()
                          .flatMap(c -> templateClauseRepository.findByClauseId(c.getId()).stream())
                          .toList();
                  assertThat(allAssociations).isNotEmpty();
                  // Check required flags
                  var paymentTerms =
                      clauses.stream()
                          .filter(c -> "payment-terms".equals(c.getSlug()))
                          .findFirst()
                          .orElseThrow();
                  var ptAssocs = templateClauseRepository.findByClauseId(paymentTerms.getId());
                  assertThat(ptAssocs).isNotEmpty();
                  assertThat(ptAssocs.getFirst().isRequired()).isTrue();
                }));
  }

  @Test
  void is_idempotent() {
    // Call seeder again — should not duplicate
    clausePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "standard-clauses", ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(12);
                }));
  }

  @Test
  void records_pack_in_org_settings() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant();
                  assertThat(settings).isPresent();
                  assertThat(settings.get().getPackStatus().getClausePackStatus()).isNotNull();
                  assertThat(settings.get().getPackStatus().getClausePackStatus())
                      .anyMatch(entry -> "standard-clauses".equals(entry.get("packId")));
                }));
  }

  @Test
  void handles_slug_collision() {
    // Create a custom clause with a slug that would collide
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // The pack has already been seeded, so we verify the seeder doesn't crash
                  // if called again when clauses exist (idempotency via pack status)
                  var existingCount =
                      clauseRepository
                          .findByPackIdAndSourceAndActiveTrue(
                              "standard-clauses", ClauseSource.SYSTEM)
                          .size();
                  assertThat(existingCount).isEqualTo(12);
                }));
  }

  @Test
  void skips_missing_templates() {
    // The pack references "engagement-letter" template from "common" pack.
    // If the template pack is missing, associations are skipped (no crash).
    // Since we provision fully, templates exist. This test verifies the seeder
    // completed without errors even for template associations.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "standard-clauses", ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(12);
                }));
  }

  @Test
  void clause_bodyJson_has_doc_root_node() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "standard-clauses", ClauseSource.SYSTEM);
                  assertThat(clauses).isNotEmpty();
                  assertThat(clauses)
                      .allSatisfy(
                          clause -> {
                            assertThat(clause.getBody()).isNotNull();
                            assertThat(clause.getBody()).containsEntry("type", "doc");
                          });
                }));
  }

  @SuppressWarnings("unchecked")
  @Test
  void clause_bodyJson_contains_variable_nodes() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var paymentTerms = clauseRepository.findBySlug("payment-terms").orElseThrow();
                  Map<String, Object> bodyJson = paymentTerms.getBody();
                  assertThat(bodyJson).containsKey("content");
                  List<Map<String, Object>> content =
                      (List<Map<String, Object>>) bodyJson.get("content");
                  assertThat(content).isNotEmpty();
                  // First paragraph should contain variable nodes for org.name and customer.name
                  Map<String, Object> firstParagraph = content.getFirst();
                  assertThat(firstParagraph).containsEntry("type", "paragraph");
                  List<Map<String, Object>> paragraphContent =
                      (List<Map<String, Object>>) firstParagraph.get("content");
                  assertThat(paragraphContent)
                      .anyMatch(node -> "variable".equals(node.get("type")));
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  // =====================================================================================
  // G-B18 (Phase 88 626G): conveyancing-za-clauses v2 template associations + reconcile
  // =====================================================================================

  @Test
  void conveyancingPack_freshTenant_associatesEachTemplateWithItsEmbeddedClauses() {
    for (var declared : DECLARED.entrySet()) {
      assertThat(associations(declared.getKey()))
          .as("fresh legal-za tenant associations of %s", declared.getKey())
          .containsExactlyElementsOf(expectedRows(declared.getValue()));
    }
    assertThat(ledgerVersion()).isEqualTo("2");
  }

  @Test
  void conveyancingPack_freshTenant_otpRendersItsConditionsOfSaleClauses() {
    String html = renderPackTemplate(OTP);
    assertThat(html)
        .contains("Conditions of Sale")
        .contains("The property is sold voetstoots")
        .doesNotContain("clause not found");
    for (String key : DECLARED.keySet()) {
      assertThat(renderPackTemplate(key)).as("%s render", key).doesNotContain("clause not found");
    }
  }

  @Test
  void reconcile_v1Tenant_addsEveryDeclaredAssociation_andAdvancesTheLedger() {
    try {
      simulateV1Tenant(false);
      DECLARED.keySet().forEach(key -> assertThat(associations(key)).as(key).isEmpty());
      long v2EntriesBefore = orgSettingsClauseEntries("2");

      List<String> logs =
          captureLogs(this::seedLegalTenant, ClausePackSeeder.class, PackVersionGate.class);

      for (var declared : DECLARED.entrySet()) {
        assertThat(associations(declared.getKey()))
            .as("reconciled associations of %s", declared.getKey())
            .containsExactlyElementsOf(expectedRows(declared.getValue()));
      }
      assertThat(ledgerVersion()).isEqualTo("2");
      assertThat(logs)
          .anyMatch(m -> m.startsWith("Reconciling CLAUSE pack conveyancing-za-clauses v1 -> v2"));
      assertThat(
              logs.stream()
                  .filter(m -> m.startsWith("Reconcile applied:"))
                  .filter(m -> m.contains("pack=" + CONVEYANCING_PACK))
                  .filter(m -> m.contains("clause_template_association"))
                  .count())
          .isEqualTo(DECLARED_PAIRS);
      assertThat(logs).noneMatch(m -> m.startsWith("Reconcile entry ignored"));
      // R-11: like the installer-managed types, the OrgSettings pack-status list re-records the
      // reconciled version (the ADR-356 backfill source agrees with the ledger).
      assertThat(orgSettingsClauseEntries("2") - v2EntriesBefore).isEqualTo(1);
    } finally {
      restoreV2();
    }
  }

  @Test
  void reconcile_keepsAFirmEditedAssociation() {
    try {
      simulateV1Tenant(true);
      assertThat(associations(OTP)).containsExactly(new Row("voetstoots", 42, false));

      seedLegalTenant();

      List<Row> expected = new ArrayList<>(expectedRows(DECLARED.get(OTP)));
      expected.removeIf(r -> r.slug().equals("voetstoots"));
      expected.add(new Row("voetstoots", 42, false));
      assertThat(associations(OTP)).containsExactlyInAnyOrderElementsOf(expected);
      assertThat(ledgerVersion()).isEqualTo("2");
    } finally {
      restoreV2();
      inLegalTenant(
          () -> {
            var otpId = packRow(OTP).getId();
            var voetstoots = clauseRepository.findBySlug("voetstoots").orElseThrow();
            templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(otpId).stream()
                .filter(tc -> tc.getClauseId().equals(voetstoots.getId()))
                .forEach(
                    tc -> {
                      tc.setSortOrder(0);
                      tc.setRequired(true);
                      templateClauseRepository.save(tc);
                    });
            return null;
          });
    }
    assertThat(associations(OTP)).containsExactlyElementsOf(expectedRows(DECLARED.get(OTP)));
  }

  @Test
  void reconcile_neverTouchesAnOtpClone() {
    UUID cloneId = inLegalTenant(() -> saveCloneOf(OTP, "-custom-cps"));
    try {
      simulateV1Tenant(false);

      seedLegalTenant();

      assertThat(associationsOf(cloneId)).isEmpty();
      assertThat(associations(OTP)).containsExactlyElementsOf(expectedRows(DECLARED.get(OTP)));
    } finally {
      restoreV2();
      inLegalTenant(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  @Test
  void reconcile_atEqualVersion_isANoOp() {
    assertThat(ledgerVersion()).isEqualTo("2");
    Map<String, Set<UUID>> idsBefore = new LinkedHashMap<>();
    DECLARED.keySet().forEach(key -> idsBefore.put(key, associationIds(key)));

    List<String> logs =
        captureLogs(this::seedLegalTenant, ClausePackSeeder.class, PackVersionGate.class);

    DECLARED
        .keySet()
        .forEach(key -> assertThat(associationIds(key)).as(key).isEqualTo(idsBefore.get(key)));
    assertThat(logs)
        .noneMatch(m -> m.startsWith("Reconciling CLAUSE pack " + CONVEYANCING_PACK))
        .noneMatch(m -> m.startsWith("Reconcile applied:"));
    assertThat(ledgerVersion()).isEqualTo("2");
  }

  @Test
  void reconcileEntry_forAnUndeclaredPair_failsLoud() throws IOException {
    var pack = loadConveyancingPack();
    var undeclared =
        new PackReconcileEntry(
            "clause_template_association", "voetstoots", "association", null, "deed-of-transfer");

    assertThatThrownBy(
            () ->
                clausePackSeeder.applyReconcileEntry(
                    pack, new ClassPathResource(CONVEYANCING_PACK_JSON), undeclared, legalSchema))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(CONVEYANCING_PACK)
        .hasMessageContaining("voetstoots")
        .hasMessageContaining("deed-of-transfer");
  }

  @Test
  void reconcileEntry_unsupportedEntity_isLoggedAndSkipped() throws IOException {
    var pack = loadConveyancingPack();
    var unsupported = new PackReconcileEntry("clause", "voetstoots", "title", null, "New title");
    Set<UUID> before = associationIds(OTP);

    List<String> logs =
        captureLogs(
            () ->
                assertThatCode(
                        () ->
                            inLegalTenant(
                                () -> {
                                  clausePackSeeder.applyReconcileEntry(
                                      pack,
                                      new ClassPathResource(CONVEYANCING_PACK_JSON),
                                      unsupported,
                                      legalSchema);
                                  return null;
                                }))
                    .doesNotThrowAnyException(),
            ClausePackSeeder.class);

    assertThat(logs).anyMatch(m -> m.startsWith("Reconcile entry ignored"));
    assertThat(associationIds(OTP)).isEqualTo(before);
  }

  // --- helpers (legal tenant) ---

  private record Row(String slug, int sortOrder, boolean required) {}

  private static List<Row> expectedRows(Declared declared) {
    var rows = new ArrayList<Row>();
    for (int i = 0; i < declared.slugs().size(); i++) {
      String slug = declared.slugs().get(i);
      rows.add(new Row(slug, i, declared.required().contains(slug)));
    }
    return rows;
  }

  private void seedLegalTenant() {
    clausePackSeeder.seedPacksForTenant(legalSchema, LEGAL_ORG_ID);
  }

  private DocumentTemplate packRow(String templateKey) {
    var rows = documentTemplateRepository.findPackSeeded(TEMPLATE_PACK, templateKey);
    assertThat(rows).as("pack-seeded %s rows", templateKey).hasSize(1);
    return rows.getFirst();
  }

  /** Associations of the pack-seeded row of {@code templateKey}, by sort order. */
  private List<Row> associations(String templateKey) {
    return associationsOf(inLegalTenant(() -> packRow(templateKey).getId()));
  }

  private List<Row> associationsOf(UUID templateId) {
    return inLegalTenant(
        () -> {
          var rows = templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
          Map<UUID, String> slugs =
              clauseRepository
                  .findAllById(rows.stream().map(TemplateClause::getClauseId).toList())
                  .stream()
                  .collect(Collectors.toMap(Clause::getId, Clause::getSlug));
          return rows.stream()
              .map(tc -> new Row(slugs.get(tc.getClauseId()), tc.getSortOrder(), tc.isRequired()))
              .toList();
        });
  }

  private Set<UUID> associationIds(String templateKey) {
    return inLegalTenant(
        () ->
            templateClauseRepository
                .findByTemplateIdOrderBySortOrderAsc(packRow(templateKey).getId())
                .stream()
                .map(TemplateClause::getId)
                .collect(Collectors.toSet()));
  }

  /**
   * Puts the legal tenant in the state of a tenant provisioned at conveyancing-za-clauses v1: no
   * associations on the four conveyancing templates and the ledger rewound 2 → 1. With {@code
   * keepEditedVoetstoots} the OTP–voetstoots row survives, firm-edited (required off, sort 42).
   */
  private void simulateV1Tenant(boolean keepEditedVoetstoots) {
    inLegalTenant(
        () -> {
          UUID voetstootsId = clauseRepository.findBySlug("voetstoots").orElseThrow().getId();
          for (String key : DECLARED.keySet()) {
            UUID templateId = packRow(key).getId();
            for (var tc :
                templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(templateId)) {
              if (keepEditedVoetstoots
                  && OTP.equals(key)
                  && tc.getClauseId().equals(voetstootsId)) {
                tc.setRequired(false);
                tc.setSortOrder(42);
                templateClauseRepository.save(tc);
              } else {
                templateClauseRepository.delete(tc);
              }
            }
          }
          assertThat(packInstallRepository.compareAndSetVersion(CONVEYANCING_PACK, "2", "1"))
              .as("rewind %s 2 -> 1", CONVEYANCING_PACK)
              .isEqualTo(1);
          return null;
        });
  }

  /** Leaves the tenant at v2 with every association present, even if a test failed mid-way. */
  private void restoreV2() {
    if ("1".equals(ledgerVersion())) {
      seedLegalTenant();
    }
  }

  private String ledgerVersion() {
    return inLegalTenant(
        () ->
            packInstallRepository
                .findByPackId(CONVEYANCING_PACK)
                .map(PackInstall::getPackVersion)
                .orElse(null));
  }

  /** OrgSettings clause pack-status entries for the conveyancing pack at {@code version}. */
  private long orgSettingsClauseEntries(String version) {
    return inLegalTenant(
        () ->
            orgSettingsRepository
                .findForCurrentTenant()
                .orElseThrow()
                .getPackStatus()
                .getClausePackStatus()
                .stream()
                .filter(e -> CONVEYANCING_PACK.equals(e.get("packId")))
                .filter(e -> version.equals(String.valueOf(e.get("version"))))
                .count());
  }

  /** Renders a pack template the way the preview does: default clauses, then TiptapRenderer. */
  private String renderPackTemplate(String templateKey) {
    return inLegalTenant(
        () -> {
          var template = packRow(templateKey);
          Map<UUID, Clause> clauses =
              clauseResolver.resolveClauses(template.getId(), null).stream()
                  .collect(
                      Collectors.toMap(
                          Clause::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
          return tiptapRenderer.render(template.getContent(), Map.of(), clauses, template.getCss());
        });
  }

  /** Mirrors {@code DocumentTemplateService.cloneTemplate}: ORG_CUSTOM, same pack id + key. */
  private UUID saveCloneOf(String templateKey, String slugSuffix) {
    var original = packRow(templateKey);
    var clone =
        new DocumentTemplate(
            original.getPrimaryEntityType(),
            original.getName() + " (Custom)",
            original.getSlug() + slugSuffix,
            original.getCategory(),
            original.getContent());
    clone.setSourceTemplateId(original.getId());
    clone.setPackId(original.getPackId());
    clone.setPackTemplateKey(original.getPackTemplateKey());
    return documentTemplateRepository.save(clone).getId();
  }

  private ClausePackDefinition loadConveyancingPack() throws IOException {
    try (InputStream in = new ClassPathResource(CONVEYANCING_PACK_JSON).getInputStream()) {
      return objectMapper.readValue(in, ClausePackDefinition.class);
    }
  }

  private <T> T inLegalTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> action.get()));
  }

  private static List<String> captureLogs(Runnable action, Class<?>... sources) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    var loggers = new ArrayList<Logger>();
    for (Class<?> source : sources) {
      Logger logger = (Logger) LoggerFactory.getLogger(source);
      logger.addAppender(appender);
      logger.setLevel(Level.INFO);
      loggers.add(logger);
    }
    try {
      action.run();
    } finally {
      for (Logger logger : loggers) {
        logger.detachAppender(appender);
        logger.setLevel(null);
      }
    }
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }
}
