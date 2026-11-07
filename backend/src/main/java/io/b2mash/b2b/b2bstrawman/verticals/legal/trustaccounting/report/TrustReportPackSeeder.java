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
 * Seeds the 7 Section 35 trust accounting report definitions for legal-za tenants (G88-F40, Phase
 * 89 630A). Called right after the standard report pack by {@code TenantProvisioningService} (fresh
 * tenants) and {@code PackReconciliationRunner} (every existing tenant, every boot). Gated on the
 * vertical profile only — the {@code trust_accounting} module flag is deliberately not consulted
 * (the trust reports page hides itself when the module is off; architecture §14.3.1).
 *
 * <p>ADR-356 (625E shape, mirrors {@code StandardReportPackSeeder}): the pack is Java-defined, so
 * its reconcile block is the {@link #RECONCILE} constant beside {@link #PACK_VERSION}. An applied
 * pack is reconciled only when its {@code pack_install} ledger row (type REPORT) records a lower
 * version. Empty at v1 — bump {@link #PACK_VERSION} and add entries when a shipped definition
 * changes ("fresh tenants only" is a review reject).
 */
@Service
public class TrustReportPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(TrustReportPackSeeder.class);
  static final String PACK_ID = "trust-reports";
  static final int PACK_VERSION = 2;

  /** ADR-356 reconcile entity of this pack's rows ({@code key} = the report slug). */
  static final String REPORT_ENTITY = "report_definition";

  /** The vertical profile this pack belongs to (LegalTariffSeeder precedent). */
  private static final String LEGAL_ZA_PROFILE = "legal-za";

  /** Slugs this pack ships, in install order. */
  static final List<String> SLUGS =
      List.of(
          "trust-receipts-payments",
          "client-trust-balances",
          "client-ledger-statement",
          "trust-reconciliation",
          "investment-register",
          "interest-allocation",
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
   */
  static final List<PackReconcileEntry> RECONCILE =
      List.of(
          entry(
              "section-35-data-pack",
              "templateBody",
              "sha256:80af07498e6ad52e27de1794c56998c501f05dc4f9a31a3807ddabd904392603",
              "sha256:6b70dd38153d35103916dfe479e40d2baa1d5647cf53b095e894011737d86c86"));

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

  /** Applies {@link #RECONCILE} under the ADR-356 one rule. */
  void reconcileExistingPack(String tenantId) {
    for (PackReconcileEntry entry : PackReconcileEntry.parse(PACK_ID, RECONCILE)) {
      entry.applyAndLog(resolveReconcileTarget(entry), log, tenantId, PACK_ID);
    }
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
      case "client-ledger-statement" -> clientLedgerStatementDefinition();
      case "trust-reconciliation" -> trustReconciliationDefinition();
      case "investment-register" -> investmentRegisterDefinition();
      case "interest-allocation" -> interestAllocationDefinition();
      case "section-35-data-pack" -> section35DataPackDefinition();
      default -> null;
    };
  }

  private static ReportDefinition trustReceiptsPaymentsDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
                Map.of("name", "dateFrom", "type", "date", "label", "From Date", "required", true),
                Map.of("name", "dateTo", "type", "date", "label", "To Date", "required", true)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "date", "label", "Date", "type", "date"),
                Map.of("key", "reference", "label", "Reference", "type", "string"),
                Map.of("key", "type", "label", "Type", "type", "string"),
                Map.of("key", "clientName", "label", "Client", "type", "string"),
                Map.of("key", "credit", "label", "Credit", "type", "currency", "format", "0.00"),
                Map.of("key", "debit", "label", "Debit", "type", "currency", "format", "0.00"),
                Map.of(
                    "key", "balance", "label", "Balance", "type", "currency", "format", "0.00")));

    var def =
        new ReportDefinition(
            "Trust Receipts & Payments",
            "trust-receipts-payments",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            TRUST_RECEIPTS_PAYMENTS_TEMPLATE);
    def.setDescription(
        "Chronological journal of all trust receipts and payments for a date range.");
    def.setSortOrder(10);
    return def;
  }

  private static ReportDefinition clientTrustBalancesDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
                Map.of(
                    "name", "asOfDate", "type", "date", "label", "As of Date", "required", true)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "clientName", "label", "Client Name", "type", "string"),
                Map.of("key", "balance", "label", "Balance", "type", "currency", "format", "0.00"),
                Map.of(
                    "key",
                    "totalDeposits",
                    "label",
                    "Total Deposits",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "totalPayments",
                    "label",
                    "Total Payments",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "totalFeeTransfers",
                    "label",
                    "Fee Transfers",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "totalInterestCredited",
                    "label",
                    "Interest",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of("key", "lastTransactionDate", "label", "Last Transaction", "type", "date")));

    var def =
        new ReportDefinition(
            "Client Trust Balances",
            "client-trust-balances",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            CLIENT_TRUST_BALANCES_TEMPLATE);
    def.setDescription("Point-in-time balances per client for a trust account.");
    def.setSortOrder(20);
    return def;
  }

  private static ReportDefinition clientLedgerStatementDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
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
                Map.of("name", "dateFrom", "type", "date", "label", "From Date", "required", true),
                Map.of("name", "dateTo", "type", "date", "label", "To Date", "required", true)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "date", "label", "Date", "type", "date"),
                Map.of("key", "reference", "label", "Reference", "type", "string"),
                Map.of("key", "type", "label", "Type", "type", "string"),
                Map.of("key", "description", "label", "Description", "type", "string"),
                Map.of("key", "debit", "label", "Debit", "type", "currency", "format", "0.00"),
                Map.of("key", "credit", "label", "Credit", "type", "currency", "format", "0.00"),
                Map.of(
                    "key",
                    "runningBalance",
                    "label",
                    "Running Balance",
                    "type",
                    "currency",
                    "format",
                    "0.00")));

    var def =
        new ReportDefinition(
            "Client Ledger Statement",
            "client-ledger-statement",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            CLIENT_LEDGER_STATEMENT_TEMPLATE);
    def.setDescription("Per-client transaction history with running balance.");
    def.setSortOrder(30);
    return def;
  }

  private static ReportDefinition trustReconciliationDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
                Map.of(
                    "name",
                    "reconciliation_id",
                    "type",
                    "uuid",
                    "label",
                    "Reconciliation",
                    "required",
                    false)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "section", "label", "Section", "type", "string"),
                Map.of("key", "label", "label", "Item", "type", "string"),
                Map.of("key", "amount", "label", "Amount", "type", "currency", "format", "0.00")));

    var def =
        new ReportDefinition(
            "Trust Reconciliation",
            "trust-reconciliation",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            TRUST_RECONCILIATION_TEMPLATE);
    def.setDescription("Three-way reconciliation: bank vs cashbook vs client ledger.");
    def.setSortOrder(40);
    return def;
  }

  private static ReportDefinition investmentRegisterDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
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
                    List.of("ACTIVE", "MATURED", "WITHDRAWN")),
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
                    List.of("FIRM_DISCRETION", "CLIENT_INSTRUCTION"))));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "clientName", "label", "Client", "type", "string"),
                Map.of("key", "institution", "label", "Institution", "type", "string"),
                Map.of("key", "accountNumber", "label", "Account #", "type", "string"),
                Map.of(
                    "key", "principal", "label", "Principal", "type", "currency", "format", "0.00"),
                Map.of(
                    "key",
                    "interestRate",
                    "label",
                    "Interest Rate",
                    "type",
                    "decimal",
                    "format",
                    "0.0000"),
                Map.of("key", "depositDate", "label", "Deposit Date", "type", "date"),
                Map.of("key", "maturityDate", "label", "Maturity Date", "type", "date"),
                Map.of(
                    "key",
                    "interestEarned",
                    "label",
                    "Interest Earned",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of("key", "status", "label", "Status", "type", "string"),
                Map.of("key", "investmentBasis", "label", "Basis", "type", "string"),
                Map.of("key", "applicableLpffRate", "label", "LPFF Rate", "type", "string")));

    var def =
        new ReportDefinition(
            "Investment Register",
            "investment-register",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            INVESTMENT_REGISTER_TEMPLATE);
    def.setDescription(
        "List of all trust investments with status, principal, and interest earned.");
    def.setSortOrder(50);
    return def;
  }

  private static ReportDefinition interestAllocationDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
                Map.of(
                    "name",
                    "interest_run_id",
                    "type",
                    "uuid",
                    "label",
                    "Interest Run",
                    "required",
                    true)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "clientName", "label", "Client Name", "type", "string"),
                Map.of(
                    "key",
                    "averageDailyBalance",
                    "label",
                    "Avg Daily Balance",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of("key", "daysInPeriod", "label", "Days", "type", "integer"),
                Map.of(
                    "key",
                    "grossInterest",
                    "label",
                    "Gross Interest",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "lpffShare",
                    "label",
                    "LPFF Share",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "clientShare",
                    "label",
                    "Client Share",
                    "type",
                    "currency",
                    "format",
                    "0.00"),
                Map.of("key", "rateSource", "label", "Rate Source", "type", "string")));

    var def =
        new ReportDefinition(
            "Interest Allocation",
            "interest-allocation",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            INTEREST_ALLOCATION_TEMPLATE);
    def.setDescription("Per-client interest allocation breakdown for a specific interest run.");
    def.setSortOrder(60);
    return def;
  }

  private static ReportDefinition section35DataPackDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of(
                    "name",
                    "trust_account_id",
                    "type",
                    "uuid",
                    "label",
                    "Trust Account",
                    "required",
                    true,
                    "entityType",
                    "trustAccount"),
                Map.of(
                    "name",
                    "financial_year_end",
                    "type",
                    "date",
                    "label",
                    "Financial Year End",
                    "required",
                    true)));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "_section", "label", "Section", "type", "string"),
                Map.of("key", "label", "label", "Item", "type", "string"),
                Map.of("key", "amount", "label", "Amount", "type", "currency", "format", "0.00")));

    var def =
        new ReportDefinition(
            "Section 35 Data Pack",
            "section-35-data-pack",
            "TRUST",
            parameterSchema,
            columnDefinitions,
            SECTION_35_TEMPLATE);
    def.setDescription(
        "Composite report combining all trust sub-reports for Section 35 compliance.");
    def.setSortOrder(70);
    return def;
  }

  // --- Thymeleaf Template Constants ---

  private static final String TRUST_REPORT_STYLE =
      """
              body { font-family: 'Helvetica Neue', Arial, sans-serif; font-size: 11px; color: #1a1a2e; margin: 0; padding: 20px; }
              .header { display: table; width: 100%; margin-bottom: 20px; border-bottom: 3px solid #1a1a2e; padding-bottom: 15px; }
              .header-left { display: table-cell; vertical-align: middle; }
              .header-right { display: table-cell; text-align: right; vertical-align: middle; }
              h1 { font-size: 20px; margin: 0 0 5px 0; }
              .param-summary { font-size: 10px; color: #666; margin-bottom: 20px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; }
              .summary-cards { display: table; width: 100%; margin-bottom: 20px; }
              .summary-card { display: table-cell; padding: 10px 15px; text-align: center; border: 1px solid #e2e8f0; }
              .summary-card .value { font-size: 18px; font-weight: 700; }
              .summary-card .label { font-size: 9px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
              table.data { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
              table.data th { padding: 8px 10px; text-align: left; font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; background-color: #1a1a2e; }
              table.data td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 11px; }
              table.data tr:nth-child(even) td { background: #f8f9fa; }
              table.data tfoot td { font-weight: 700; border-top: 2px solid #1a1a2e; padding-top: 10px; }
              .text-right { text-align: right; }
              .footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #e2e8f0; font-size: 9px; color: #999; display: table; width: 100%; }
              .footer-left { display: table-cell; }
              .footer-right { display: table-cell; text-align: right; }
      """;

  private static final String TRUST_RECEIPTS_PAYMENTS_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Trust Receipts &amp; Payments</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Trust Receipts &amp; Payments</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <div class="param-summary">
              <strong>Period:</strong> <span th:text="${parameters.dateFrom}"></span> to <span th:text="${parameters.dateTo}"></span>
          </div>
          <table class="data">
              <thead><tr><th>Date</th><th>Reference</th><th>Type</th><th>Client</th><th class="text-right">Credit</th><th class="text-right">Debit</th><th class="text-right">Balance</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['date']}"></td><td th:text="${row['reference']}"></td><td th:text="${row['type']}"></td><td th:text="${row['clientName']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['credit'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['debit'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['balance'], 1, 2)}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String CLIENT_TRUST_BALANCES_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Client Trust Balances</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Client Trust Balances</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <table class="data">
              <thead><tr><th>Client</th><th class="text-right">Balance</th><th class="text-right">Deposits</th><th class="text-right">Payments</th><th class="text-right">Fee Transfers</th><th class="text-right">Interest</th><th>Last Transaction</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['clientName']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['balance'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['totalDeposits'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['totalPayments'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['totalFeeTransfers'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['totalInterestCredited'], 1, 2)}"></td>
                      <td th:text="${row['lastTransactionDate']}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String CLIENT_LEDGER_STATEMENT_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Client Ledger Statement</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Client Ledger Statement</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <div class="param-summary">
              <strong>Period:</strong> <span th:text="${parameters.dateFrom}"></span> to <span th:text="${parameters.dateTo}"></span>
          </div>
          <table class="data">
              <thead><tr><th>Date</th><th>Reference</th><th>Type</th><th>Description</th><th class="text-right">Debit</th><th class="text-right">Credit</th><th class="text-right">Balance</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['date']}"></td><td th:text="${row['reference']}"></td><td th:text="${row['type']}"></td><td th:text="${row['description']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['debit'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['credit'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['runningBalance'], 1, 2)}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String TRUST_RECONCILIATION_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Trust Reconciliation</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Trust Reconciliation</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <table class="data">
              <thead><tr><th>Section</th><th>Item</th><th class="text-right">Amount</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['section']}"></td><td th:text="${row['label']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['amount'], 1, 2)}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String INVESTMENT_REGISTER_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Investment Register</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Investment Register</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <table class="data">
              <thead><tr><th>Client</th><th>Institution</th><th>Account #</th><th class="text-right">Principal</th><th class="text-right">Rate</th><th>Deposit</th><th>Maturity</th><th class="text-right">Interest</th><th>Status</th><th>Basis</th><th>LPFF Rate</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['clientName']}"></td><td th:text="${row['institution']}"></td><td th:text="${row['accountNumber']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['principal'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['interestRate'], 1, 4)}"></td>
                      <td th:text="${row['depositDate']}"></td><td th:text="${row['maturityDate']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['interestEarned'], 1, 2)}"></td>
                      <td th:text="${row['status']}"></td>
                      <td th:text="${row['investmentBasis']}"></td>
                      <td th:text="${row['applicableLpffRate']}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String INTEREST_ALLOCATION_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Interest Allocation</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Interest Allocation</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <table class="data">
              <thead><tr><th>Client</th><th class="text-right">Avg Daily Balance</th><th class="text-right">Days</th><th class="text-right">Gross Interest</th><th class="text-right">LPFF Share</th><th class="text-right">Client Share</th><th>Rate Source</th></tr></thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['clientName']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['averageDailyBalance'], 1, 2)}"></td>
                      <td class="text-right" th:text="${row['daysInPeriod']}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['grossInterest'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['lpffShare'], 1, 2)}"></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['clientShare'], 1, 2)}"></td>
                      <td th:text="${row['rateSource']}"></td>
                  </tr>
              </tbody>
          </table>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;

  private static final String SECTION_35_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Section 35 Data Pack</title>
          <style>"""
          + TRUST_REPORT_STYLE
          + """
              .section-header { background: #f0f4f8; padding: 10px; margin: 20px 0 10px 0; font-weight: 700; font-size: 14px; border-left: 4px solid #1a1a2e; }
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left"><h1 th:text="${report.name}">Section 35 Data Pack</h1></div>
              <div class="header-right"><div th:text="'Generated: ' + ${generatedAt}" style="font-size: 10px; color: #666;"></div></div>
          </div>
          <div class="param-summary">
              <strong>Financial Year:</strong>
              <span th:text="${summary['financialYearStart']}"></span> to
              <span th:text="${summary['financialYearEnd']}"></span>
          </div>
          <div th:each="section : ${summary['sections']}">
              <div class="section-header" th:text="${section['sectionName']}">Section</div>
              <p th:if="${section['note'] != null}" style="font-size: 10px; color: #666;" th:text="${section['note']}"></p>
              <table class="data" th:if="${section['rowCount'] > 0}">
                  <thead><tr><th>Item</th><th class="text-right">Amount</th></tr></thead>
                  <tbody>
                      <tr th:each="row : ${rows}" th:if="${row['_section'] == section['sectionName']}" th:style="${row['_total'] != null} ? 'font-weight: 700;' : null">
                          <td th:text="${row['label']}"></td>
                          <td class="text-right" th:text="${#numbers.formatDecimal(row['amount'], 1, 2)}"></td>
                      </tr>
                  </tbody>
              </table>
          </div>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;
}
