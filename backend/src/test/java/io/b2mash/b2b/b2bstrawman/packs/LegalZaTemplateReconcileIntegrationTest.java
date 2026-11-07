package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.template.TemplateSource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Hibernate test of the legal-za pack content reconcile (Phase 88 626D v8, ADR-356; 626E v9):
 * the startup path {@code PackInstallService.internalInstall} → {@code
 * TemplatePackInstaller.reconcile} (version gate → refresh pass → pristine snapshot → {@code
 * TemplatePackSeeder.resolveReconcileTarget} → content-hash re-pin) takes every rewritten template
 * from its v7 body (or, for the three templates re-edited in v9, its v8 body) to the shipped v9
 * body, keeps firm edits, never touches a clone, and is a no-op at equal version and on a second
 * run. Phase 89 v11: a v10 tenant's four client-facing bodies reach the letterhead bodies and the
 * four contract templates receive their {@code requiredContextFields} — only while their body is
 * the shipped body. Phase 89 v12: a v11 tenant receives the new {@code payment-receipt-za} template
 * on the bump (the installer's "create missing" — no reconcile entry). Phase 89 v13 (G89-F6, D11):
 * a v12 tenant's statement of account, client trust statement and trust receipt reach the v13
 * bodies — only while pristine or still at the v12 body.
 *
 * <p>A v7 (v8) tenant is simulated by writing each rewritten row's exact v7 (v8) body (test fixture
 * copied from git before the edit) with its install-time hash pinned (pristine), then rewinding
 * {@code pack_install.pack_version} by one. Each test re-runs the simulation, so tests are
 * order-independent; assertions target this tenant's rows by pack key only (no global counts).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalZaTemplateReconcileIntegrationTest {

  private static final String ORG_ID = "org_legal_za_template_reconcile_test";
  private static final String PACK_ID = "legal-za";
  private static final String CURRENT_VERSION = "13";
  private static final List<String> RECONCILED_KEYS =
      List.of(
          "engagement-letter-litigation",
          "engagement-letter-conveyancing",
          "engagement-letter-general",
          "fee-note-za",
          "statement-of-account",
          "matter-closure-letter",
          "deed-of-transfer",
          "bond-cancellation-instruction");

  /** Template bodies re-edited in v9 (626E), reconciled from their v8 body. */
  private static final List<String> V9_EDITED_KEYS =
      List.of("fee-note-za", "statement-of-account", "matter-closure-letter");

  /** Template bodies re-edited in v10 (G88-F12 contract parties), reconciled from their v9 body. */
  private static final List<String> V10_EDITED_KEYS =
      List.of(
          "offer-to-purchase",
          "deed-of-transfer",
          "bond-cancellation-instruction",
          "letter-of-demand");

  /** Client-facing bodies re-edited in v11 (Phase 89 G88-F16 letterhead), from their v10 body. */
  private static final List<String> V11_LETTERHEAD_KEYS =
      List.of(
          "statement-of-account",
          "matter-closure-letter",
          "client-trust-statement",
          "trust-receipt");

  /** Templates whose pack entry declares requiredContextFields in v11 (Phase 89 C1). */
  private static final Map<String, List<Map<String, String>>> V11_REQUIRED_FIELDS =
      Map.of(
          "offer-to-purchase",
          List.of(field("customFields.seller_name"), field("customFields.purchaser_name")),
          "deed-of-transfer",
          List.of(field("customFields.seller_name"), field("customFields.purchaser_name")),
          "bond-cancellation-instruction",
          List.of(field("customFields.seller_name")),
          "letter-of-demand",
          List.of(field("customFields.opposing_party")));

  /**
   * Bodies re-edited in v13 (G89-F6 Legal Practice Act wording on the trust receipt and client
   * trust statement, D11 trust ledger on the client trust statement, the SoA unbilled-WIP row),
   * from their v12 body.
   */
  private static final List<String> V13_EDITED_KEYS =
      List.of("statement-of-account", "client-trust-statement", "trust-receipt");

  /**
   * A legal-za template outside the reconcile block with no clause links: deleted to force a
   * create.
   */
  private static final String MISSING_KEY = "notice-of-motion";

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackInstallService packInstallService;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TemplatePackInstaller templatePackInstaller;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Legal ZA Template Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  /** (a) Pristine v7 rows reach the shipped bodies, hashes re-pinned, nothing reads as edited. */
  @Test
  void pristineV7Templates_reachTheShippedBodies_andStayPristine() {
    simulateV7Tenant(rows -> {}, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s hash re-pinned to the new content", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
    String reason = uninstallReason();
    assertThat(reason == null ? "" : reason)
        .as("reconciled rows must not read as edited")
        .doesNotContain("edited");
  }

  /** (b) A firm-edited body survives untouched and still reads as edited. */
  @Test
  void firmEditedBody_survives_andIsLoggedAtInfo() {
    Map<String, Object> edited = withExtraParagraph(v7Body("fee-note-za"), "Our own payment terms");
    var pinned = new ArrayList<String>();
    simulateV7Tenant(
        rows -> {
          var feeNote = rows.get("fee-note-za");
          pinned.add(feeNote.getContentHash()); // v7 hash stays → the edit reads as edited
          feeNote.updateContent(feeNote.getName(), feeNote.getDescription(), edited, null);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var feeNote = packRow("fee-note-za");
          assertThat(ContentHashUtil.hashOf(feeNote.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(edited));
          assertThat(feeNote.getContentHash()).isEqualTo(pinned.getFirst());
          var general = packRow("engagement-letter-general");
          assertThat(ContentHashUtil.hashOf(general.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-general")));
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=legal-za")
                    && m.contains("'fee-note-za'"));
    assertThat(uninstallReason()).contains("edited");
  }

  /**
   * (c) The refresh pass skips a row whose css was edited (stored hash ≠ content + css), but its
   * body is still byte-identical to v7 — the reconcile block applies the shipped body, keeps the
   * css and does not re-pin (the row was not pristine).
   */
  @Test
  void cssEditedRowWithV7Body_getsTheShippedBodyFromTheReconcileBlock_cssKept_notRepinned() {
    String css = "p { color: #333333; }";
    var pinned = new ArrayList<String>();
    simulateV7Tenant(
        rows -> {
          var row = rows.get("engagement-letter-conveyancing");
          pinned.add(row.getContentHash());
          row.updateContent(row.getName(), row.getDescription(), row.getContent(), css);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var row = packRow("engagement-letter-conveyancing");
          assertThat(ContentHashUtil.hashOf(row.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-conveyancing")));
          assertThat(row.getCss()).isEqualTo(css);
          assertThat(row.getContentHash()).isEqualTo(pinned.getFirst());
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile applied")
                    && m.contains("'engagement-letter-conveyancing'"));
  }

  /** (a') 626E: pristine v8 rows of the three v9-re-edited templates reach the v9 bodies. */
  @Test
  void pristineV8Rows_reachTheV9Bodies() {
    simulateV8Tenant(rows -> {}, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : V9_EDITED_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped v9 body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s hash re-pinned to the new content", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
    String reason = uninstallReason();
    assertThat(reason == null ? "" : reason)
        .as("reconciled rows must not read as edited")
        .doesNotContain("edited");
  }

  /**
   * (c') 626E: a css-edited SoA row still on the v8 body is skipped by the refresh pass; the v8 →
   * v9 reconcile entry applies the v9 body ({@code categoryLabel}), keeps the css and does not
   * re-pin.
   */
  @Test
  void cssEditedV8SoaRow_getsTheV9BodyFromTheReconcileBlock_cssKept() {
    String css = "td { padding: 2pt; }";
    var pinned = new ArrayList<String>();
    simulateV8Tenant(
        rows -> {
          var row = rows.get("statement-of-account");
          pinned.add(row.getContentHash());
          row.updateContent(row.getName(), row.getDescription(), row.getContent(), css);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var row = packRow("statement-of-account");
          assertThat(ContentHashUtil.hashOf(row.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("statement-of-account")));
          assertThat(row.getCss()).isEqualTo(css);
          assertThat(row.getContentHash()).isEqualTo(pinned.getFirst());
          return null;
        });
    assertThat(logs)
        .anyMatch(m -> m.startsWith("Reconcile applied") && m.contains("'statement-of-account'"));
  }

  /**
   * (d) A clone shares {@code packId} + {@code packTemplateKey} with its source. Reconcile must not
   * throw on it (it used to: a single-result finder over both rows) and must leave it
   * byte-identical while the pack row is reconciled.
   */
  @Test
  void clone_survivesByteIdentical_andReconcileDoesNotThrow() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId = inTenantTx(() -> saveCloneOf("fee-note-za", "-custom-626d"));
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(clone.getSource()).isEqualTo(TemplateSource.ORG_CUSTOM);
            assertThat(ContentHashUtil.hashOf(clone.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            assertThat(ContentHashUtil.hashOf(packRow("fee-note-za").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("fee-note-za")));
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /**
   * 626D review F-3: a version bump that re-creates a missing pack template tags the new row with
   * the install — and never sweeps in a clone (clones copy {@code packId} and leave {@code
   * source_pack_install_id} null). The clone stays untagged with its hash unchanged and is not
   * counted as an item of the install.
   */
  @Test
  void versionBumpCreatingAMissingTemplate_neverTagsAClone() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId = inTenantTx(() -> saveCloneOf("fee-note-za", "-custom-626d-f3"));
    String cloneHashBefore =
        inTenantTx(
            () -> documentTemplateRepository.findById(cloneId).orElseThrow().getContentHash());
    inTenantTx(
        () -> {
          documentTemplateRepository.delete(packRow(MISSING_KEY));
          return null;
        });
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            UUID installId = packInstallRepository.findByPackId(PACK_ID).orElseThrow().getId();
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(clone.getSourcePackInstallId()).as("clone stays untagged").isNull();
            assertThat(clone.getContentHash()).isEqualTo(cloneHashBefore);
            var recreated = packRow(MISSING_KEY);
            assertThat(recreated.getSourcePackInstallId()).isEqualTo(installId);
            assertThat(documentTemplateRepository.findBySourcePackInstallId(installId))
                .extracting(DocumentTemplate::getId)
                .contains(recreated.getId())
                .doesNotContain(cloneId);
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /**
   * 626D review F-5: two pack-seeded rows for one key (no unique constraint backs the pair) never
   * throw at boot — the installer's refresh pass and the reconcile resolver each WARN and skip that
   * template, and the rest of the pack is still reconciled.
   */
  @Test
  void twoPackSeededRowsForOneKey_warnAndSkip_onBothPaths_neverThrow() {
    simulateV7Tenant(rows -> {}, true);
    UUID originalId = inTenantTx(() -> packRow("fee-note-za").getId());
    UUID duplicateId =
        inTenantTx(
            () -> {
              var original = packRow("fee-note-za");
              var dup =
                  new DocumentTemplate(
                      original.getPrimaryEntityType(),
                      original.getName() + " (duplicate)",
                      original.getSlug() + "-dup-626d",
                      original.getCategory(),
                      original.getContent());
              dup.setSource(TemplateSource.PLATFORM);
              dup.setPackId(original.getPackId());
              dup.setPackTemplateKey(original.getPackTemplateKey());
              dup.setSourcePackInstallId(original.getSourcePackInstallId());
              dup.setContentHash(original.getContentHash());
              return documentTemplateRepository.save(dup).getId();
            });
    try {
      var logs =
          captureLogs(
              () -> packInstallService.internalInstall(PACK_ID, schema),
              TemplatePackInstaller.class,
              TemplatePackSeeder.class);

      inTenantTx(
          () -> {
            for (UUID id : List.of(originalId, duplicateId)) {
              assertThat(
                      ContentHashUtil.hashOf(
                          documentTemplateRepository.findById(id).orElseThrow().getContent()))
                  .as("ambiguous fee-note row %s left at v7", id)
                  .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            }
            assertThat(ContentHashUtil.hashOf(packRow("engagement-letter-general").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("engagement-letter-general")));
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
      assertThat(logs)
          .anyMatch(m -> m.contains("'fee-note-za'") && m.contains("2 pack-seeded rows"))
          .anyMatch(m -> m.startsWith("Reconcile entry ignored") && m.contains("ambiguous"));
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(duplicateId);
            return null;
          });
    }
  }

  /**
   * The resolver's ownership predicate lives in JPQL ({@code findPackSeededByInstall}): a clone is
   * never a target even in the worst case of carrying this install's tag — it survives
   * byte-identical while the pack row is reconciled.
   */
  @Test
  void cloneTaggedWithTheInstall_isNeverATarget() {
    simulateV7Tenant(rows -> {}, true);
    UUID cloneId =
        inTenantTx(
            () -> {
              UUID id = saveCloneOf("fee-note-za", "-custom-626d-tagged");
              var clone = documentTemplateRepository.findById(id).orElseThrow();
              clone.setSourcePackInstallId(packRow("fee-note-za").getSourcePackInstallId());
              documentTemplateRepository.save(clone);
              return id;
            });
    try {
      inTenantTx(
          () -> {
            var pack = packRow("fee-note-za");
            assertThat(
                    documentTemplateRepository.findPackSeededByInstall(
                        pack.getSourcePackInstallId(), "fee-note-za"))
                .extracting(DocumentTemplate::getId)
                .containsExactly(pack.getId());
            return null;
          });

      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            var clone = documentTemplateRepository.findById(cloneId).orElseThrow();
            assertThat(ContentHashUtil.hashOf(clone.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(v7Body("fee-note-za")));
            assertThat(ContentHashUtil.hashOf(packRow("fee-note-za").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("fee-note-za")));
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            documentTemplateRepository.deleteById(cloneId);
            return null;
          });
    }
  }

  /**
   * (g) G88-F12: a v9 tenant's pristine Offer to Purchase, Deed of Transfer, Bond Cancellation
   * Instruction and Letter of Demand reach the party-bound v10 bodies (still pristine), and the
   * Letter of Demand moves from the client to the matter so its opposing-party field resolves.
   */
  @Test
  void pristineV9Tenant_partyBoundBodies_letterOfDemandBecomesAMatterTemplate() {
    inTenantTx(
        () -> {
          for (String key : V10_EDITED_KEYS) {
            var row = packRow(key);
            Map<String, Object> body = v9Body(key);
            row.updateContent(row.getName(), row.getDescription(), body, row.getCss());
            row.setContentHash(templatePackInstaller.computeContentHash(body, row.getCss()));
            if ("letter-of-demand".equals(key)) {
              row.changePrimaryEntityType(TemplateEntityType.CUSTOMER);
            }
            documentTemplateRepository.save(row);
          }
          assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, CURRENT_VERSION, "9"))
              .isEqualTo(1);
          return null;
        });

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : V10_EDITED_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped v10 body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s stays pristine", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          assertThat(packRow("letter-of-demand").getPrimaryEntityType())
              .isEqualTo(TemplateEntityType.PROJECT);
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /**
   * (h) Phase 89 v11: a pristine v10 tenant's four client-facing rows reach the letterhead bodies
   * (still pristine), and the four contract rows — which never carried a declaration — get the
   * shipped {@code requiredContextFields}.
   */
  @Test
  void pristineV10Tenant_letterheadBodies_andRequiredContextFieldsApplied() {
    simulateV10Tenant(rows -> {}, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : V11_LETTERHEAD_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped v11 body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s stays pristine", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          V11_REQUIRED_FIELDS.forEach(
              (key, expected) ->
                  assertThat(packRow(key).getRequiredContextFields())
                      .as("%s requiredContextFields", key)
                      .isEqualTo(expected));
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
    String reason = uninstallReason();
    assertThat(reason == null ? "" : reason)
        .as("reconciled rows must not read as edited")
        .doesNotContain("edited");
  }

  /** (i) A firm-edited v10 statement of account keeps its body and still reads as edited. */
  @Test
  void firmEditedV10StatementOfAccount_survives_andIsLoggedAtInfo() {
    Map<String, Object> edited =
        withExtraParagraph(v10Body("statement-of-account"), "Our own statement wording");
    var pinned = new ArrayList<String>();
    simulateV10Tenant(
        rows -> {
          var soa = rows.get("statement-of-account");
          pinned.add(soa.getContentHash()); // v10 hash stays → the edit reads as edited
          soa.updateContent(soa.getName(), soa.getDescription(), edited, null);
        },
        true);

    var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

    inTenantTx(
        () -> {
          var soa = packRow("statement-of-account");
          assertThat(ContentHashUtil.hashOf(soa.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(edited));
          assertThat(soa.getContentHash()).isEqualTo(pinned.getFirst());
          var receipt = packRow("trust-receipt");
          assertThat(ContentHashUtil.hashOf(receipt.getContent()))
              .isEqualTo(ContentHashUtil.hashOf(shippedBody("trust-receipt")));
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=legal-za")
                    && m.contains("'statement-of-account' field content"));
    assertThat(uninstallReason()).contains("edited");
  }

  /**
   * (j) {@code requiredContextFields} moves only with the shipped body: a firm-edited Offer to
   * Purchase keeps no declaration (LEFT_ALONE, logged), while the pristine Deed of Transfer gets
   * its list.
   */
  @Test
  void firmEditedOfferToPurchase_requiredContextFieldsLeftAlone_pristineDeedGetsItsList() {
    simulateV10Tenant(
        rows -> {
          var otp = rows.get("offer-to-purchase");
          otp.updateContent(
              otp.getName(),
              otp.getDescription(),
              withExtraParagraph(shippedBody("offer-to-purchase"), "Our own special conditions"),
              otp.getCss());
        },
        true);
    try {
      var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

      inTenantTx(
          () -> {
            assertThat(packRow("offer-to-purchase").getRequiredContextFields()).isNull();
            assertThat(packRow("deed-of-transfer").getRequiredContextFields())
                .isEqualTo(V11_REQUIRED_FIELDS.get("deed-of-transfer"));
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
      assertThat(logs)
          .anyMatch(
              m ->
                  m.startsWith("Reconcile skipped (edited)")
                      && m.contains("'offer-to-purchase' field requiredContextFields"))
          .noneMatch(
              m ->
                  m.startsWith("Reconcile applied")
                      && m.contains("'offer-to-purchase' field requiredContextFields"));
    } finally {
      restorePristineShippedBody("offer-to-purchase"); // not covered by simulateTenant
    }
  }

  /**
   * (l) A css-edited v10 client trust statement is skipped by the refresh pass; its new v10 → v11
   * {@code content} entry applies the letterhead body, keeps the css and does not re-pin.
   */
  @Test
  void cssEditedV10ClientTrustStatement_getsTheV11BodyFromTheReconcileBlock_cssKept() {
    String css = "h1 { letter-spacing: 1pt; }";
    var pinned = new ArrayList<String>();
    simulateV10Tenant(
        rows -> {
          var row = rows.get("client-trust-statement");
          pinned.add(row.getContentHash());
          row.updateContent(row.getName(), row.getDescription(), row.getContent(), css);
        },
        true);
    try {
      var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

      inTenantTx(
          () -> {
            var row = packRow("client-trust-statement");
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("client-trust-statement")));
            assertThat(row.getCss()).isEqualTo(css);
            assertThat(row.getContentHash()).isEqualTo(pinned.getFirst());
            return null;
          });
      assertThat(logs)
          .anyMatch(
              m ->
                  m.startsWith("Reconcile applied")
                      && m.contains("'client-trust-statement' field content"));
    } finally {
      restorePristineShippedBody("client-trust-statement"); // not covered by simulateTenant
    }
  }

  /** (k) Equal version at v11: the gate stays closed — v10 bodies and null declarations stay. */
  @Test
  void equalVersionV11_isANoOp_forTheLetterheadBodiesAndDeclarations() {
    simulateV10Tenant(rows -> {}, false);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : V11_LETTERHEAD_KEYS) {
            assertThat(ContentHashUtil.hashOf(packRow(key).getContent()))
                .as("%s untouched at equal version", key)
                .isEqualTo(ContentHashUtil.hashOf(v10Body(key)));
          }
          for (String key : V11_REQUIRED_FIELDS.keySet()) {
            assertThat(packRow(key).getRequiredContextFields())
                .as("%s untouched at equal version", key)
                .isNull();
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /**
   * Phase 89 632B (v11 → v12, ADR-364): an existing v11 tenant without {@code payment-receipt-za}
   * gets it on the bump — a PLATFORM row tagged with the install, pristine (stored hash = the
   * shipped body's), INVOICE / OTHER at sortOrder 18; a second run at equal version creates no
   * duplicate.
   */
  @Test
  void v11Tenant_receivesThePaymentReceiptTemplateOnTheBump_equalVersionNoDuplicate() {
    inTenantTx(
        () -> {
          documentTemplateRepository.delete(packRow("payment-receipt-za"));
          assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, CURRENT_VERSION, "11"))
              .as("rewind %s %s -> 11", PACK_ID, CURRENT_VERSION)
              .isEqualTo(1);
          return null;
        });
    assertThat(
            inTenantTx(
                () ->
                    documentTemplateRepository.findByPackTemplateKey("payment-receipt-za").stream()
                        .filter(dt -> PACK_ID.equals(dt.getPackId()))
                        .toList()))
        .as("simulated v11 tenant has no receipt template")
        .isEmpty();

    packInstallService.internalInstall(PACK_ID, schema);

    UUID createdId =
        inTenantTx(
            () -> {
              UUID installId = packInstallRepository.findByPackId(PACK_ID).orElseThrow().getId();
              var created = packRow("payment-receipt-za");
              assertThat(created.getSource()).isEqualTo(TemplateSource.PLATFORM);
              assertThat(created.getSourceTemplateId()).isNull();
              assertThat(created.getSourcePackInstallId()).isEqualTo(installId);
              assertThat(created.getPrimaryEntityType()).isEqualTo(TemplateEntityType.INVOICE);
              assertThat(created.getCategory()).isEqualTo(TemplateCategory.OTHER);
              assertThat(created.getSortOrder()).isEqualTo(18);
              assertThat(created.isActive()).isTrue();
              assertThat(ContentHashUtil.hashOf(created.getContent()))
                  .isEqualTo(ContentHashUtil.hashOf(shippedBody("payment-receipt-za")));
              assertThat(created.getContentHash())
                  .as("pristine: stored hash = install hash of the shipped body")
                  .isEqualTo(
                      templatePackInstaller.computeContentHash(
                          created.getContent(), created.getCss()));
              assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
              return created.getId();
            });

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          assertThat(packRow("payment-receipt-za").getId()).isEqualTo(createdId); // one row
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /**
   * (m) Phase 89 v13: a pristine v12 tenant's three re-edited rows reach the v13 bodies (Legal
   * Practice Act wording, the client trust statement's ledger, the SoA unbilled-WIP row) and stay
   * pristine.
   */
  @Test
  void pristineV12Tenant_reachesTheV13Bodies_andStaysPristine() {
    simulateV12Tenant(rows -> {}, true);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : V13_EDITED_KEYS) {
            var row = packRow(key);
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .as("%s carries the shipped v13 body", key)
                .isEqualTo(ContentHashUtil.hashOf(shippedBody(key)));
            assertThat(row.getContentHash())
                .as("%s stays pristine", key)
                .isEqualTo(
                    templatePackInstaller.computeContentHash(row.getContent(), row.getCss()));
          }
          assertThat(packRow("trust-receipt").getContent().toString())
              .contains("section 86 of the Legal Practice Act 28 of 2014")
              .doesNotContain("78(1)");
          assertThat(packRow("client-trust-statement").getContent().toString())
              .contains("trust.lines")
              .doesNotContain("to be inserted");
          assertThat(packRow("statement-of-account").getContent().toString())
              .contains("summary.unbilled_wip");
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /** (n) A firm-edited v12 client trust statement keeps its body; the trust receipt still moves. */
  @Test
  void firmEditedV12ClientTrustStatement_survives_andIsLoggedAtInfo() {
    Map<String, Object> edited =
        withExtraParagraph(v12Body("client-trust-statement"), "Our own trust wording");
    simulateV12Tenant(
        rows -> {
          var cts = rows.get("client-trust-statement");
          cts.updateContent(cts.getName(), cts.getDescription(), edited, null);
        },
        true);
    try {
      var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

      inTenantTx(
          () -> {
            assertThat(ContentHashUtil.hashOf(packRow("client-trust-statement").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(edited));
            assertThat(ContentHashUtil.hashOf(packRow("trust-receipt").getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("trust-receipt")));
            return null;
          });
      assertThat(logs)
          .anyMatch(
              m ->
                  m.startsWith("Reconcile skipped (edited)")
                      && m.contains("'client-trust-statement' field content"));
    } finally {
      restorePristineShippedBody("client-trust-statement");
    }
  }

  /**
   * (o) A css-edited v12 trust receipt is skipped by the refresh pass; the new v12 → v13 {@code
   * content} entry applies the body and keeps the css.
   */
  @Test
  void cssEditedV12TrustReceipt_getsTheV13BodyFromTheReconcileBlock_cssKept() {
    String css = "p { line-height: 1.4; }";
    simulateV12Tenant(
        rows -> {
          var row = rows.get("trust-receipt");
          row.updateContent(row.getName(), row.getDescription(), row.getContent(), css);
        },
        true);
    try {
      var logs = captureSeederLogs(() -> packInstallService.internalInstall(PACK_ID, schema));

      inTenantTx(
          () -> {
            var row = packRow("trust-receipt");
            assertThat(ContentHashUtil.hashOf(row.getContent()))
                .isEqualTo(ContentHashUtil.hashOf(shippedBody("trust-receipt")));
            assertThat(row.getCss()).isEqualTo(css);
            return null;
          });
      assertThat(logs)
          .anyMatch(
              m ->
                  m.startsWith("Reconcile applied") && m.contains("'trust-receipt' field content"));
    } finally {
      restorePristineShippedBody("trust-receipt");
    }
  }

  /** (p) Equal version at v13: the gate stays closed — the v12 bodies stay. */
  @Test
  void equalVersionV13_isANoOp_forTheV12Bodies() {
    simulateV12Tenant(rows -> {}, false);
    try {
      packInstallService.internalInstall(PACK_ID, schema);

      inTenantTx(
          () -> {
            for (String key : V13_EDITED_KEYS) {
              assertThat(ContentHashUtil.hashOf(packRow(key).getContent()))
                  .as("%s untouched at equal version", key)
                  .isEqualTo(ContentHashUtil.hashOf(v12Body(key)));
            }
            assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
            return null;
          });
    } finally {
      V13_EDITED_KEYS.forEach(this::restorePristineShippedBody);
    }
  }

  /** (e) Equal version: the gate stays closed and nothing is written. */
  @Test
  void equalVersion_isANoOp() {
    simulateV7Tenant(rows -> {}, false);

    packInstallService.internalInstall(PACK_ID, schema);

    inTenantTx(
        () -> {
          for (String key : RECONCILED_KEYS) {
            assertThat(ContentHashUtil.hashOf(packRow(key).getContent()))
                .as("%s untouched at equal version", key)
                .isEqualTo(ContentHashUtil.hashOf(v7Body(key)));
          }
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  /** (f) A second run after the reconcile changes nothing. */
  @Test
  void secondRun_changesNothing() {
    simulateV7Tenant(rows -> {}, true);
    packInstallService.internalInstall(PACK_ID, schema);
    Map<String, Snapshot> first = snapshot();

    packInstallService.internalInstall(PACK_ID, schema);

    assertThat(snapshot()).isEqualTo(first);
    inTenantTx(
        () -> {
          assertThat(installedVersion()).isEqualTo(CURRENT_VERSION);
          return null;
        });
  }

  // --- helpers ---

  private record Snapshot(String bodyHash, String contentHash, Instant updatedAt) {}

  private Map<String, Snapshot> snapshot() {
    return inTenantTx(
        () -> {
          var out = new LinkedHashMap<String, Snapshot>();
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            out.put(
                key,
                new Snapshot(
                    ContentHashUtil.hashOf(row.getContent()),
                    row.getContentHash(),
                    row.getUpdatedAt()));
          }
          return out;
        });
  }

  /**
   * Writes the v7 body into every rewritten pack row with its install-time hash pinned (pristine),
   * applies {@code firmEdits}, then (optionally) rewinds the recorded install version by one.
   */
  private void simulateV7Tenant(Consumer<Map<String, DocumentTemplate>> firmEdits, boolean rewind) {
    simulateTenant(LegalZaTemplateReconcileIntegrationTest::v7Body, firmEdits, rewind);
  }

  /**
   * A v8 tenant: the three v9-re-edited rows carry their v8 body, every other rewritten row its
   * shipped body (unchanged since v8); hashes pinned, then (optionally) rewound by one version.
   */
  private void simulateV8Tenant(Consumer<Map<String, DocumentTemplate>> firmEdits, boolean rewind) {
    simulateTenant(
        key ->
            V9_EDITED_KEYS.contains(key)
                ? v8Body(key)
                : V10_EDITED_KEYS.contains(key) ? v9Body(key) : shippedBody(key),
        firmEdits,
        rewind);
  }

  /**
   * A v10 tenant: the four letterhead rows carry their v10 body (pristine, fixture copied before
   * the v11 edit), the four contract rows their shipped body (unchanged since v10, pristine) and no
   * {@code requiredContextFields} (no pack declared them before v11), every other rewritten row its
   * shipped body (pristine — undoes sibling tests' edits); {@code firmEdits} applied, then
   * (optionally) rewound 11 → 10.
   */
  private void simulateV10Tenant(
      Consumer<Map<String, DocumentTemplate>> firmEdits, boolean rewind) {
    inTenantTx(
        () -> {
          var rows = new LinkedHashMap<String, DocumentTemplate>();
          for (String key : V11_LETTERHEAD_KEYS) {
            rows.put(key, packRowWithPinnedBody(key, v10Body(key)));
          }
          for (String key : V11_REQUIRED_FIELDS.keySet()) {
            var row = packRowWithPinnedBody(key, shippedBody(key));
            row.setRequiredContextFields(null);
            rows.put(key, row);
          }
          for (String key : RECONCILED_KEYS) {
            rows.computeIfAbsent(key, k -> packRowWithPinnedBody(k, shippedBody(k)));
          }
          firmEdits.accept(rows);
          documentTemplateRepository.saveAll(rows.values());
          if (rewind) {
            assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, CURRENT_VERSION, "10"))
                .as("rewind %s %s -> 10", PACK_ID, CURRENT_VERSION)
                .isEqualTo(1);
          }
          return null;
        });
  }

  /**
   * A v12 tenant: the three v13-re-edited rows carry their v12 body (pristine, fixture copied
   * before the v13 edit), every other rewritten row its shipped body (pristine — undoes sibling
   * tests' edits); {@code firmEdits} applied, then (optionally) rewound 13 → 12.
   */
  private void simulateV12Tenant(
      Consumer<Map<String, DocumentTemplate>> firmEdits, boolean rewind) {
    inTenantTx(
        () -> {
          var rows = new LinkedHashMap<String, DocumentTemplate>();
          for (String key : V13_EDITED_KEYS) {
            rows.put(key, packRowWithPinnedBody(key, v12Body(key)));
          }
          for (String key : RECONCILED_KEYS) {
            rows.computeIfAbsent(key, k -> packRowWithPinnedBody(k, shippedBody(k)));
          }
          firmEdits.accept(rows);
          documentTemplateRepository.saveAll(rows.values());
          if (rewind) {
            assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, CURRENT_VERSION, "12"))
                .as("rewind %s %s -> 12", PACK_ID, CURRENT_VERSION)
                .isEqualTo(1);
          }
          return null;
        });
  }

  private DocumentTemplate packRowWithPinnedBody(String key, Map<String, Object> body) {
    var row = packRow(key);
    row.updateContent(row.getName(), row.getDescription(), body, null);
    row.setContentHash(templatePackInstaller.computeContentHash(body, null));
    return row;
  }

  /** Puts a row back on its shipped body + null css with the install hash pinned (pristine). */
  private void restorePristineShippedBody(String key) {
    inTenantTx(() -> documentTemplateRepository.save(packRowWithPinnedBody(key, shippedBody(key))));
  }

  private static Map<String, String> field(String dottedField) {
    return Map.of("entity", "project", "field", dottedField);
  }

  private void simulateTenant(
      java.util.function.Function<String, Map<String, Object>> bodyFor,
      Consumer<Map<String, DocumentTemplate>> firmEdits,
      boolean rewind) {
    inTenantTx(
        () -> {
          var rows = new LinkedHashMap<String, DocumentTemplate>();
          for (String key : RECONCILED_KEYS) {
            var row = packRow(key);
            Map<String, Object> body = bodyFor.apply(key);
            row.updateContent(row.getName(), row.getDescription(), body, null);
            row.setContentHash(templatePackInstaller.computeContentHash(body, null));
            rows.put(key, row);
          }
          firmEdits.accept(rows);
          documentTemplateRepository.saveAll(rows.values());

          if (rewind) {
            String current = installedVersion();
            String previous = String.valueOf(Integer.parseInt(current) - 1);
            assertThat(packInstallRepository.compareAndSetVersion(PACK_ID, current, previous))
                .as("rewind %s %s -> %s", PACK_ID, current, previous)
                .isEqualTo(1);
          }
          return null;
        });
  }

  /** The pack-seeded (PLATFORM, non-clone) row for a key — clones share packId + key. */
  private DocumentTemplate packRow(String key) {
    var rows =
        documentTemplateRepository.findByPackTemplateKey(key).stream()
            .filter(dt -> PACK_ID.equals(dt.getPackId()))
            .filter(dt -> dt.getSource() == TemplateSource.PLATFORM)
            .filter(dt -> dt.getSourceTemplateId() == null)
            .toList();
    assertThat(rows).as("one pack row for %s", key).hasSize(1);
    return rows.getFirst();
  }

  private String installedVersion() {
    return packInstallRepository.findByPackId(PACK_ID).orElseThrow().getPackVersion();
  }

  private String uninstallReason() {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> templatePackInstaller.checkUninstallable(PACK_ID, schema).blockingReason());
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static Map<String, Object> v7Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v7/" + key + ".json");
  }

  private static Map<String, Object> v8Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v8/" + key + ".json");
  }

  private static Map<String, Object> v9Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v9/" + key + ".json");
  }

  private static Map<String, Object> v10Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v10/" + key + ".json");
  }

  private static Map<String, Object> v12Body(String key) {
    return readBody("fixtures/template-packs/legal-za-v12/" + key + ".json");
  }

  private static Map<String, Object> shippedBody(String key) {
    return readBody("template-packs/legal-za/" + key + ".json");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readBody(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JSON.readValue(in, Map.class);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> withExtraParagraph(Map<String, Object> body, String text) {
    var copy = new LinkedHashMap<>(body);
    var content = new ArrayList<>((List<Object>) body.get("content"));
    content.add(
        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))));
    copy.put("content", content);
    return copy;
  }

  private static List<String> captureSeederLogs(Runnable action) {
    return captureLogs(action, TemplatePackSeeder.class);
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

  /** Mirrors {@code DocumentTemplateService.cloneTemplate}: ORG_CUSTOM, same pack id + key. */
  private UUID saveCloneOf(String key, String slugSuffix) {
    var original = packRow(key);
    var clone =
        new DocumentTemplate(
            original.getPrimaryEntityType(),
            original.getName() + " (Custom)",
            original.getSlug() + slugSuffix,
            original.getCategory(),
            original.getContent());
    clone.setDescription(original.getDescription());
    clone.setSourceTemplateId(original.getId());
    clone.setPackId(original.getPackId());
    clone.setPackTemplateKey(original.getPackTemplateKey());
    return documentTemplateRepository.save(clone).getId();
  }
}
