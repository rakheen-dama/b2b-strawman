package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Hibernate test of the ADR-356 field-pack resolver (628C, U21 terminology): {@code
 * field_group} / {@code field_definition} {@code name} / {@code description} entries reach an
 * existing tenant through the startup path {@code FieldPackSeeder.seedPacksForTenant} → version
 * gate ({@code backfillAndTryOpen}) → {@code FieldPackSeeder.resolveReconcileTarget}, and the one
 * rule keeps every firm edit.
 *
 * <p>An older tenant is simulated by writing the previously shipped strings back onto the
 * pack-seeded rows and rewinding {@code pack_install.pack_version} by one. Each test re-runs the
 * simulation, so tests are order-independent; assertions target this tenant's pack-seeded rows.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldPackSeederReconcileTest {

  private static final String ORG_ID = "org_field_pack_reconcile_test";

  static final String COMMON_PACK = "common-project";
  static final String V2_GROUP_NAME = "Project Info";
  static final String V2_GROUP_DESC = "Common project metadata fields";
  static final String V2_CATEGORY_DESC = "Project category or type";
  static final String V3_GROUP_NAME = "Matter Info";
  static final String V3_GROUP_DESC = "Common matter metadata fields";
  static final String V3_CATEGORY_DESC = "Matter category or type";

  static final String LEGAL_V2_DESC =
      "South African legal-specific fields for matter (project) entities";
  static final String LEGAL_V3_DESC = "South African legal-specific fields for matters";
  static final String CONVEYANCING_V1_DESC =
      "South African conveyancing-specific fields for property transfer matters (projects with"
          + " matter_type = CONVEYANCING)";
  static final String CONVEYANCING_V2_DESC =
      "South African conveyancing-specific fields for property transfer matters (matter type"
          + " Conveyancing)";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Field Pack Reconcile Test Org", "legal-za");
    schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void pristineV2CommonProject_isReconciledToV3() {
    simulateCommonV2(V2_GROUP_NAME, true);

    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          var group = group("project_info");
          assertThat(group.getName()).isEqualTo(V3_GROUP_NAME);
          assertThat(group.getDescription()).isEqualTo(V3_GROUP_DESC);
          assertThat(definition("category").getDescription()).isEqualTo(V3_CATEGORY_DESC);
          assertThat(ledgerVersion(COMMON_PACK)).isEqualTo("3");
          return null;
        });
  }

  @Test
  void firmRenamedGroup_keepsName_descriptionStillReconciled() {
    simulateCommonV2("Our matter fields", true);

    List<String> logs = captureLogs(() -> fieldPackSeeder.seedPacksForTenant(schema, ORG_ID));

    inTenantTx(
        () -> {
          var group = group("project_info");
          assertThat(group.getName()).isEqualTo("Our matter fields");
          assertThat(group.getDescription()).isEqualTo(V3_GROUP_DESC);
          assertThat(definition("category").getDescription()).isEqualTo(V3_CATEGORY_DESC);
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=common-project")
                    && m.contains("field_group 'project_info' field name"));
  }

  @Test
  void equalVersion_isANoOp() {
    simulateCommonV2(V2_GROUP_NAME, false);

    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          var group = group("project_info");
          assertThat(group.getName()).isEqualTo(V2_GROUP_NAME);
          assertThat(group.getDescription()).isEqualTo(V2_GROUP_DESC);
          assertThat(definition("category").getDescription()).isEqualTo(V2_CATEGORY_DESC);
          return null;
        });
    // leave the rows at the shipped values for sibling tests
    simulateCommonV2(V2_GROUP_NAME, true);
    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);
  }

  @Test
  void legalZaAndConveyancingGroupDescriptions_reconciled() {
    inTenantTx(
        () -> {
          var legal = group("legal_za_matter");
          legal.updateMetadata(legal.getName(), LEGAL_V2_DESC, legal.getSortOrder());
          var conveyancing = group("conveyancing_za_matter");
          conveyancing.updateMetadata(
              conveyancing.getName(), CONVEYANCING_V1_DESC, conveyancing.getSortOrder());
          fieldGroupRepository.saveAll(List.of(legal, conveyancing));
          rewind("legal-za-project", "3", "2");
          rewind("conveyancing-za-project", "2", "1");
          return null;
        });

    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

    inTenantTx(
        () -> {
          assertThat(group("legal_za_matter").getDescription()).isEqualTo(LEGAL_V3_DESC);
          assertThat(group("legal_za_matter").getName()).isEqualTo("SA Legal — Matter Details");
          assertThat(group("conveyancing_za_matter").getDescription())
              .isEqualTo(CONVEYANCING_V2_DESC);
          assertThat(ledgerVersion("legal-za-project")).isEqualTo("3");
          assertThat(ledgerVersion("conveyancing-za-project")).isEqualTo("2");
          return null;
        });
  }

  /**
   * R-18 LEFT_ALONE for {@code field_definition}: a firm-edited {@code category} description is
   * kept while the group's entries in the same block still apply.
   */
  @Test
  void firmEditedDefinitionDescription_isLeftAlone_groupEntriesStillApply() {
    simulateCommonV2(V2_GROUP_NAME, true);
    inTenantTx(
        () -> {
          var category = definition("category");
          category.updateMetadata(
              category.getName(),
              "Our firm's category notes",
              category.isRequired(),
              category.getValidation());
          return fieldDefinitionRepository.save(category);
        });

    List<String> logs = captureLogs(() -> fieldPackSeeder.seedPacksForTenant(schema, ORG_ID));

    inTenantTx(
        () -> {
          assertThat(definition("category").getDescription())
              .isEqualTo("Our firm's category notes");
          var group = group("project_info");
          assertThat(group.getName()).isEqualTo(V3_GROUP_NAME);
          assertThat(group.getDescription()).isEqualTo(V3_GROUP_DESC);
          assertThat(ledgerVersion(COMMON_PACK)).isEqualTo("3");
          return null;
        });
    assertThat(logs)
        .anyMatch(
            m ->
                m.startsWith("Reconcile skipped (edited)")
                    && m.contains("pack=common-project")
                    && m.contains("field_definition 'category' field description"));
  }

  /**
   * R-18 ownership by {@code pack_id}: rows that own the pack's slugs ({@code project_info} group,
   * {@code category} definition) but carry no {@code pack_id} — a firm re-created row — are never
   * written, even though their values equal every {@code from}. Removing the {@code pack_id} filter
   * in {@code FieldPackSeeder.groupTarget} / {@code definitionTarget} turns this red.
   */
  @Test
  void slugOwningRowsWithoutPackId_areNeverWritten() {
    simulateCommonV2(V2_GROUP_NAME, true);
    setPackOwnership(null, "category");
    try {
      List<String> logs = captureLogs(() -> fieldPackSeeder.seedPacksForTenant(schema, ORG_ID));

      inTenantTx(
          () -> {
            var group = group("project_info");
            assertThat(group.getName()).isEqualTo(V2_GROUP_NAME);
            assertThat(group.getDescription()).isEqualTo(V2_GROUP_DESC);
            assertThat(definition("category").getDescription()).isEqualTo(V2_CATEGORY_DESC);
            assertThat(ledgerVersion(COMMON_PACK)).isEqualTo("3");
            return null;
          });
      assertThat(logs)
          .filteredOn(
              m ->
                  m.startsWith("Reconcile skipped (no pack-seeded row)")
                      && m.contains("pack=common-project"))
          .hasSize(3);
    } finally {
      setPackOwnership(COMMON_PACK, "category");
    }
  }

  /**
   * R-18 ownership by {@code pack_field_key}: a {@code category} definition carrying this pack's
   * {@code pack_id} but a different {@code pack_field_key} is skipped. Removing the {@code
   * pack_field_key} check in {@code FieldPackSeeder.definitionTarget} turns this red.
   */
  @Test
  void definitionWithAnotherPackFieldKey_isSkipped() {
    simulateCommonV2(V2_GROUP_NAME, true);
    setPackOwnership(COMMON_PACK, "other_field_key");
    try {
      fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

      inTenantTx(
          () -> {
            assertThat(definition("category").getDescription()).isEqualTo(V2_CATEGORY_DESC);
            assertThat(group("project_info").getName()).isEqualTo(V3_GROUP_NAME);
            return null;
          });
    } finally {
      setPackOwnership(COMMON_PACK, "category");
    }
  }

  /**
   * A firm-created group under a different slug that carries the old pack strings is never touched:
   * resolution is by the pack's own slug, so other slugs are never looked up. (The {@code pack_id}
   * filter is proven by {@link #slugOwningRowsWithoutPackId_areNeverWritten()}.)
   */
  @Test
  void firmCreatedGroupUnderAnotherSlug_withTheOldName_isNeverTouched() {
    simulateCommonV2(V2_GROUP_NAME, true);
    var firmGroupId =
        inTenantTx(
            () -> {
              var firm = new FieldGroup(EntityType.PROJECT, V2_GROUP_NAME, "firm_project_info");
              firm.setDescription(V2_GROUP_DESC);
              return fieldGroupRepository.save(firm).getId();
            });
    try {
      fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

      inTenantTx(
          () -> {
            var firm = fieldGroupRepository.findById(firmGroupId).orElseThrow();
            assertThat(firm.getName()).isEqualTo(V2_GROUP_NAME);
            assertThat(firm.getDescription()).isEqualTo(V2_GROUP_DESC);
            assertThat(group("project_info").getName()).isEqualTo(V3_GROUP_NAME);
            return null;
          });
    } finally {
      inTenantTx(
          () -> {
            fieldGroupRepository.deleteById(firmGroupId);
            return null;
          });
    }
  }

  @Test
  void secondRunAtNewVersion_changesNothing() {
    simulateCommonV2(V2_GROUP_NAME, true);
    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);
    Supplier<Instant> groupUpdatedAt = () -> inTenantTx(() -> group("project_info").getUpdatedAt());
    Supplier<Instant> definitionUpdatedAt =
        () -> inTenantTx(() -> definition("category").getUpdatedAt());
    Instant groupBefore = groupUpdatedAt.get();
    Instant definitionBefore = definitionUpdatedAt.get();

    fieldPackSeeder.seedPacksForTenant(schema, ORG_ID);

    assertThat(groupUpdatedAt.get()).isEqualTo(groupBefore);
    assertThat(definitionUpdatedAt.get()).isEqualTo(definitionBefore);
    assertThat(inTenantTx(() -> group("project_info").getName())).isEqualTo(V3_GROUP_NAME);
  }

  /**
   * No-Spring guard over every shipped field pack with a {@code reconcile} block: each entry
   * targets the pack's group slug ({@code field_group}) or one of its field slugs ({@code
   * field_definition}), its {@code to} equals the value shipped in the same file, and {@code from}
   * differs — a later JSON edit without updating {@code to} fails here, not at a tenant's boot.
   * Cumulative-chain convention ({@code backend/CLAUDE.md} › Pack reconcile contract): every {@code
   * to} is the head (the shipped value) and every {@code from} a value of some prior version; when
   * a field is re-edited, re-point every older entry's {@code to} to the new head and add one entry
   * whose {@code from} is the previous head.
   */
  @Test
  void shippedFieldPackReconcileEntries_targetDeclaredKeysAndShippedValues() throws IOException {
    var mapper = new ObjectMapper();
    int checked = 0;
    for (Resource resource :
        new PathMatchingResourcePatternResolver().getResources("classpath:field-packs/*.json")) {
      FieldPackDefinition pack;
      try (InputStream in = resource.getInputStream()) {
        pack = mapper.readValue(in, FieldPackDefinition.class);
      }
      for (PackReconcileEntry e : PackReconcileEntry.parse(pack.packId(), pack.reconcile())) {
        String where = pack.packId() + " " + e.entity() + " '" + e.key() + "' " + e.field();
        assertThat(e.field()).as(where).isIn("name", "description");
        Object shipped =
            switch (e.entity()) {
              case "field_group" -> {
                assertThat(e.key()).as(where).isEqualTo(pack.group().slug());
                yield "name".equals(e.field()) ? pack.group().name() : pack.group().description();
              }
              case "field_definition" -> {
                var field =
                    pack.fields().stream()
                        .filter(f -> e.key().equals(f.slug()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(where + ": no such field slug"));
                yield "name".equals(e.field()) ? field.name() : field.description();
              }
              default -> throw new AssertionError(where + ": unexpected entity");
            };
        assertThat(e.to()).as(where).isEqualTo(shipped);
        assertThat(e.from()).as(where).isInstanceOf(String.class).isNotEqualTo(e.to());
        checked++;
      }
    }
    assertThat(checked)
        .as("common-project (3) + legal-za-project (1) + conveyancing (1)")
        .isEqualTo(5);
  }

  /**
   * Unsupported entries are WARN-logged and skipped, never thrown (no tenant needed: every case is
   * rejected before a repository lookup).
   */
  @Test
  void unsupportedEntries_warnAndNeverThrow() throws IOException {
    FieldPackDefinition shipped;
    try (InputStream in =
        new PathMatchingResourcePatternResolver()
            .getResource("classpath:field-packs/common-project.json")
            .getInputStream()) {
      shipped = new ObjectMapper().readValue(in, FieldPackDefinition.class);
    }
    var seeder =
        new FieldPackSeeder(
            mock(ResourcePatternResolver.class),
            new ObjectMapper(),
            mock(FieldDefinitionRepository.class),
            mock(FieldGroupRepository.class),
            mock(FieldGroupMemberRepository.class),
            mock(OrgSettingsRepository.class),
            mock(TenantTransactionHelper.class),
            mock(PackVersionGate.class));
    var pack =
        new FieldPackDefinition(
            shipped.packId(),
            shipped.version(),
            shipped.verticalProfile(),
            shipped.entityType(),
            shipped.group(),
            shipped.fields(),
            List.of(
                new PackReconcileEntry("field_option", "project_info", "name", "a", "Matter Info"),
                new PackReconcileEntry("field_option", "project_info", "slug", "a", 42),
                new PackReconcileEntry("field_group", "project_info", "slug", "a", "b"),
                new PackReconcileEntry("field_group", "project_info", "name", "a", 42),
                new PackReconcileEntry("field_group", "other_group", "name", "a", "Matter Info"),
                new PackReconcileEntry("field_group", "project_info", "name", "a", "Not shipped"),
                new PackReconcileEntry("field_definition", "no_such_field", "name", "a", "b")));

    var logs = new java.util.ArrayList<String>();
    assertThatCode(
            () -> logs.addAll(captureLogs(() -> seeder.reconcileInstalledPack(pack, null, "t"))))
        .doesNotThrowAnyException();

    assertThat(logs).filteredOn(m -> m.startsWith("Reconcile entry ignored")).hasSize(7);
    // the entity is checked first, so an unsupported entity is reported as such even when its
    // field / value are unsupported too
    assertThat(logs)
        .filteredOn(m -> m.contains("'field_option'") && m.endsWith("unsupported entity"))
        .hasSize(2);
  }

  // --- helpers ---

  /**
   * Writes the v2 common-project strings back (group name = {@code groupName}, so a firm rename is
   * simulated by passing another value) and, when {@code rewind}, rewinds the ledger 3 → 2.
   */
  private void simulateCommonV2(String groupName, boolean rewind) {
    inTenantTx(
        () -> {
          var group = group("project_info");
          group.updateMetadata(groupName, V2_GROUP_DESC, group.getSortOrder());
          fieldGroupRepository.save(group);
          var category = definition("category");
          category.updateMetadata(
              category.getName(),
              V2_CATEGORY_DESC,
              category.isRequired(),
              category.getValidation());
          fieldDefinitionRepository.save(category);
          if (rewind) {
            rewind(COMMON_PACK, "3", "2");
          }
          return null;
        });
  }

  /**
   * Sets {@code pack_id} on the {@code project_info} group and {@code category} definition, and the
   * definition's {@code pack_field_key}; {@code (COMMON_PACK, "category")} restores the seeded
   * state.
   */
  private void setPackOwnership(String packId, String categoryPackFieldKey) {
    inTenantTx(
        () -> {
          var group = group("project_info");
          group.setPackId(packId);
          fieldGroupRepository.save(group);
          var category = definition("category");
          category.setPackId(packId);
          category.setPackFieldKey(categoryPackFieldKey);
          fieldDefinitionRepository.save(category);
          return null;
        });
  }

  /** CAS-rewinds the ledger; must run inside a tenant tx. */
  private void rewind(String packId, String from, String to) {
    assertThat(packInstallRepository.compareAndSetVersion(packId, from, to))
        .as("rewind %s %s -> %s", packId, from, to)
        .isEqualTo(1);
  }

  private FieldGroup group(String slug) {
    return fieldGroupRepository
        .findByEntityTypeAndSlug(EntityType.PROJECT, slug)
        .orElseThrow(() -> new AssertionError("No field group " + slug));
  }

  private FieldDefinition definition(String slug) {
    return fieldDefinitionRepository
        .findByEntityTypeAndSlug(EntityType.PROJECT, slug)
        .orElseThrow(() -> new AssertionError("No field definition " + slug));
  }

  private String ledgerVersion(String packId) {
    return packInstallRepository.findByPackId(packId).orElseThrow().getPackVersion();
  }

  private <T> T inTenantTx(Supplier<T> body) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .call(() -> transactionTemplate.execute(tx -> body.get()));
  }

  private static List<String> captureLogs(Runnable action) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(FieldPackSeeder.class);
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
