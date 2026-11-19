package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.reporting.ReportDefinition;
import io.b2mash.b2b.b2bstrawman.reporting.ReportDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.PackStatusSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Seeds the 9 trust accounting report definitions for legal-za tenants (G88-F40, Phase 89 630A;
 * Phase 92 650C v4). Called right after the standard report pack by {@code
 * TenantProvisioningService} (fresh tenants) and {@code PackReconciliationRunner} (every existing
 * tenant, every boot). Gated on the vertical profile only — the {@code trust_accounting} module
 * flag is deliberately not consulted (the trust reports page hides itself when the module is off;
 * architecture §14.3.1).
 *
 * <p>ADR-356 (625E shape, mirrors {@code StandardReportPackSeeder}): the pack is Java-defined, so
 * its reconcile block is the {@link #RECONCILE} constant beside {@link #PACK_VERSION}. An applied
 * pack is reconciled only when its {@code pack_install} ledger row (type REPORT) records a lower
 * version. Empty at v1 — bump {@link #PACK_VERSION} and add entries when a shipped definition
 * changes ("fresh tenants only" is a review reject).
 *
 * <p>v4 (Phase 92 650C, architecture §16.8.6, R11): every report-definition edit of the phase in
 * one bump. Three reports are added ({@code trust-position}, {@code
 * trust-interest-charges-schedule}, {@code unidentified-deposits-age-analysis}) and {@code
 * interest-allocation} is retired (its query no longer exists; ruling #11). Field entries cannot
 * add or remove a report, so the reconcile carries explicit {@value #MEMBERSHIP_FIELD} entries,
 * applied before the field entries inside the version gate's transaction ({@link
 * #reconcileExistingPack}).
 */
@Service
public class TrustReportPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(TrustReportPackSeeder.class);
  static final String PACK_ID = "trust-reports";
  static final int PACK_VERSION = 4;

  /** The ADR-356 field of an entry that adds a report to, or retires one from, the pack (650C). */
  static final String MEMBERSHIP_FIELD = "membership";

  /**
   * The {@code to} of a retire entry: {@link PackReconcileEntry#parse} refuses a null {@code to}.
   */
  static final String RETIRED = "retired";

  /** The head {@code section-35-data-pack} template body hash (v4, Phase 92 650C). */
  private static final String SECTION_35_V4_HASH =
      "sha256:387591f0206a9e6b911a7e57590cd5bf98d00f7a1c83af64b9c252365bbab7da";

  /** ADR-356 reconcile entity of this pack's rows ({@code key} = the report slug). */
  static final String REPORT_ENTITY = "report_definition";

  /** The vertical profile this pack belongs to (LegalTariffSeeder precedent). */
  private static final String LEGAL_ZA_PROFILE = "legal-za";

  /** Slugs this pack ships, in install order. */
  static final List<String> SLUGS =
      List.of(
          "trust-receipts-payments",
          "client-trust-balances",
          "trust-position",
          "client-ledger-statement",
          "trust-reconciliation",
          "investment-register",
          "trust-interest-charges-schedule",
          "unidentified-deposits-age-analysis",
          "section-35-data-pack");

  /**
   * ADR-356 reconcile block of the Java-defined trust-reports pack. Same entry contract as {@code
   * StandardReportPackSeeder.RECONCILE}: {@code name} / {@code description} are strings; {@code
   * parameterSchema} / {@code columnDefinitions} / {@code templateBody} are whole-value {@code
   * sha256:} + {@link ContentHashUtil#hashOf}. Bump {@link #PACK_VERSION} with every entry added;
   * entries are cumulative ({@code TrustReportPackSeederIntegrationTest} pins every {@code from} to
   * a prior version and every {@code to} to the factories below).
   *
   * <p>v2 (G89-F18): the Section 35 data pack PDF prints each section's rows (label + amount) and
   * totals instead of only the section names and row counts. Column definitions are unchanged — the
   * query now fills the declared {@code label} / {@code amount} columns.
   *
   * <p>v3 (G89-F48): the Section 35 PDF prints each amount as rand ("R 38 019,15", "-R 1 250,00")
   * instead of a raw decimal ("38019.15"). Cumulative chain (ADR-356): the v2 entry's {@code to} is
   * re-pointed at the v3 head, and one entry moves the v2 body to the head.
   *
   * <p>v4 (Phase 92 650C, §16.8.6): three {@value #MEMBERSHIP_FIELD} adds and one retire first;
   * then the v1/v2/v3 section-35 bodies re-pointed at the v4 head, and one entry per changed field
   * of the six surviving reports from its v3 value ({@code TrustReportsV3}) to the head. Every
   * template changes (the §16.8 scope note heads every report).
   */
  static final List<PackReconcileEntry> RECONCILE =
      List.of(
          // v4 membership (§16.8.6) — applied before every field entry
          entry("trust-position", MEMBERSHIP_FIELD, null, PACK_ID),
          entry("trust-interest-charges-schedule", MEMBERSHIP_FIELD, null, PACK_ID),
          entry("unidentified-deposits-age-analysis", MEMBERSHIP_FIELD, null, PACK_ID),
          entry("interest-allocation", MEMBERSHIP_FIELD, PACK_ID, RETIRED),
          // section-35-data-pack templateBody: the cumulative chain, every prior body to the head
          entry(
              "section-35-data-pack",
              "templateBody",
              "sha256:80af07498e6ad52e27de1794c56998c501f05dc4f9a31a3807ddabd904392603",
              SECTION_35_V4_HASH),
          entry(
              "section-35-data-pack",
              "templateBody",
              "sha256:6b70dd38153d35103916dfe479e40d2baa1d5647cf53b095e894011737d86c86",
              SECTION_35_V4_HASH),
          entry(
              "section-35-data-pack",
              "templateBody",
              "sha256:9b18be3fa0d10d296b0fde65ab4e32f0d133fdc4dc2c681385ea19272bbd67e1",
              SECTION_35_V4_HASH),
          // v4 field entries (650C): from = the v3 value (string, or sha256: of it), to = the head
          entry(
              "trust-receipts-payments",
              "columnDefinitions",
              "sha256:00d8f0ba5500c65d760aa3967d6b1bb656b9b6f66c2be26bc85d5184ee32d6cf",
              "sha256:0725c1bebe52193bd2e708ff1e1a867aa475df5782971eca696a85ef8ccf83f3"),
          entry(
              "trust-receipts-payments",
              "templateBody",
              "sha256:0869fce0a9c917d8c249ee3080d971e777b8b1c273c7da66273d525e9d34705c",
              "sha256:7a2d8d2eec3939c3c352f9e15678ab0ed1cb38cf5afda2e3c32c9051f461b4d3"),
          entry(
              "client-trust-balances",
              "name",
              "Client Trust Balances",
              "Trust Creditors List (Rule 54.15)"),
          entry(
              "client-trust-balances",
              "description",
              "Point-in-time balances per client for a trust account.",
              "Every person and non-client creditor for whom trust money is held, as at a date — the locked monthly list where one exists."),
          entry(
              "client-trust-balances",
              "parameterSchema",
              "sha256:5f8d0e0d727f0af24d59908a1dbf78b110efb0506f7e593cfd0fe801aa1152ee",
              "sha256:9862bea520f999d3b90ba6b491eff3c153d16e855ef5e8ad61ceb7072fef90ef"),
          entry(
              "client-trust-balances",
              "columnDefinitions",
              "sha256:177f4413b956151dacc985bb8553b17f2cbf95131033fba66e4b87170a59dab0",
              "sha256:393c048fcd415c488262104795d2a8a95cc0c82f16b01df8502ffd0f3a00b9a5"),
          entry(
              "client-trust-balances",
              "templateBody",
              "sha256:aec9f394cf0b173146e04708deec94027cd9fb32092ca335f6ffe1429c7f6459",
              "sha256:2b45db12ad684646f047f428b01ad4c29511a6c2950c31d36ac3c922dc287fb1"),
          entry(
              "client-ledger-statement",
              "templateBody",
              "sha256:f92c9fbba4344bdd3d8cab01323b9ff5d55e1a85078be3ab0ae7d1480cd4c205",
              "sha256:4482088ddb901c18cc8e9957073a47f413e9c83452be494492daa8207edf4355"),
          entry(
              "trust-reconciliation",
              "description",
              "Three-way reconciliation: bank vs cashbook vs client ledger.",
              "Bank, cashbook and trust creditors compared at the reconciliation's period end, with the stored creditor breakdown."),
          entry(
              "trust-reconciliation",
              "templateBody",
              "sha256:8a2b12482bb23fc26a63c24a289d293545c32477ca1c3b148415da414a467ded",
              "sha256:28a7c6b54a3eb6061433b95d83c8ccbb52f9450835c2b05fdd52b82651d8d163"),
          entry(
              "investment-register",
              "description",
              "List of all trust investments with status, principal, and interest earned.",
              "Every trust investment on the books: account, basis, principal, balance and interest — gross, the Fund's 5% and net."),
          entry(
              "investment-register",
              "parameterSchema",
              "sha256:8e2113c088389a900416b6b30899ee12b7d4309bdfd256c40fde2d8f04cb843b",
              "sha256:5520f1869ad09d1d7772d93f644bfa990e0506326280a0c15b3a2dd53eee99cc"),
          entry(
              "investment-register",
              "columnDefinitions",
              "sha256:e7c5f96f87042aca9e0e4a7796499becf3b32b905c9e3e6e67bb93d930f8d2a9",
              "sha256:665866afa02edc0fa531484afc7cf536ff0da86dcb26bfccc91c54801d757d8e"),
          entry(
              "investment-register",
              "templateBody",
              "sha256:99c62cd223381302d5b0dffb37452444489f4c2bbd10200e546e0b9eaf816b5f",
              "sha256:734f48721cb6883bfbd1af2568cb71c81199e5869e88eb33490805af78201dcc"));

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final PackVersionGate packVersionGate;

  public TrustReportPackSeeder(
      ReportDefinitionRepository reportDefinitionRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
    this.packVersionGate = packVersionGate;
  }

  public void seedForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeed(t));
  }

  private void doSeed(String tenantId) {
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    if (settings == null || !LEGAL_ZA_PROFILE.equals(settings.getVerticalProfile())) {
      log.debug("Skipping trust report pack for tenant {} (not legal-za profile)", tenantId);
      return;
    }

    if (isPackAlreadyApplied(settings)) {
      reconcileAppliedPack(settings, tenantId);
      return;
    }

    for (String slug : SLUGS) {
      upsertReport(shipped(slug));
    }

    settings.getPackStatus().recordReportPackApplication(PACK_ID, PACK_VERSION);
    orgSettingsRepository.save(settings);
    packVersionGate.recordIfAbsent(PackType.REPORT, PACK_ID, String.valueOf(PACK_VERSION), PACK_ID);

    log.info("Trust report pack seeded for tenant {}: {} definitions", tenantId, SLUGS.size());
  }

  private void reconcileAppliedPack(OrgSettings settings, String tenantId) {
    // OQ #14 backfill (tenants that applied the pack before the ledger row existed) + version gate.
    String appliedVersion =
        PackStatusSettings.highestAppliedVersion(
                settings.getPackStatus().getReportPackStatus(), PACK_ID)
            .orElse(null);
    if (packVersionGate.backfillAndTryOpen(
        PackType.REPORT,
        PACK_ID,
        appliedVersion,
        String.valueOf(PACK_VERSION),
        PACK_ID,
        tenantId)) {
      reconcileExistingPack(tenantId);
    }
  }

  /**
   * Applies {@link #RECONCILE} under the ADR-356 one rule, inside the version gate's transaction:
   * the {@value #MEMBERSHIP_FIELD} entries first (§16.8.6), then the field entries.
   */
  void reconcileExistingPack(String tenantId) {
    var entries = PackReconcileEntry.parse(PACK_ID, RECONCILE);
    for (PackReconcileEntry entry : entries) {
      if (isMembership(entry)) {
        applyMembership(entry, tenantId);
      }
    }
    for (PackReconcileEntry entry : entries) {
      if (!isMembership(entry)) {
        entry.applyAndLog(resolveReconcileTarget(entry), log, tenantId, PACK_ID);
      }
    }
  }

  static boolean isMembership(PackReconcileEntry entry) {
    return REPORT_ENTITY.equals(entry.entity()) && MEMBERSHIP_FIELD.equals(entry.field());
  }

  /**
   * A membership entry (650C, the {@code FieldPackSeeder} "membership" precedent). Add ({@code
   * null} → the pack): an additive upsert — if no row has the slug, create it exactly as a fresh
   * install does; a row that owns the slug is never written. Retire (the pack → {@value #RETIRED}):
   * delete the system row, firm-edited or not — its query bean no longer exists, so a surviving row
   * could only fail at run time (ruling #11; no foreign key references {@code report_definitions}).
   * A non-system row with the slug is left alone.
   */
  private void applyMembership(PackReconcileEntry entry, String tenantId) {
    if (entry.from() == null && PACK_ID.equals(entry.to())) {
      var shipped = shipped(entry.key());
      if (shipped == null) {
        unsupported(entry, "key is not a slug of this pack");
        return;
      }
      if (reportDefinitionRepository.findBySlug(entry.key()).isPresent()) {
        log.info(
            "Reconcile skipped (edited): tenant={} pack={} {} '{}' field {} — the slug is owned by"
                + " an existing row, left alone",
            tenantId,
            PACK_ID,
            entry.entity(),
            entry.key(),
            entry.field());
        return;
      }
      reportDefinitionRepository.save(shipped);
      log.info(
          "Reconcile applied: tenant={} pack={} {} '{}' field {} added",
          tenantId,
          PACK_ID,
          entry.entity(),
          entry.key(),
          entry.field());
      return;
    }
    if (PACK_ID.equals(entry.from()) && RETIRED.equals(entry.to())) {
      if (SLUGS.contains(entry.key())) {
        unsupported(entry, "a shipped slug cannot be retired");
        return;
      }
      var row = reportDefinitionRepository.findBySlug(entry.key());
      if (row.isEmpty()) {
        log.debug(
            "Reconcile no-op: tenant={} pack={} {} '{}' already retired",
            tenantId,
            PACK_ID,
            entry.entity(),
            entry.key());
        return;
      }
      if (!row.get().isSystem()) {
        log.info(
            "Reconcile skipped (edited): tenant={} pack={} {} '{}' field {} — not a system row,"
                + " left alone",
            tenantId,
            PACK_ID,
            entry.entity(),
            entry.key(),
            entry.field());
        return;
      }
      reportDefinitionRepository.delete(row.get());
      log.info(
          "Reconcile applied: tenant={} pack={} {} '{}' field {} retired (deleted)",
          tenantId,
          PACK_ID,
          entry.entity(),
          entry.key(),
          entry.field());
      return;
    }
    unsupported(entry, "membership entries are {null -> pack} (add) or {pack -> retired} (retire)");
  }

  /**
   * ADR-356 resolver (copied from StandardReportPackSeeder, 628C): the {@value #REPORT_ENTITY} row
   * an entry targets — {@code key} is the slug of a report this pack ships, ownership is the slug +
   * {@code is_system = true} (report definitions carry no pack column; there is no firm edit path
   * today, the one rule still applies). Fields: {@code name} / {@code description} (string {@code
   * to} equal to the shipped value) and {@code parameterSchema} / {@code columnDefinitions} /
   * {@code templateBody} ({@code sha256:} {@code to} equal to the hash of the shipped value; the
   * target ignores the hash string and writes the shipped value through the managed entity).
   * Unsupported entries are WARN-logged and skipped — nothing here throws at boot.
   */
  Optional<PackReconcileEntry.Target> resolveReconcileTarget(PackReconcileEntry entry) {
    if (!REPORT_ENTITY.equals(entry.entity()) || !(entry.to() instanceof String to)) {
      return unsupported(entry, "only report_definition entries with a string 'to' are supported");
    }
    ReportDefinition shipped = shipped(entry.key());
    if (shipped == null) {
      return unsupported(entry, "key is not a slug of this pack");
    }
    return switch (entry.field()) {
      case "name" ->
          stringTarget(entry, to, shipped.getName(), ReportDefinition::getName, d -> d::rename);
      case "description" ->
          stringTarget(
              entry,
              to,
              shipped.getDescription(),
              ReportDefinition::getDescription,
              d -> d::updateDescription);
      case "parameterSchema" ->
          hashTarget(
              entry,
              to,
              shipped.getParameterSchema(),
              ReportDefinition::getParameterSchema,
              d ->
                  () ->
                      d.updateDefinition(
                          d.getTemplateBody(),
                          shipped.getParameterSchema(),
                          d.getColumnDefinitions()));
      case "columnDefinitions" ->
          hashTarget(
              entry,
              to,
              shipped.getColumnDefinitions(),
              ReportDefinition::getColumnDefinitions,
              d ->
                  () ->
                      d.updateDefinition(
                          d.getTemplateBody(),
                          d.getParameterSchema(),
                          shipped.getColumnDefinitions()));
      case "templateBody" ->
          hashTarget(
              entry,
              to,
              shipped.getTemplateBody(),
              ReportDefinition::getTemplateBody,
              d -> () -> d.updateTemplate(shipped.getTemplateBody()));
      default -> unsupported(entry, "unsupported field");
    };
  }

  private Optional<PackReconcileEntry.Target> stringTarget(
      PackReconcileEntry entry,
      String to,
      String shippedValue,
      Function<ReportDefinition, Object> current,
      Function<ReportDefinition, Consumer<String>> write) {
    if (to.startsWith(PackReconcileEntry.HASH_PREFIX) || !to.equals(shippedValue)) {
      return unsupported(entry, "'to' does not match the shipped value");
    }
    return systemRow(entry)
        .map(d -> target(() -> current.apply(d), v -> write.apply(d).accept((String) v)));
  }

  private Optional<PackReconcileEntry.Target> hashTarget(
      PackReconcileEntry entry,
      String to,
      Object shippedValue,
      Function<ReportDefinition, Object> current,
      Function<ReportDefinition, Runnable> writeShipped) {
    if (!to.equals(PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(shippedValue))) {
      return unsupported(entry, "'to' is not the sha256: of the shipped value");
    }
    // sha256: contract — the argument is the hash string; write the shipped value.
    return systemRow(entry)
        .map(d -> target(() -> current.apply(d), ignoredHash -> writeShipped.apply(d).run()));
  }

  private Optional<ReportDefinition> systemRow(PackReconcileEntry entry) {
    return reportDefinitionRepository.findBySlug(entry.key()).filter(ReportDefinition::isSystem);
  }

  private static Optional<PackReconcileEntry.Target> unsupported(
      PackReconcileEntry entry, String reason) {
    log.warn(
        "Reconcile entry ignored: report pack {} entry '{}'.{} (key {}) — {}",
        PACK_ID,
        entry.entity(),
        entry.field(),
        entry.key(),
        reason);
    return Optional.empty();
  }

  private static PackReconcileEntry.Target target(
      Supplier<Object> current, Consumer<Object> write) {
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return current.get();
      }

      @Override
      public void apply(Object to) {
        write.accept(to);
      }
    };
  }

  private static PackReconcileEntry entry(String slug, String field, String from, String to) {
    return new PackReconcileEntry(REPORT_ENTITY, slug, field, from, to);
  }

  private boolean isPackAlreadyApplied(OrgSettings settings) {
    if (settings.getPackStatus().getReportPackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getReportPackStatus().stream()
        .anyMatch(entry -> PACK_ID.equals(entry.get("packId")));
  }

  private void upsertReport(ReportDefinition definition) {
    reportDefinitionRepository
        .findBySlug(definition.getSlug())
        .ifPresentOrElse(
            existing -> {
              existing.updateDefinition(
                  definition.getTemplateBody(),
                  definition.getParameterSchema(),
                  definition.getColumnDefinitions());
              reportDefinitionRepository.save(existing);
            },
            () -> reportDefinitionRepository.save(definition));
  }

  // --- Report Definition Factories ---

  /**
   * A fresh copy of the shipped definition for {@code slug}, or {@code null} for a slug this pack
   * does not ship.
   */
  static ReportDefinition shipped(String slug) {
    return switch (slug) {
      case "trust-receipts-payments" -> trustReceiptsPaymentsDefinition();
      case "client-trust-balances" -> clientTrustBalancesDefinition();
      case "trust-position" -> trustPositionDefinition();
      case "client-ledger-statement" -> clientLedgerStatementDefinition();
      case "trust-reconciliation" -> trustReconciliationDefinition();
      case "investment-register" -> investmentRegisterDefinition();
      case "trust-interest-charges-schedule" -> trustInterestChargesScheduleDefinition();
      case "unidentified-deposits-age-analysis" -> unidentifiedDepositsAgeDefinition();
      case "section-35-data-pack" -> section35DataPackDefinition();
      default -> null;
    };
  }

  private static Map<String, Object> trustAccountParam(boolean required) {
    return Map.of(
        "name",
        "trust_account_id",
        "type",
        "uuid",
        "label",
        "Trust Account",
        "required",
        required,
        "entityType",
        "trustAccount");
  }

  private static Map<String, Object> dateParam(String name, String label, boolean required) {
    return Map.of("name", name, "type", "date", "label", label, "required", required);
  }

  private static Map<String, Object> col(String key, String label, String type) {
    return Map.of("key", key, "label", label, "type", type);
  }

  private static Map<String, Object> moneyCol(String key, String label) {
    return Map.of("key", key, "label", label, "type", "currency", "format", "0.00");
  }

  @SafeVarargs
  private static Map<String, Object> parameters(Map<String, Object>... parameters) {
    return Map.<String, Object>of("parameters", List.of(parameters));
  }

  @SafeVarargs
  private static Map<String, Object> columns(Map<String, Object>... columns) {
    return Map.<String, Object>of("columns", List.of(columns));
  }

  private static ReportDefinition definition(
      String name,
      String slug,
      Map<String, Object> parameterSchema,
      Map<String, Object> columnDefinitions,
      String template,
      String description,
      int sortOrder) {
    var def =
        new ReportDefinition(name, slug, "TRUST", parameterSchema, columnDefinitions, template);
    def.setDescription(description);
    def.setSortOrder(sortOrder);
    return def;
  }

  private static ReportDefinition trustReceiptsPaymentsDefinition() {
    return definition(
        "Trust Receipts & Payments",
        "trust-receipts-payments",
        parameters(
            trustAccountParam(true),
            dateParam("dateFrom", "From Date", true),
            dateParam("dateTo", "To Date", true)),
        columns(
            col("date", "Date", "date"),
            col("reference", "Reference", "string"),
            col("type", "Type", "string"),
            col("ledgerHeading", "Ledger", "string"),
            col("clientName", "Client", "string"),
            moneyCol("credit", "Credit"),
            moneyCol("debit", "Debit"),
            moneyCol("balance", "Balance")),
        TRUST_RECEIPTS_PAYMENTS_TEMPLATE,
        "Chronological journal of all trust receipts and payments for a date range.",
        10);
  }

  private static ReportDefinition clientTrustBalancesDefinition() {
    return definition(
        "Trust Creditors List (Rule 54.15)",
        "client-trust-balances",
        parameters(trustAccountParam(false), dateParam("asOfDate", "As at Date", true)),
        columns(
            col("clientName", "Trust Creditor", "string"),
            moneyCol("generalBalance", "General Balance"),
            moneyCol("investedBalance", "Invested (s86(4))"),
            moneyCol("balance", "Total"),
            col("receivedDate", "Received", "date"),
            col("bankDescription", "Bank Description", "string"),
            col("sourceNote", "Source", "string")),
        CLIENT_TRUST_BALANCES_TEMPLATE,
        "Every person and non-client creditor for whom trust money is held, as at a date — the"
            + " locked monthly list where one exists.",
        20);
  }

  private static ReportDefinition trustPositionDefinition() {
    return definition(
        "Trust Position as at a Date",
        "trust-position",
        parameters(dateParam("asOfDate", "As at Date", true), trustAccountParam(false)),
        columns(
            col("accountName", "Trust Account", "string"),
            col("sectionLabel", "Section", "string"),
            moneyCol("cashbook", "Cashbook"),
            moneyCol("clientLedgers", "Client Ledgers"),
            moneyCol("investmentCreditors", "Section 86(4) Investments"),
            moneyCol("fundLedger", "Fidelity Fund Interest & Charges"),
            moneyCol("unidentifiedDeposits", "Unidentified Deposits"),
            moneyCol("interAccount", "Section 86(3) Placements"),
            moneyCol("creditorTotal", "Trust Creditors"),
            moneyCol("difference", "Difference"),
            moneyCol("adjustedBankBalance", "Adjusted Bank Balance"),
            col("bankNote", "Bank Comparison", "string"),
            col("integrity", "Integrity", "string")),
        TRUST_POSITION_TEMPLATE,
        "The cashbook against every trust creditor as at any date, per account and firm-wide, with"
            + " the integrity flags.",
        25);
  }

  private static ReportDefinition clientLedgerStatementDefinition() {
    return definition(
        "Client Ledger Statement",
        "client-ledger-statement",
        parameters(
            trustAccountParam(true),
            Map.of(
                "name",
                "customer_id",
                "type",
                "uuid",
                "label",
                "Client",
                "required",
                true,
                "entityType",
                "customer"),
            dateParam("dateFrom", "From Date", true),
            dateParam("dateTo", "To Date", true)),
        columns(
            col("date", "Date", "date"),
            col("reference", "Reference", "string"),
            col("type", "Type", "string"),
            col("description", "Description", "string"),
            moneyCol("debit", "Debit"),
            moneyCol("credit", "Credit"),
            moneyCol("runningBalance", "Running Balance")),
        CLIENT_LEDGER_STATEMENT_TEMPLATE,
        "Per-client transaction history with running balance.",
        30);
  }

  private static ReportDefinition trustReconciliationDefinition() {
    return definition(
        "Trust Reconciliation",
        "trust-reconciliation",
        parameters(
            trustAccountParam(true),
            Map.of(
                "name",
                "reconciliation_id",
                "type",
                "uuid",
                "label",
                "Reconciliation",
                "required",
                false)),
        columns(
            col("section", "Section", "string"),
            col("label", "Item", "string"),
            moneyCol("amount", "Amount")),
        TRUST_RECONCILIATION_TEMPLATE,
        "Bank, cashbook and trust creditors compared at the reconciliation's period end, with the"
            + " stored creditor breakdown.",
        40);
  }

  private static ReportDefinition investmentRegisterDefinition() {
    return definition(
        "Investment Register",
        "investment-register",
        parameters(
            trustAccountParam(true),
            Map.of(
                "name",
                "status",
                "type",
                "enum",
                "label",
                "Status Filter",
                "required",
                false,
                "options",
                List.of("ACTIVE", "MATURED", "WITHDRAWN"),
                "optionLabels",
                Map.of("ACTIVE", "Active", "MATURED", "Matured", "WITHDRAWN", "Withdrawn")),
            Map.of(
                "name",
                "investmentBasis",
                "type",
                "enum",
                "label",
                "Investment Basis",
                "required",
                false,
                "options",
                List.of("FIRM_DISCRETION", "CLIENT_INSTRUCTION"),
                "optionLabels",
                Map.of(
                    "FIRM_DISCRETION",
                    "Section 86(3) — firm's discretion",
                    "CLIENT_INSTRUCTION",
                    "Section 86(4) — client's instruction"))),
        columns(
            col("clientName", "Client", "string"),
            col("institution", "Institution", "string"),
            col("accountNumber", "Account #", "string"),
            col("investmentAccountName", "Investment Account", "string"),
            col("basisLabel", "Basis", "string"),
            moneyCol("principal", "Principal"),
            moneyCol("balance", "Balance"),
            moneyCol("grossInterest", "Gross Interest"),
            moneyCol("fundShare", "Fund 5%"),
            moneyCol("interestEarned", "Net Interest"),
            col("statusLabel", "Status", "string")),
        INVESTMENT_REGISTER_TEMPLATE,
        "Every trust investment on the books: account, basis, principal, balance and interest —"
            + " gross, the Fund's 5% and net.",
        50);
  }

  private static ReportDefinition trustInterestChargesScheduleDefinition() {
    return definition(
        "Interest and Charges Schedule (Rule 54.28)",
        "trust-interest-charges-schedule",
        parameters(
            dateParam("dateFrom", "From Month", true),
            dateParam("dateTo", "To Month", true),
            trustAccountParam(false)),
        columns(
            col("accountName", "Trust Account", "string"),
            col("sectionLabel", "Section", "string"),
            col("month", "Month", "string"),
            moneyCol("fundBroughtForward", "Fund Ledger b/f"),
            moneyCol("grossInterestCashbook", "Gross Interest (Cashbook)"),
            moneyCol("grossInterestMemo", "Gross Interest (At Source, Memo)"),
            moneyCol("clientShare", "Client's Share (s86(4))"),
            moneyCol("fundShareCredited", "Fund 5% Credited"),
            moneyCol("fundSharePaidAtSource", "Fund 5% Paid at Source"),
            moneyCol("chargesRecoverable", "Charges Recoverable"),
            moneyCol("chargesNonRecoverable", "Charges Non-recoverable"),
            moneyCol("chargesVat", "VAT in Charges"),
            moneyCol("chargesMemo", "Charges (At Source, Memo)"),
            moneyCol("chargesMemoVat", "VAT (Memo)"),
            moneyCol("chargesRefunded", "Charges Refunded"),
            moneyCol("paidToFund", "Paid to the Fund"),
            moneyCol("fundCarriedForward", "Fund Ledger c/f")),
        TRUST_INTEREST_CHARGES_SCHEDULE_TEMPLATE,
        "Rule 54.28: interest earned and charges levied per trust account and month, the Fund"
            + " ledger brought forward and carried forward; memo figures shown separately.",
        60);
  }

  private static ReportDefinition unidentifiedDepositsAgeDefinition() {
    return definition(
        "Unidentified Deposits Age Analysis",
        "unidentified-deposits-age-analysis",
        parameters(dateParam("asOfDate", "As at Date", true), trustAccountParam(false)),
        columns(
            col("accountName", "Trust Account", "string"),
            col("receivedDate", "Received", "date"),
            col("bankDescription", "Bank Description", "string"),
            moneyCol("amount", "Amount"),
            col("ageDays", "Age (days)", "integer"),
            col("band", "Age Band", "string"),
            col("position", "Position", "string")),
        UNIDENTIFIED_DEPOSITS_AGE_TEMPLATE,
        "Unidentified deposits open as at a date by age band, and those allocated or paid to the"
            + " Fund in the twelve months to it.",
        65);
  }

  private static ReportDefinition section35DataPackDefinition() {
    return definition(
        "Section 35 Data Pack",
        "section-35-data-pack",
        parameters(
            trustAccountParam(true),
            Map.of(
                "name",
                "financial_year_end",
                "type",
                "date",
                "label",
                "Financial Year End",
                "required",
                true)),
        columns(
            col("_section", "Section", "string"),
            col("label", "Item", "string"),
            moneyCol("amount", "Amount")),
        SECTION_35_TEMPLATE,
        "Composite report combining all trust sub-reports for Section 35 compliance.",
        70);
  }

  // --- Thymeleaf Template Constants (v4, Phase 92 650C) ---

  /**
   * A rand cell: {@code th:with} binds {@code expr}, printed "R 1 250,00" / "-R 1 250,00", "—" when
   * absent (a null number would fail the PDF export).
   */
  private static String rand(String expr) {
    return "th:with=\"v=${"
        + expr
        + "}\" th:text=\"${v == null ? '—' : (v lt 0 ? '-R ' + #numbers.formatDecimal(v * -1, 1,"
        + " 'WHITESPACE', 2, 'COMMA') : 'R ' + #numbers.formatDecimal(v, 1, 'WHITESPACE', 2,"
        + " 'COMMA'))}\"";
  }

  private static final String TRUST_REPORT_STYLE =
      """
              body { font-family: 'Helvetica Neue', Arial, sans-serif; font-size: 11px; color: #1a1a2e; margin: 0; padding: 20px; }
              .header { display: table; width: 100%; margin-bottom: 20px; border-bottom: 3px solid #1a1a2e; padding-bottom: 15px; }
              .header-left { display: table-cell; vertical-align: middle; }
              .header-right { display: table-cell; text-align: right; vertical-align: middle; }
              h1 { font-size: 20px; margin: 0 0 5px 0; }
              h2 { font-size: 13px; margin: 18px 0 8px 0; }
              .scope-note { font-size: 10px; color: #444; margin: 4px 0 0 0; }
              .note { font-size: 10px; color: #666; margin: 0 0 10px 0; }
              .param-summary { font-size: 10px; color: #666; margin-bottom: 20px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; }
              table.data { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
              table.data th { padding: 8px 10px; text-align: left; font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; background-color: #1a1a2e; }
              table.data td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 11px; }
              table.data tr:nth-child(even) td { background: #f8f9fa; }
              table.data tfoot td { font-weight: 700; border-top: 2px solid #1a1a2e; padding-top: 10px; }
              table.compact th, table.compact td { padding: 4px 5px; font-size: 8px; }
              .text-right { text-align: right; }
              .footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #e2e8f0; font-size: 9px; color: #999; display: table; width: 100%; }
              .footer-left { display: table-cell; }
              .footer-right { display: table-cell; text-align: right; }
      """;

  /** The document head and the header every trust report prints, with the §16.8 scope note. */
  private static String head(String title) {
    return """
        <!DOCTYPE html>
        <html xmlns:th="http://www.thymeleaf.org">
        <head>
            <meta charset="UTF-8"/>
            <title th:text="${report.name}">%s</title>
            <style>"""
            .formatted(title)
        + TRUST_REPORT_STYLE
        + """
            </style>
        </head>
        <body>
            <div class="header">
                <div class="header-left"><h1 th:text="${report.name}">%s</h1><p class="scope-note">%s</p></div>
                <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
            </div>
        """
            .formatted(title, TrustReportWords.TRUST_ASSETS_NOTE);
  }

  private static final String FOOT =
      """
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String TRUST_RECEIPTS_PAYMENTS_TEMPLATE =
      head("Trust Receipts &amp; Payments")
          + """
              <div class="param-summary">
                  <strong>Period:</strong> <span th:text="${parameters.dateFrom}"></span> to <span th:text="${parameters.dateTo}"></span>
              </div>
              <table class="data">
                  <thead><tr><th>Date</th><th>Reference</th><th>Type</th><th>Ledger</th><th>Client</th><th class="text-right">Credit</th><th class="text-right">Debit</th><th class="text-right">Balance</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['date']}"></td><td th:text="${row['reference']}"></td><td th:text="${row['type']}"></td><td th:text="${row['ledgerHeading']}"></td><td th:text="${row['clientName']}"></td>
          """
          + "                <td class=\"text-right\" "
          + rand("row['credit']")
          + "></td>\n                <td class=\"text-right\" "
          + rand("row['debit']")
          + "></td>\n                <td class=\"text-right\" "
          + rand("row['balance']")
          + """
          ></td>
                      </tr>
                  </tbody>
              </table>
              <h2>By ledger</h2>
              <table class="data">
                  <thead><tr><th>Ledger</th><th class="text-right">Receipts</th><th class="text-right">Payments</th></tr></thead>
                  <tbody>
                      <tr th:each="h : ${summary['headings']}">
                          <td th:text="${h['heading']}"></td>
          """
          + "                <td class=\"text-right\" "
          + rand("h['receipts']")
          + "></td>\n                <td class=\"text-right\" "
          + rand("h['payments']")
          + """
          ></td>
                      </tr>
                  </tbody>
              </table>
          """
          + FOOT;

  private static final String CLIENT_TRUST_BALANCES_TEMPLATE =
      head("Trust Creditors List (Rule 54.15)")
          + """
              <div class="param-summary">
                  <strong>As at:</strong> <span th:text="${parameters.asOfDate}"></span>
              </div>
              <p class="note" th:each="a : ${summary['accounts']}" th:text="${a['accountName'] + ': ' + a['sourceNote']}"></p>
              <table class="data">
                  <thead><tr><th>Trust creditor</th><th class="text-right">General</th><th class="text-right">Invested (s86(4))</th><th class="text-right">Total</th><th>Received</th><th>Bank description</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['clientName']}"></td>
          """
          + "                <td class=\"text-right\" "
          + rand("row['generalBalance']")
          + "></td>\n                <td class=\"text-right\" "
          + rand("row['investedBalance']")
          + "></td>\n                <td class=\"text-right\" "
          + rand("row['balance']")
          + """
          ></td>
                          <td th:text="${row['receivedDate']}"></td><td th:text="${row['bankDescription']}"></td>
                      </tr>
                  </tbody>
              </table>
              <h2>Comparison</h2>
              <table class="data">
                  <tbody>
          """
          + "            <tr><td>Total trust creditors</td><td class=\"text-right\" "
          + rand("summary['creditorTotal']")
          + "></td></tr>\n            <tr><td>Trust bank (adjusted, per trust banking account)</td><td class=\"text-right\" "
          + rand("summary['bankTotal']")
          + "></td></tr>\n            <tr><td>Trust investment accounts</td><td class=\"text-right\" "
          + rand("summary['investmentAccountTotal']")
          + "></td></tr>\n            <tr><td>Trust cash</td><td class=\"text-right\" th:text=\"${summary['trustCashNote']}\"></td></tr>\n            <tr><td><strong>Difference</strong></td><td class=\"text-right\" "
          + rand("summary['difference']")
          + """
          ></td></tr>
                  </tbody>
              </table>
              <p class="note" th:if="${summary['interAccountNote'] != null}" th:text="${summary['interAccountNote']}"></p>
              <div th:if="${not #lists.isEmpty(summary['afterLock'])}">
                  <h2>Entered after the list was locked</h2>
                  <table class="data">
                      <thead><tr><th>Account</th><th>Date</th><th>Reference</th><th>Type</th><th>Client</th><th class="text-right">Cashbook effect</th></tr></thead>
                      <tbody>
                          <tr th:each="e : ${summary['afterLock']}">
                              <td th:text="${e['accountName']}"></td><td th:text="${e['transactionDate']}"></td><td th:text="${e['reference']}"></td><td th:text="${e['typeLabel']}"></td><td th:text="${e['clientName']}"></td>
          """
          + "                    <td class=\"text-right\" "
          + rand("e['cashbookEffect']")
          + "></td>\n                </tr>\n            </tbody>\n            <tfoot><tr><td colspan=\"5\">Net effect</td><td class=\"text-right\" "
          + rand("summary['afterLockNet']")
          + """
          ></td></tr></tfoot>
                  </table>
              </div>
          """
          + FOOT;

  private static final String TRUST_POSITION_TEMPLATE =
      head("Trust Position as at a Date")
          + """
              <div class="param-summary">
                  <strong>As at:</strong> <span th:text="${parameters.asOfDate}"></span>
              </div>
              <table class="data compact">
                  <thead><tr><th>Trust account</th><th>Section</th><th class="text-right">Cashbook</th><th class="text-right">Client ledgers</th><th class="text-right">s86(4) investments</th><th class="text-right">Fund interest &amp; charges</th><th class="text-right">Unidentified deposits</th><th class="text-right">s86(3) placements</th><th class="text-right">Trust creditors</th><th class="text-right">Difference</th><th class="text-right">Adjusted bank</th><th>Integrity</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['accountName']}"></td><td th:text="${row['sectionLabel']}"></td>
          """
          + cells(
              "row",
              "cashbook",
              "clientLedgers",
              "investmentCreditors",
              "fundLedger",
              "unidentifiedDeposits",
              "interAccount",
              "creditorTotal",
              "difference",
              "adjustedBankBalance")
          + """
                          <td><span th:text="${row['integrity']}"></span><br th:if="${row['bankNote'] != null}"/><span th:if="${row['bankNote'] != null}" th:text="${row['bankNote']}"></span></td>
                      </tr>
                  </tbody>
                  <tfoot><tr><td colspan="2">Firm-wide</td>
          """
          + cells(
              "summary",
              "cashbook",
              "clientLedgers",
              "investmentCreditors",
              "fundLedger",
              "unidentifiedDeposits",
              "interAccount",
              "creditorTotal",
              "difference")
          + """
                  <td></td><td></td></tr></tfoot>
              </table>
          """
          + FOOT;

  /** One right-aligned rand cell per key of {@code var}. */
  private static String cells(String var, String... keys) {
    var sb = new StringBuilder();
    for (var key : keys) {
      sb.append("                <td class=\"text-right\" ")
          .append(rand(var + "['" + key + "']"))
          .append("></td>\n");
    }
    return sb.toString();
  }

  private static final String CLIENT_LEDGER_STATEMENT_TEMPLATE =
      head("Client Ledger Statement")
          + """
              <div class="param-summary">
                  <strong>Period:</strong> <span th:text="${parameters.dateFrom}"></span> to <span th:text="${parameters.dateTo}"></span>
              </div>
              <table class="data">
                  <thead><tr><th>Date</th><th>Reference</th><th>Type</th><th>Description</th><th class="text-right">Debit</th><th class="text-right">Credit</th><th class="text-right">Balance</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['date']}"></td><td th:text="${row['reference']}"></td><td th:text="${row['type']}"></td><td th:text="${row['description']}"></td>
          """
          + cells("row", "debit", "credit", "runningBalance")
          + """
                      </tr>
                  </tbody>
              </table>
              <div th:if="${summary['invested'] != null}" th:with="inv=${summary['invested']}">
                  <h2>Section 86(4) investment</h2>
                  <table class="data">
                      <tbody>
          """
          + "                <tr><td>Invested balance at the start of the period</td><td class=\"text-right\" "
          + rand("inv['openingBalance']")
          + "></td></tr>\n                <tr><td>Capital placed</td><td class=\"text-right\" "
          + rand("inv['capitalPlaced']")
          + "></td></tr>\n                <tr><td>Interest earned (gross)</td><td class=\"text-right\" "
          + rand("inv['interestGross']")
          + "></td></tr>\n                <tr><td>Less: the Fund's 5%</td><td class=\"text-right\" "
          + rand("inv['interestFundShare']")
          + "></td></tr>\n                <tr><td>Interest to the client (net)</td><td class=\"text-right\" "
          + rand("inv['interestNet']")
          + "></td></tr>\n                <tr><td>Returned to trust</td><td class=\"text-right\" "
          + rand("inv['returnedToTrust']")
          + "></td></tr>\n                <tr><td><strong>Invested balance at the end of the period</strong></td><td class=\"text-right\" "
          + rand("inv['closingBalance']")
          + """
          ></td></tr>
                      </tbody>
                  </table>
              </div>
          """
          + FOOT;

  private static final String TRUST_RECONCILIATION_TEMPLATE =
      head("Trust Reconciliation")
          + """
              <div class="param-summary">
                  <strong>Period end:</strong> <span th:text="${summary['periodEnd']}"></span>
                  <span th:if="${summary['status'] != null}" th:text="' · ' + ${summary['status']}"></span>
              </div>
              <p class="note" th:if="${summary['message'] != null}" th:text="${summary['message']}"></p>
              <table class="data">
                  <thead><tr><th>Section</th><th>Item</th><th class="text-right">Amount</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['section']}"></td><td th:text="${row['label']}"></td>
          """
          + cells("row", "amount")
          + """
                      </tr>
                  </tbody>
              </table>
          """
          + FOOT;

  private static final String INVESTMENT_REGISTER_TEMPLATE =
      head("Investment Register")
          + """
              <table class="data compact">
                  <thead><tr><th>Client</th><th>Institution</th><th>Account #</th><th>Investment account</th><th>Basis</th><th class="text-right">Principal</th><th class="text-right">Balance</th><th class="text-right">Gross interest</th><th class="text-right">Fund 5%</th><th class="text-right">Net interest</th><th>Status</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['clientName']}"></td><td th:text="${row['institution']}"></td><td th:text="${row['accountNumber']}"></td><td th:text="${row['investmentAccountName']}"></td><td th:text="${row['basisLabel']}"></td>
          """
          + cells("row", "principal", "balance", "grossInterest", "fundShare", "interestEarned")
          + """
                          <td th:text="${row['statusLabel']}"></td>
                      </tr>
                  </tbody>
                  <tfoot><tr><td colspan="5">Total</td>
          """
          + cells(
              "summary",
              "totalPrincipal",
              "totalBalance",
              "totalGrossInterest",
              "totalFundShare",
              "totalInterestEarned")
          + """
                  <td></td></tr></tfoot>
              </table>
          """
          + FOOT;

  private static final String TRUST_INTEREST_CHARGES_SCHEDULE_TEMPLATE =
      head("Interest and Charges Schedule (Rule 54.28)")
          + """
              <div class="param-summary">
                  <strong>Period:</strong> <span th:text="${summary['periodFrom']}"></span> to <span th:text="${summary['periodTo']}"></span>
              </div>
              <p class="note">In the cashbook and at source (memo) are separate figures and are never added together. The Fund's 5% paid at source and every memo figure never reached the Fund ledger.</p>
              <div th:each="block : ${summary['blocks']}">
                  <h2 th:text="${block['accountName'] + ' — ' + block['sectionLabel']}">Account</h2>
                  <table class="data compact">
                      <thead><tr><th>Month</th><th class="text-right">Fund ledger b/f</th><th class="text-right">Gross interest (cashbook)</th><th class="text-right">Gross interest (at source, memo)</th><th class="text-right">Client's share (s86(4))</th><th class="text-right">Fund 5% credited</th><th class="text-right">Fund 5% paid at source</th><th class="text-right">Charges recoverable</th><th class="text-right">Charges non-recoverable</th><th class="text-right">VAT in charges</th><th class="text-right">Charges (at source, memo)</th><th class="text-right">VAT (memo)</th><th class="text-right">Charges refunded</th><th class="text-right">Paid to the Fund</th><th class="text-right">Fund ledger c/f</th></tr></thead>
                      <tbody>
                          <tr th:each="row : ${rows}" th:if="${row['trustAccountId'] == block['trustAccountId']}">
                              <td th:text="${row['month']}"></td>
          """
          + cells(
              "row",
              "fundBroughtForward",
              "grossInterestCashbook",
              "grossInterestMemo",
              "clientShare",
              "fundShareCredited",
              "fundSharePaidAtSource",
              "chargesRecoverable",
              "chargesNonRecoverable",
              "chargesVat",
              "chargesMemo",
              "chargesMemoVat",
              "chargesRefunded",
              "paidToFund",
              "fundCarriedForward")
          + """
                          </tr>
                      </tbody>
                      <tfoot><tr><td>Period</td>
          """
          + cells(
              "block",
              "fundBroughtForward",
              "grossInterestCashbook",
              "grossInterestMemo",
              "clientShare",
              "fundShareCredited",
              "fundSharePaidAtSource",
              "chargesRecoverable",
              "chargesNonRecoverable",
              "chargesVat",
              "chargesMemo",
              "chargesMemoVat",
              "chargesRefunded",
              "paidToFund",
              "fundCarriedForward")
          + """
                      </tr></tfoot>
                  </table>
              </div>
          """
          + FOOT;

  private static final String UNIDENTIFIED_DEPOSITS_AGE_TEMPLATE =
      head("Unidentified Deposits Age Analysis")
          + """
              <div class="param-summary">
                  <strong>As at:</strong> <span th:text="${summary['asOfDate']}"></span>
              </div>
              <table class="data">
                  <thead><tr><th>Trust account</th><th>Received</th><th>Bank description</th><th class="text-right">Amount</th><th class="text-right">Age (days)</th><th>Age band</th><th>Position</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}">
                          <td th:text="${row['accountName']}"></td><td th:text="${row['receivedDate']}"></td><td th:text="${row['bankDescription']}"></td>
          """
          + cells("row", "amount")
          + """
                          <td class="text-right" th:text="${row['ageDays'] == null ? '—' : row['ageDays']}"></td><td th:text="${row['band']}"></td><td th:text="${row['position']}"></td>
                      </tr>
                  </tbody>
              </table>
              <h2>Open as at the date</h2>
              <table class="data">
                  <tbody>
          """
          + "            <tr><td>0–30 days</td><td class=\"text-right\" "
          + rand("summary['upToOneMonthTotal']")
          + "></td></tr>\n            <tr><td>31–90 days</td><td class=\"text-right\" "
          + rand("summary['oneToThreeMonthsTotal']")
          + "></td></tr>\n            <tr><td>91–180 days</td><td class=\"text-right\" "
          + rand("summary['threeToSixMonthsTotal']")
          + "></td></tr>\n            <tr><td>181–365 days</td><td class=\"text-right\" "
          + rand("summary['sixToTwelveMonthsTotal']")
          + "></td></tr>\n            <tr><td>Over one year</td><td class=\"text-right\" "
          + rand("summary['overOneYearTotal']")
          + "></td></tr>\n            <tr><td><strong>Total open</strong></td><td class=\"text-right\" "
          + rand("summary['openTotal']")
          + """
          ></td></tr>
                  </tbody>
              </table>
          """
          + FOOT;

  private static final String SECTION_35_TEMPLATE =
      head("Section 35 Data Pack")
              .replace(
                  "</style>",
                  "    .section-header { background: #f0f4f8; padding: 10px; margin: 20px 0 10px 0; font-weight: 700; font-size: 14px; border-left: 4px solid #1a1a2e; }\n    </style>")
          + """
              <div class="param-summary">
                  <strong>Financial Year:</strong>
                  <span th:text="${summary['financialYearStart']}"></span> to
                  <span th:text="${summary['financialYearEnd']}"></span>
              </div>
              <div th:each="section : ${summary['sections']}">
                  <div class="section-header" th:text="${section['sectionName']}">Section</div>
                  <p th:if="${section['note'] != null}" class="note" th:text="${section['note']}"></p>
                  <table class="data" th:if="${section['rowCount'] > 0}">
                      <thead><tr><th>Item</th><th class="text-right">Amount</th></tr></thead>
                      <tbody>
                          <tr th:each="row : ${rows}" th:if="${row['_section'] == section['sectionName']}" th:style="${row['_total'] != null} ? 'font-weight: 700;' : null">
                              <td th:text="${row['label']}"></td>
          """
          + cells("row", "amount")
          + """
                          </tr>
                      </tbody>
                  </table>
              </div>
          """
          + FOOT;
}
