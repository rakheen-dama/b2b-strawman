package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateDefinition.AutomationTemplatePack;
import io.b2mash.b2b.b2bstrawman.clause.ClausePackDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackDefinition;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestPackDefinition;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstall;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for the ADR-356 reconcile framework (Epic 625E): the {@link PackReconcileEntry} one
 * rule and parser, the {@link AbstractPackSeeder} version-gate flow (driven through a fake seeder
 * with a mocked {@link PackVersionGate}), {@link PackVersionGate} itself (mocked repository), and a
 * classpath guard over every real pack's {@code reconcile} block. No Spring context — log lines are
 * asserted with a Logback {@link ListAppender} (application-test.yml sets everything to ERROR).
 */
class AbstractPackSeederReconcileTest {

  private static final String TENANT = "tenant_aaaaaaaaaaaa";
  private static final String ORG = "org_reconcile_unit";
  private static final String PACK = "fake-pack";

  record FakePack(String packId, int version, List<PackReconcileEntry> reconcile)
      implements ReconcilablePack {}

  static final class Row {
    String name;
    Boolean enabled;
    Map<String, Object> body;
  }

  static final class FakeSeeder extends AbstractPackSeeder<FakePack> {
    final Map<String, Row> rows = new HashMap<>();
    FakePack pack;
    PackType type = PackType.FIELD;
    int hookCalls;
    int applyCalls;

    FakeSeeder(OrgSettingsRepository repo, TenantTransactionHelper tx, PackVersionGate gate) {
      super(mock(ResourcePatternResolver.class), new ObjectMapper(), repo, tx, gate);
    }

    @Override
    protected List<LoadedPack<FakePack>> loadPacks() {
      return List.of(new LoadedPack<>(pack, null));
    }

    @Override
    protected String getPackResourcePattern() {
      return "classpath:fake/*.json";
    }

    @Override
    protected Class<FakePack> getPackDefinitionType() {
      return FakePack.class;
    }

    @Override
    protected String getPackTypeName() {
      return "fake";
    }

    @Override
    protected String getPackId(FakePack p) {
      return p.packId();
    }

    @Override
    protected String getPackVersion(FakePack p) {
      return String.valueOf(p.version());
    }

    @Override
    protected String getVerticalProfile(FakePack p) {
      return null;
    }

    @Override
    protected boolean isPackAlreadyApplied(OrgSettings s, String id) {
      var l = s.getPackStatus().getFieldPackStatus();
      return l != null && l.stream().anyMatch(e -> id.equals(e.get("packId")));
    }

    @Override
    protected void recordPackApplication(OrgSettings s, FakePack p) {
      s.getPackStatus().recordPackApplication(p.packId(), p.version());
    }

    @Override
    protected void applyPack(FakePack p, Resource r, String t) {
      applyCalls++;
    }

    @Override
    protected PackType getPackType() {
      return type;
    }

    @Override
    protected void reconcileExistingPack(FakePack p, Resource r, String t) {
      hookCalls++;
      super.reconcileExistingPack(p, r, t);
    }

    @Override
    protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
        FakePack p, PackReconcileEntry e, Resource r) {
      Row row = rows.get(e.key());
      if (row == null) {
        return Optional.empty();
      }
      return Optional.of(
          switch (e.field()) {
            case "name" -> target(() -> row.name, v -> row.name = (String) v);
            case "enabled" -> target(() -> row.enabled, v -> row.enabled = (Boolean) v);
            case "content" -> target(() -> row.body, v -> row.body = NEW_BODY);
            default -> throw new IllegalArgumentException(e.field());
          });
    }
  }

  /** A target that records every write — used to prove ALREADY_CURRENT/LEFT_ALONE never write. */
  static final class CountingTarget implements PackReconcileEntry.Target {
    Object value;
    int writes;

    CountingTarget(Object value) {
      this.value = value;
    }

    @Override
    public Object currentValue() {
      return value;
    }

    @Override
    public void apply(Object to) {
      writes++;
      value = to;
    }
  }

  static final Map<String, Object> OLD_BODY = body("Old letterhead");
  static final Map<String, Object> NEW_BODY = body("New letterhead");

  private static Map<String, Object> body(String text) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "doc");
    m.put("content", List.of(Map.of("type", "paragraph", "text", text)));
    return m;
  }

  private static PackReconcileEntry.Target target(Supplier<Object> get, Consumer<Object> set) {
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return get.get();
      }

      @Override
      public void apply(Object to) {
        set.accept(to);
      }
    };
  }

  private static String sha(Object json) {
    return PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(json);
  }

  private static PackReconcileEntry entry(String key, String field, Object from, Object to) {
    return new PackReconcileEntry("automation_rule", key, field, from, to);
  }

  // --- Logback capture (FakeSeeder logger + PackVersionGate logger) ---

  private ListAppender<ILoggingEvent> appender;
  private final List<Logger> capturedLoggers = new ArrayList<>();

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    for (Class<?> c : List.of(FakeSeeder.class, PackVersionGate.class)) {
      Logger logger = (Logger) LoggerFactory.getLogger(c);
      logger.addAppender(appender);
      logger.setLevel(Level.INFO);
      capturedLoggers.add(logger);
    }
  }

  @AfterEach
  void detachAppender() {
    for (Logger logger : capturedLoggers) {
      logger.detachAppender(appender);
      logger.setLevel(null);
    }
    capturedLoggers.clear();
  }

  private boolean logged(Level level, String... fragments) {
    return appender.list.stream()
        .anyMatch(
            e -> {
              if (e.getLevel() != level) {
                return false;
              }
              String msg = e.getFormattedMessage();
              for (String f : fragments) {
                if (!msg.contains(f)) {
                  return false;
                }
              }
              return true;
            });
  }

  private Logger fakeLogger() {
    return (Logger) LoggerFactory.getLogger(FakeSeeder.class);
  }

  // =====================================================================================
  // The one rule
  // =====================================================================================

  @Nested
  class OneRule {

    @Test
    void stringEqualToFrom_isApplied_andLogged() {
      var t = new CountingTarget("Document accepted");
      var e =
          entry("document-review-notification", "name", "Document accepted", "Document uploaded");

      var outcome = e.applyAndLog(Optional.of(t), fakeLogger(), TENANT, PACK);

      assertThat(outcome).isEqualTo(PackReconcileEntry.Outcome.APPLIED);
      assertThat(t.value).isEqualTo("Document uploaded");
      assertThat(t.writes).isEqualTo(1);
      assertThat(
              logged(Level.INFO, "Reconcile applied", TENANT, PACK, "document-review-notification"))
          .isTrue();
    }

    @Test
    void editedString_isLeftAlone_andLoggedWithTenantPackKey() {
      var t = new CountingTarget("Our firm's own name");
      var e = entry("rule-edited", "name", "Document accepted", "Document uploaded");

      var outcome = e.applyAndLog(Optional.of(t), fakeLogger(), TENANT, PACK);

      assertThat(outcome).isEqualTo(PackReconcileEntry.Outcome.LEFT_ALONE);
      assertThat(t.value).isEqualTo("Our firm's own name");
      assertThat(t.writes).isZero();
      assertThat(logged(Level.INFO, TENANT, PACK, "rule-edited")).isTrue();
    }

    @Test
    void alreadyAtTo_isAlreadyCurrent_andNeverWritten() {
      var t = new CountingTarget("Document uploaded");
      var e = entry("k", "name", "Document accepted", "Document uploaded");

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.ALREADY_CURRENT);
      assertThat(t.writes).isZero();
    }

    @Test
    void booleanTrueToFalse_isApplied_andAlreadyFalseIsCurrent() {
      var enabled = new CountingTarget(Boolean.TRUE);
      var e = entry("document-review-notification", "enabled", true, false);
      assertThat(e.applyTo(enabled)).isEqualTo(PackReconcileEntry.Outcome.APPLIED);
      assertThat(enabled.value).isEqualTo(Boolean.FALSE);

      var disabled = new CountingTarget(Boolean.FALSE);
      assertThat(e.applyTo(disabled)).isEqualTo(PackReconcileEntry.Outcome.ALREADY_CURRENT);
      assertThat(disabled.writes).isZero();
    }

    @Test
    void stringTrueDoesNotMatchBooleanTrue_typeStrict() {
      var t = new CountingTarget(Boolean.TRUE);
      var e = entry("k", "enabled", "true", false);

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.LEFT_ALONE);
      assertThat(t.value).isEqualTo(Boolean.TRUE);
    }

    @Test
    void numbersCompareByValueAcrossIntegerAndLong() {
      var t = new CountingTarget(1L);
      var e = entry("k", "sortOrder", 1, 2);

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.APPLIED);
      assertThat(t.value).isEqualTo(2);
    }

    @Test
    void hashFromMatchesPristineBody_isApplied() {
      var t = new CountingTarget(OLD_BODY);
      var e =
          new PackReconcileEntry(
              "document_template", "fee-note", "content", sha(OLD_BODY), sha(NEW_BODY));

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.APPLIED);
      assertThat(t.writes).isEqualTo(1);
    }

    @Test
    void hashFromDoesNotMatchEditedBody_isLeftAlone() {
      var t = new CountingTarget(body("Firm-edited letterhead"));
      var e =
          new PackReconcileEntry(
              "document_template", "fee-note", "content", sha(OLD_BODY), sha(NEW_BODY));

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.LEFT_ALONE);
      assertThat(t.writes).isZero();
    }

    @Test
    void hashToEqualsCurrentBody_isAlreadyCurrent() {
      var t = new CountingTarget(NEW_BODY);
      var e =
          new PackReconcileEntry(
              "document_template", "fee-note", "content", sha(OLD_BODY), sha(NEW_BODY));

      assertThat(e.applyTo(t)).isEqualTo(PackReconcileEntry.Outcome.ALREADY_CURRENT);
      assertThat(t.writes).isZero();
    }

    @Test
    void bodyHashIsKeyOrderInsensitive() {
      Map<String, Object> forward = new LinkedHashMap<>();
      forward.put("type", "doc");
      forward.put("attrs", Map.of("a", 1, "b", 2));
      Map<String, Object> reversed = new LinkedHashMap<>();
      reversed.put("attrs", Map.of("b", 2, "a", 1));
      reversed.put("type", "doc");

      assertThat(ContentHashUtil.hashOf(forward)).isEqualTo(ContentHashUtil.hashOf(reversed));
      assertThat(ContentHashUtil.hashOf(forward)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void noTarget_isLoggedAsNoPackSeededRow() {
      var e = entry("missing-rule", "name", "a", "b");

      var outcome = e.applyAndLog(Optional.empty(), fakeLogger(), TENANT, PACK);

      assertThat(outcome).isEqualTo(PackReconcileEntry.Outcome.NO_TARGET);
      assertThat(logged(Level.INFO, "no pack-seeded row", TENANT, PACK, "missing-rule")).isTrue();
    }
  }

  // =====================================================================================
  // Parser
  // =====================================================================================

  @Nested
  class Parser {

    @Test
    void nullOrEmptyBlock_parsesToEmptyList() {
      assertThat(PackReconcileEntry.parse(PACK, null)).isEmpty();
      assertThat(PackReconcileEntry.parse(PACK, List.of())).isEmpty();
    }

    @Test
    void missingRequiredField_isRejectedNamingPackAndIndex() {
      var ok = entry("k", "name", "a", "b");
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK, List.of(ok, new PackReconcileEntry(null, "k", "name", "a", "b"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(PACK)
          .hasMessageContaining("reconcile[1]");
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK, List.of(new PackReconcileEntry("e", " ", "name", "a", "b"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("reconcile[0]");
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK, List.of(new PackReconcileEntry("e", "k", null, "a", "b"))))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK, List.of(new PackReconcileEntry("e", "k", "name", "a", null))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'to'");
    }

    @Test
    void objectFrom_isRejected() {
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK, List.of(new PackReconcileEntry("e", "k", "f", Map.of("x", 1), "b"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("'from'");
    }

    @Test
    void malformedHash_isRejected() {
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK,
                      List.of(new PackReconcileEntry("e", "k", "content", "sha256:abc", "b"))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("sha256");
      assertThatThrownBy(
              () ->
                  PackReconcileEntry.parse(
                      PACK,
                      List.of(
                          new PackReconcileEntry(
                              "e", "k", "content", sha(OLD_BODY), "sha256:" + "A".repeat(64)))))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validBlock_parsesUnchanged() {
      var a = entry("k", "name", "a", "b");
      var b =
          new PackReconcileEntry("document_template", "t", "content", sha(OLD_BODY), sha(NEW_BODY));
      assertThat(PackReconcileEntry.parse(PACK, List.of(a, b))).containsExactly(a, b);
    }
  }

  // =====================================================================================
  // AbstractPackSeeder version-gate flow (FakeSeeder + mocked PackVersionGate)
  // =====================================================================================

  @Nested
  class SeederFlow {

    private OrgSettingsRepository orgSettingsRepository;
    private TenantTransactionHelper tx;
    private PackVersionGate gate;
    private OrgSettings settings;
    private FakeSeeder seeder;

    @BeforeEach
    void setUp() {
      orgSettingsRepository = mock(OrgSettingsRepository.class);
      tx = mock(TenantTransactionHelper.class);
      gate = mock(PackVersionGate.class);
      settings = new OrgSettings("ZAR");
      when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
      doAnswer(
              inv -> {
                Consumer<String> action = inv.getArgument(2);
                action.accept(inv.getArgument(0));
                return null;
              })
          .when(tx)
          .executeInTenantTransaction(anyString(), anyString(), any());
      seeder = new FakeSeeder(orgSettingsRepository, tx, gate);
      seeder.pack =
          new FakePack(
              PACK,
              2,
              List.of(
                  entry("rule-pristine", "name", "Document accepted", "Document uploaded"),
                  entry("rule-edited", "name", "Document accepted", "Document uploaded"),
                  entry("rule-pristine", "enabled", true, false)));
      Row pristine = new Row();
      pristine.name = "Document accepted";
      pristine.enabled = true;
      Row edited = new Row();
      edited.name = "Firm renamed this";
      edited.enabled = true;
      seeder.rows.put("rule-pristine", pristine);
      seeder.rows.put("rule-edited", edited);
    }

    private void markApplied(int... versions) {
      for (int v : versions) {
        settings.getPackStatus().recordPackApplication(PACK, v);
      }
    }

    @Test
    void gateClosed_hookNotCalled() {
      markApplied(1);
      when(gate.tryOpen(PackType.FIELD, PACK, "2", TENANT)).thenReturn(false);

      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.hookCalls).isZero();
      assertThat(seeder.rows.get("rule-pristine").name).isEqualTo("Document accepted");
      assertThat(seeder.applyCalls).isZero();
    }

    @Test
    void gateOpen_hookCalledOnce_entriesAppliedUnderOneRule() {
      markApplied(1);
      when(gate.tryOpen(PackType.FIELD, PACK, "2", TENANT)).thenReturn(true);

      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.hookCalls).isEqualTo(1);
      assertThat(seeder.rows.get("rule-pristine").name).isEqualTo("Document uploaded");
      assertThat(seeder.rows.get("rule-pristine").enabled).isFalse();
      assertThat(seeder.rows.get("rule-edited").name).isEqualTo("Firm renamed this");
      assertThat(logged(Level.INFO, TENANT, PACK, "rule-edited")).isTrue();
      assertThat(logged(Level.INFO, "Reconcile applied", TENANT, PACK, "rule-pristine")).isTrue();
    }

    @Test
    void backfillRecordsTheOrgSettingsVersion_notTheClasspathVersion() {
      markApplied(1);

      seeder.seedPacksForTenant(TENANT, ORG);

      verify(gate).recordIfAbsent(PackType.FIELD, PACK, "1", PACK);
      verify(gate, never()).recordIfAbsent(PackType.FIELD, PACK, "2", PACK);
    }

    @Test
    void backfillTakesTheHighestRecordedVersionWhenTheListHasDuplicates() {
      markApplied(1, 2);

      seeder.seedPacksForTenant(TENANT, ORG);

      verify(gate).recordIfAbsent(PackType.FIELD, PACK, "2", PACK);
    }

    @Test
    void notYetApplied_appliesPackAndRecordsClasspathVersion_gateNotOpened() {
      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.applyCalls).isEqualTo(1);
      verify(gate).recordIfAbsent(PackType.FIELD, PACK, "2", PACK);
      verify(gate, never()).tryOpen(any(), anyString(), anyString(), anyString());
      assertThat(seeder.hookCalls).isZero();
    }

    @Test
    void untypedSeeder_hookRunsOnEveryPass_gateNeverTouched() {
      seeder.type = null;
      markApplied(1);

      seeder.seedPacksForTenant(TENANT, ORG);
      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.hookCalls).isEqualTo(2);
      verifyNoInteractions(gate);
    }

    @Test
    void installerManagedType_appliedPackIsANoOp_gateNeverTouched() {
      seeder.type = PackType.AUTOMATION_TEMPLATE;
      markApplied(1);

      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.hookCalls).isZero();
      verifyNoInteractions(gate);
    }

    @Test
    void installerManagedType_unappliedPackWritesNoLedgerRow() {
      seeder.type = PackType.AUTOMATION_TEMPLATE;

      seeder.seedPacksForTenant(TENANT, ORG);

      assertThat(seeder.applyCalls).isEqualTo(1);
      verifyNoInteractions(gate);
    }

    @Test
    void reconcileInstalledPack_runsHookWithoutConsultingTheGate() {
      seeder.type = PackType.AUTOMATION_TEMPLATE;

      seeder.reconcileInstalledPack(seeder.pack, null, TENANT);

      assertThat(seeder.hookCalls).isEqualTo(1);
      assertThat(seeder.rows.get("rule-pristine").enabled).isFalse();
      verifyNoInteractions(gate);
    }
  }

  // =====================================================================================
  // PackVersionGate (mocked repository)
  // =====================================================================================

  @Nested
  class VersionGate {

    private PackInstallRepository repository;
    private PackVersionGate gate;

    @BeforeEach
    void setUp() {
      repository = mock(PackInstallRepository.class);
      gate = new PackVersionGate(repository);
    }

    private void row(PackType type, String version) {
      when(repository.findByPackId("p"))
          .thenReturn(
              Optional.of(new PackInstall("p", type, version, "p", Instant.now(), null, 0)));
    }

    @Test
    void lowerRecordedVersion_claimsAdvanceAndOpens() {
      row(PackType.FIELD, "1");
      when(repository.compareAndSetVersion("p", "1", "2")).thenReturn(1);

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isTrue();
      verify(repository).compareAndSetVersion("p", "1", "2");
    }

    @Test
    void equalVersion_staysClosedWithoutCas() {
      row(PackType.FIELD, "2");

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isFalse();
      verify(repository, never()).compareAndSetVersion(anyString(), anyString(), anyString());
    }

    @Test
    void nonNumericRecordedVersion_closesAndWarns() {
      row(PackType.FIELD, "1.0.0");

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isFalse();
      assertThat(logged(Level.WARN, "non-numeric", "p", TENANT)).isTrue();
      verify(repository, never()).compareAndSetVersion(anyString(), anyString(), anyString());
    }

    @Test
    void nonNumericClasspathVersion_closesAndWarns() {
      row(PackType.FIELD, "1");

      assertThat(gate.tryOpen(PackType.FIELD, "p", "x", TENANT)).isFalse();
      assertThat(logged(Level.WARN, "non-numeric", "p")).isTrue();
    }

    @Test
    void missingRow_closesAndWarns() {
      when(repository.findByPackId("p")).thenReturn(Optional.empty());

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isFalse();
      assertThat(logged(Level.WARN, "no pack_install row", "p", TENANT)).isTrue();
    }

    @Test
    void rowOfAnotherType_closesAndWarns() {
      row(PackType.DOCUMENT_TEMPLATE, "1");

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isFalse();
      assertThat(logged(Level.WARN, "p", "DOCUMENT_TEMPLATE")).isTrue();
      verify(repository, never()).compareAndSetVersion(anyString(), anyString(), anyString());
    }

    @Test
    void lostCasRace_closes() {
      row(PackType.FIELD, "1");
      when(repository.compareAndSetVersion("p", "1", "2")).thenReturn(0);

      assertThat(gate.tryOpen(PackType.FIELD, "p", "2", TENANT)).isFalse();
    }

    @Test
    void recordIfAbsent_insertsForSeederManagedTypes() {
      when(repository.insertIfAbsent("p", "CLAUSE", "1", "p")).thenReturn(1);

      assertThat(gate.recordIfAbsent(PackType.CLAUSE, "p", "1", "p")).isTrue();
      verify(repository).insertIfAbsent(eq("p"), eq("CLAUSE"), eq("1"), eq("p"));
    }

    @Test
    void recordIfAbsent_rejectsInstallerManagedTypes() {
      assertThatThrownBy(() -> gate.recordIfAbsent(PackType.DOCUMENT_TEMPLATE, "p", "1", "p"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> gate.recordIfAbsent(PackType.AUTOMATION_TEMPLATE, "p", "1", "p"))
          .isInstanceOf(IllegalArgumentException.class);
      verifyNoInteractions(repository);
    }

    @Test
    void parseVersion_integerOnly() {
      assertThat(PackVersionGate.parseVersion("7").getAsInt()).isEqualTo(7);
      assertThat(PackVersionGate.parseVersion(" 3 ").getAsInt()).isEqualTo(3);
      assertThat(PackVersionGate.parseVersion("1.0.0")).isEmpty();
      assertThat(PackVersionGate.parseVersion(null)).isEmpty();
    }
  }

  // =====================================================================================
  // Classpath guard: every real pack's reconcile block parses; typed pack ids are unique
  // =====================================================================================

  @Test
  void everyClasspathReconcileBlockParses_andTypedPackIdsAreUnique() throws Exception {
    var resolver = new PathMatchingResourcePatternResolver();
    // Mirrors Spring Boot's mapper (unknown properties ignored).
    var mapper =
        JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    List<String> ids = new ArrayList<>();
    ids.addAll(
        loadAndParse(
            resolver,
            mapper,
            "classpath*:template-packs/*/pack.json",
            TemplatePackDefinition.class,
            TemplatePackDefinition::packId));
    ids.addAll(
        loadAndParse(
            resolver,
            mapper,
            "classpath*:clause-packs/*/pack.json",
            ClausePackDefinition.class,
            ClausePackDefinition::packId));
    ids.addAll(
        loadAndParse(
            resolver,
            mapper,
            "classpath*:automation-templates/*.json",
            AutomationTemplatePack.class,
            AutomationTemplatePack::packId));
    ids.addAll(
        loadAndParse(
            resolver,
            mapper,
            "classpath*:field-packs/*.json",
            FieldPackDefinition.class,
            FieldPackDefinition::packId));
    ids.addAll(
        loadAndParse(
            resolver,
            mapper,
            "classpath*:request-packs/*.json",
            RequestPackDefinition.class,
            RequestPackDefinition::packId));
    ids.add("standard-reports");

    assertThat(ids)
        .as("pack_install.pack_id is UNIQUE — typed pack ids must not collide")
        .doesNotHaveDuplicates()
        .contains(
            "legal-za",
            "automation-common",
            "standard-clauses",
            "common-project",
            "fica-onboarding-pack");
  }

  private static <D extends ReconcilablePack> List<String> loadAndParse(
      ResourcePatternResolver resolver,
      ObjectMapper mapper,
      String pattern,
      Class<D> type,
      Function<D, String> packId)
      throws Exception {
    Resource[] resources = resolver.getResources(pattern);
    assertThat(resources).as(pattern).isNotEmpty();
    List<String> ids = new ArrayList<>();
    for (Resource resource : resources) {
      D pack;
      try (var in = resource.getInputStream()) {
        pack = mapper.readValue(in, type);
      }
      String id = packId.apply(pack);
      PackReconcileEntry.parse(id, pack.reconcile()); // throws on a malformed block
      ids.add(id);
    }
    return ids;
  }
}
