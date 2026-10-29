package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
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
 * Seeds standard report definitions (Timesheet, Fee Note Aging, Matter Profitability) for newly
 * provisioned tenants. Idempotent: tracks applied packs in OrgSettings.reportPackStatus.
 *
 * <p>ADR-356: the pack is Java-defined, so its reconcile block is the {@link #RECONCILE} constant
 * beside {@link #PACK_VERSION}. An applied pack is reconciled only when its {@code pack_install}
 * ledger row (type REPORT, backfilled from OrgSettings for pre-625E tenants) records a lower
 * version.
 */
@Service
public class StandardReportPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(StandardReportPackSeeder.class);
  static final String PACK_ID = "standard-reports";
  static final int PACK_VERSION = 2;

  /** ADR-356 reconcile entity of this pack's rows ({@code key} = the report slug). */
  static final String REPORT_ENTITY = "report_definition";

  /**
   * ADR-356 reconcile block of the Java-defined standard-reports pack. Bump {@link #PACK_VERSION}
   * with every entry added here; entries are cumulative. {@code name} / {@code description} are
   * strings; {@code parameterSchema} / {@code columnDefinitions} / {@code templateBody} are
   * whole-value {@code sha256:} + {@link ContentHashUtil#hashOf} of the previously shipped ({@code
   * from}) and the shipped ({@code to}) value — {@code StandardReportPackSeederReconcileTest} pins
   * every {@code from} to the v1 factories and every {@code to} to the factories below.
   *
   * <p>v2 (628C, U21 terminology): matter / client / fee-note labels, names and PDF headers; the
   * timesheet {@code groupBy} parameter gains {@code optionLabels} (value {@code project} kept).
   */
  static final List<PackReconcileEntry> RECONCILE =
      List.of(
          entry(
              "timesheet",
              "description",
              "Time entries grouped by member, project, or date for a given period.",
              "Time entries grouped by member, matter, or date for a given period."),
          entry(
              "timesheet",
              "parameterSchema",
              "sha256:d5e3799ebae394b3694c29ea8fd330d4e54b748ef76a45069132584a28f32008",
              "sha256:0fb643c08406a8f4b1db8e8de694f78035aa452cfb55cb930c42590931f51af9"),
          entry(
              "timesheet",
              "templateBody",
              "sha256:4ce6d868d0444e0cceb1184237418cb39bec62684ef411a3eedcf7a655249990",
              "sha256:7a3d37b92415ff19dd76206f45a57ad1b0b7ef9caf53864c39fa09895a7954c5"),
          entry("invoice-aging", "name", "Invoice Aging Report", "Fee Note Aging Report"),
          entry(
              "invoice-aging",
              "description",
              "Outstanding invoices grouped by age bucket.",
              "Outstanding fee notes grouped by age bucket."),
          entry(
              "invoice-aging",
              "parameterSchema",
              "sha256:b6e7b0974c6ab99ac74d9f2a9f44776fa06fb32d4227539ee4b521620fe4fd80",
              "sha256:e12302f4caf43a0757a893a74e6d1003ea12e9caab08615a7feac13bc86aade0"),
          entry(
              "invoice-aging",
              "columnDefinitions",
              "sha256:ab3808f9233bb7dc7e7692eea60a5d3732063514d8b8175e6e0ed9e391c4b23f",
              "sha256:d47a58ca240ad84e054a9b5737b61c8d1b203777b6eb29268a8cf1128f47d042"),
          entry(
              "invoice-aging",
              "templateBody",
              "sha256:b10155e9b05a7b3e9fa4640d5c2f07526b199dfcb9a6621a35dd4066353a447a",
              "sha256:2487e9881b1f4e5dbb82f050ba7f431a1fd4da3393ffb492b4d5aee7c343abe3"),
          entry(
              "project-profitability",
              "name",
              "Project Profitability Report",
              "Matter Profitability Report"),
          entry(
              "project-profitability",
              "description",
              "Revenue vs. cost per project for a date range.",
              "Revenue vs. cost per matter for a date range."),
          entry(
              "project-profitability",
              "parameterSchema",
              "sha256:3a8ca010e1f415431ea573ae8ea2e36231dfd1b6b60fc01acaa5187ce821441d",
              "sha256:4a6b462230f217434fc2cfb347ce588b7fae27e3d681b56f9b64632a3387c7a7"),
          entry(
              "project-profitability",
              "columnDefinitions",
              "sha256:25c80d187b4249869f5cc89b7e7961e7c38d3b0ece173fd86fd1e0400b2970df",
              "sha256:f2acb668c409e61e7b8608ac1d0d58e36b7cce4b03c40821778025290c1c95cb"),
          entry(
              "project-profitability",
              "templateBody",
              "sha256:7ba7d32fb1b72e8c38cb5de25ccaa7c45254ef007ddf9c37fef235da1acafad5",
              "sha256:8a53b04d29e916c9d2562087ea4cccf1a467a4b841d28101ae30617a56541b84"));

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;
  private final PackVersionGate packVersionGate;

  public StandardReportPackSeeder(
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
    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    if (isPackAlreadyApplied(settings)) {
      reconcileAppliedPack(settings, tenantId);
      return;
    }

    upsertReport(timesheetDefinition());
    upsertReport(invoiceAgingDefinition());
    upsertReport(projectProfitabilityDefinition());

    settings.getPackStatus().recordReportPackApplication(PACK_ID, PACK_VERSION);
    orgSettingsRepository.save(settings);
    packVersionGate.recordIfAbsent(PackType.REPORT, PACK_ID, String.valueOf(PACK_VERSION), PACK_ID);

    log.info("Applied report pack {} v{} for tenant {}", PACK_ID, PACK_VERSION, tenantId);
  }

  private void reconcileAppliedPack(OrgSettings settings, String tenantId) {
    // OQ #14 backfill (pre-625E tenants record the version they applied) + version gate.
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
   * ADR-356 resolver (628C): the {@value #REPORT_ENTITY} row an entry targets — {@code key} is the
   * slug of a report this pack ships, ownership is the slug + {@code is_system = true} (report
   * definitions carry no pack column; there is no firm edit path today, the one rule still
   * applies). Fields: {@code name} / {@code description} (string {@code to} equal to the shipped
   * value) and {@code parameterSchema} / {@code columnDefinitions} / {@code templateBody} ({@code
   * sha256:} {@code to} equal to the hash of the shipped value; the target ignores the hash string
   * and writes the shipped value through the managed entity). Unsupported entries are WARN-logged
   * and skipped — nothing here throws at boot.
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
              existing.updateTemplate(definition.getTemplateBody());
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
      case "timesheet" -> timesheetDefinition();
      case "invoice-aging" -> invoiceAgingDefinition();
      case "project-profitability" -> projectProfitabilityDefinition();
      default -> null;
    };
  }

  private static ReportDefinition timesheetDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of("name", "dateFrom", "type", "date", "label", "From Date", "required", true),
                Map.of("name", "dateTo", "type", "date", "label", "To Date", "required", true),
                Map.of(
                    "name",
                    "groupBy",
                    "type",
                    "enum",
                    "label",
                    "Group By",
                    "options",
                    List.of("member", "project", "date"),
                    "optionLabels",
                    Map.of("member", "Member", "project", "Matter", "date", "Date"),
                    "default",
                    "member"),
                Map.of(
                    "name",
                    "projectId",
                    "type",
                    "uuid",
                    "label",
                    "Matter",
                    "required",
                    false,
                    "entityType",
                    "project"),
                Map.of(
                    "name",
                    "memberId",
                    "type",
                    "uuid",
                    "label",
                    "Member",
                    "required",
                    false,
                    "entityType",
                    "member")));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "groupLabel", "label", "Group", "type", "string"),
                Map.of(
                    "key",
                    "totalHours",
                    "label",
                    "Total Hours",
                    "type",
                    "decimal",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "billableHours",
                    "label",
                    "Billable Hours",
                    "type",
                    "decimal",
                    "format",
                    "0.00"),
                Map.of(
                    "key",
                    "nonBillableHours",
                    "label",
                    "Non-Billable",
                    "type",
                    "decimal",
                    "format",
                    "0.00"),
                Map.of("key", "entryCount", "label", "Entries", "type", "integer")));

    var def =
        new ReportDefinition(
            "Timesheet Report",
            "timesheet",
            "TIME_ATTENDANCE",
            parameterSchema,
            columnDefinitions,
            TIMESHEET_TEMPLATE);
    def.setDescription("Time entries grouped by member, matter, or date for a given period.");
    def.setSortOrder(10);
    return def;
  }

  private static ReportDefinition invoiceAgingDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of("name", "asOfDate", "type", "date", "label", "As of Date", "required", true),
                Map.of(
                    "name",
                    "customerId",
                    "type",
                    "uuid",
                    "label",
                    "Client",
                    "required",
                    false,
                    "entityType",
                    "customer")));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "invoiceNumber", "label", "Fee Note #", "type", "string"),
                Map.of("key", "customerName", "label", "Client", "type", "string"),
                Map.of("key", "issueDate", "label", "Issue Date", "type", "date"),
                Map.of("key", "dueDate", "label", "Due Date", "type", "date"),
                Map.of("key", "amount", "label", "Amount", "type", "currency", "format", "0.00"),
                Map.of("key", "currency", "label", "Currency", "type", "string"),
                Map.of("key", "daysOverdue", "label", "Days Overdue", "type", "integer"),
                Map.of("key", "ageBucketLabel", "label", "Bucket", "type", "string")));

    var def =
        new ReportDefinition(
            "Fee Note Aging Report",
            "invoice-aging",
            "FINANCIAL",
            parameterSchema,
            columnDefinitions,
            INVOICE_AGING_TEMPLATE);
    def.setDescription("Outstanding fee notes grouped by age bucket.");
    def.setSortOrder(10);
    return def;
  }

  private static ReportDefinition projectProfitabilityDefinition() {
    var parameterSchema =
        Map.<String, Object>of(
            "parameters",
            List.of(
                Map.of("name", "dateFrom", "type", "date", "label", "From Date", "required", true),
                Map.of("name", "dateTo", "type", "date", "label", "To Date", "required", true),
                Map.of(
                    "name",
                    "projectId",
                    "type",
                    "uuid",
                    "label",
                    "Matter",
                    "required",
                    false,
                    "entityType",
                    "project"),
                Map.of(
                    "name",
                    "customerId",
                    "type",
                    "uuid",
                    "label",
                    "Client",
                    "required",
                    false,
                    "entityType",
                    "customer")));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "projectName", "label", "Matter", "type", "string"),
                Map.of("key", "customerName", "label", "Client", "type", "string"),
                Map.of("key", "currency", "label", "Currency", "type", "string"),
                Map.of(
                    "key",
                    "billableHours",
                    "label",
                    "Billable Hrs",
                    "type",
                    "decimal",
                    "format",
                    "0.00"),
                Map.of("key", "revenue", "label", "Revenue", "type", "currency", "format", "0.00"),
                Map.of("key", "cost", "label", "Cost", "type", "currency", "format", "0.00"),
                Map.of("key", "margin", "label", "Margin", "type", "currency", "format", "0.00"),
                Map.of(
                    "key",
                    "marginPercent",
                    "label",
                    "Margin %",
                    "type",
                    "decimal",
                    "format",
                    "0.0")));

    var def =
        new ReportDefinition(
            "Matter Profitability Report",
            "project-profitability",
            "PROJECT",
            parameterSchema,
            columnDefinitions,
            PROJECT_PROFITABILITY_TEMPLATE);
    def.setDescription("Revenue vs. cost per matter for a date range.");
    def.setSortOrder(10);
    return def;
  }

  // --- Thymeleaf Template Constants ---

  private static final String TIMESHEET_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Timesheet Report</title>
          <style>
              body { font-family: 'Helvetica Neue', Arial, sans-serif; font-size: 11px; color: #1a1a2e; margin: 0; padding: 20px; }
              .header { display: table; width: 100%; margin-bottom: 20px; border-bottom: 3px solid; padding-bottom: 15px; }
              .header-left { display: table-cell; vertical-align: middle; }
              .header-right { display: table-cell; text-align: right; vertical-align: middle; }
              .header img.logo { max-height: 50px; max-width: 200px; }
              h1 { font-size: 20px; margin: 0 0 5px 0; }
              .param-summary { font-size: 10px; color: #666; margin-bottom: 20px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; }
              .summary-cards { display: table; width: 100%; margin-bottom: 20px; }
              .summary-card { display: table-cell; padding: 10px 15px; text-align: center; border: 1px solid #e2e8f0; }
              .summary-card .value { font-size: 18px; font-weight: 700; }
              .summary-card .label { font-size: 9px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
              table.data { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
              table.data th { padding: 8px 10px; text-align: left; font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; }
              table.data td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 11px; }
              table.data tr:nth-child(even) td { background: #f8f9fa; }
              table.data tfoot td { font-weight: 700; border-top: 2px solid #1a1a2e; padding-top: 10px; }
              .footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #e2e8f0; font-size: 9px; color: #999; display: table; width: 100%; }
              .footer-left { display: table-cell; }
              .footer-right { display: table-cell; text-align: right; }
              .text-right { text-align: right; }
          </style>
          <style th:if="${branding != null and branding.brandColor != ''}"
                 th:inline="text">
              .header { border-bottom-color: [[${branding.brandColor}]]; }
              table.data th { background-color: [[${branding.brandColor}]]; }
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left">
                  <img th:if="${branding != null and branding.logoS3Key != ''}"
                       th:src="${branding.logoS3Key}" class="logo" alt="Logo"/>
                  <h1 th:text="${report.name}">Timesheet Report</h1>
              </div>
              <div class="header-right">
                  <div th:text="'Generated: ' + ${generatedAt}"
                       style="font-size: 10px; color: #666;">Generated: 21 Feb 2026 10:30</div>
              </div>
          </div>

          <div class="param-summary">
              <strong>Period:</strong> <span th:text="${parameters.dateFrom}">2026-01-01</span>
              to <span th:text="${parameters.dateTo}">2026-01-31</span>
              <span th:if="${parameters.groupBy != null}">
                  | <strong>Grouped by:</strong> <span th:text="${parameters.groupBy == 'project'} ? 'matter' : ${parameters.groupBy}">member</span>
              </span>
          </div>

          <div class="summary-cards" th:if="${summary != null}">
              <div class="summary-card">
                  <div class="value" th:text="${#numbers.formatDecimal(summary.totalHours, 1, 2)}">480.00</div>
                  <div class="label">Total Hours</div>
              </div>
              <div class="summary-card">
                  <div class="value" th:text="${#numbers.formatDecimal(summary.billableHours, 1, 2)}">410.00</div>
                  <div class="label">Billable Hours</div>
              </div>
              <div class="summary-card">
                  <div class="value" th:text="${#numbers.formatDecimal(summary.nonBillableHours, 1, 2)}">70.00</div>
                  <div class="label">Non-Billable Hours</div>
              </div>
              <div class="summary-card" th:if="${summary.entryCount != null}">
                  <div class="value" th:text="${summary.entryCount}">126</div>
                  <div class="label">Entries</div>
              </div>
          </div>

          <table class="data">
              <thead>
                  <tr>
                      <th th:each="col : ${columns}" th:text="${col.label}"
                          th:classappend="${col.type == 'decimal' or col.type == 'integer'} ? 'text-right' : ''">Column</th>
                  </tr>
              </thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:each="col : ${columns}"
                          th:classappend="${col.type == 'decimal' or col.type == 'integer'} ? 'text-right' : ''">
                          <span th:if="${col.type == 'decimal'}"
                                th:text="${row[col.key] != null} ? ${#numbers.formatDecimal(row[col.key], 1, 2)} : '-'">0.00</span>
                          <span th:if="${col.type == 'integer'}"
                                th:text="${row[col.key] != null} ? ${row[col.key]} : '-'">0</span>
                          <span th:if="${col.type == 'string' or col.type == 'date'}"
                                th:text="${row[col.key] != null} ? ${row[col.key]} : '-'">Value</span>
                      </td>
                  </tr>
              </tbody>
              <tfoot th:if="${summary != null}">
                  <tr>
                      <td><strong>Total</strong></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.totalHours, 1, 2)}">480.00</td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.billableHours, 1, 2)}">410.00</td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.nonBillableHours, 1, 2)}">70.00</td>
                      <td class="text-right" th:if="${summary.entryCount != null}" th:text="${summary.entryCount}">126</td>
                  </tr>
              </tfoot>
          </table>

          <div class="footer">
              <div class="footer-left" th:if="${branding != null and branding.footerText != ''}"
                   th:text="${branding.footerText}">Company Footer</div>
              <div class="footer-right"
                   th:text="'Generated ' + ${generatedAt}">
                  Generated 21 Feb 2026 10:30
              </div>
          </div>
      </body>
      </html>
      """;

  private static final String INVOICE_AGING_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Fee Note Aging Report</title>
          <style>
              body { font-family: 'Helvetica Neue', Arial, sans-serif; font-size: 11px; color: #1a1a2e; margin: 0; padding: 20px; }
              .header { display: table; width: 100%; margin-bottom: 20px; border-bottom: 3px solid; padding-bottom: 15px; }
              .header-left { display: table-cell; vertical-align: middle; }
              .header-right { display: table-cell; text-align: right; vertical-align: middle; }
              .header img.logo { max-height: 50px; max-width: 200px; }
              h1 { font-size: 20px; margin: 0 0 5px 0; }
              .param-summary { font-size: 10px; color: #666; margin-bottom: 20px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; }
              .bucket-summary { display: table; width: 100%; margin-bottom: 20px; }
              .bucket { display: table-cell; padding: 10px 15px; text-align: center; border: 1px solid #e2e8f0; }
              .bucket .amount { font-size: 16px; font-weight: 700; }
              .bucket .label { font-size: 9px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
              .bucket .count { font-size: 10px; color: #999; }
              .bucket.overdue .amount { color: #dc2626; }
              table.data { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
              table.data th { padding: 8px 10px; text-align: left; font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; }
              table.data td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 11px; }
              table.data tr:nth-child(even) td { background: #f8f9fa; }
              table.data tfoot td { font-weight: 700; border-top: 2px solid #1a1a2e; padding-top: 10px; }
              .text-right { text-align: right; }
              .status-overdue { color: #dc2626; font-weight: 600; }
              .status-current { color: #16a34a; }
              .footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #e2e8f0; font-size: 9px; color: #999; display: table; width: 100%; }
              .footer-left { display: table-cell; }
              .footer-right { display: table-cell; text-align: right; }
          </style>
          <style th:if="${branding != null and branding.brandColor != ''}"
                 th:inline="text">
              .header { border-bottom-color: [[${branding.brandColor}]]; }
              table.data th { background-color: [[${branding.brandColor}]]; }
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left">
                  <img th:if="${branding != null and branding.logoS3Key != ''}"
                       th:src="${branding.logoS3Key}" class="logo" alt="Logo"/>
                  <h1 th:text="${report.name}">Fee Note Aging Report</h1>
              </div>
              <div class="header-right">
                  <div th:text="'Generated: ' + ${generatedAt}"
                       style="font-size: 10px; color: #666;">Generated: 21 Feb 2026 10:30</div>
              </div>
          </div>

          <div class="param-summary">
              <strong>As of:</strong> <span th:text="${parameters.asOfDate}">2026-02-21</span>
              <span th:if="${parameters.customerId != null}">
                  | <strong>Client:</strong> <span th:text="${parameters.customerName}">Acme Corp</span>
              </span>
          </div>

          <div class="bucket-summary" th:if="${summary != null}">
              <div class="bucket">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['currentAmount'], 1, 2)}">0.00</div>
                  <div class="label">Current</div>
                  <div class="count" th:text="${summary['currentCount']} + ' fee notes'">0 fee notes</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket1_30Amount'], 1, 2)}">0.00</div>
                  <div class="label">1-30 Days</div>
                  <div class="count" th:text="${summary['bucket1_30Count']} + ' fee notes'">0 fee notes</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket31_60Amount'], 1, 2)}">0.00</div>
                  <div class="label">31-60 Days</div>
                  <div class="count" th:text="${summary['bucket31_60Count']} + ' fee notes'">0 fee notes</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket61_90Amount'], 1, 2)}">0.00</div>
                  <div class="label">61-90 Days</div>
                  <div class="count" th:text="${summary['bucket61_90Count']} + ' fee notes'">0 fee notes</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket90PlusAmount'], 1, 2)}">0.00</div>
                  <div class="label">90+ Days</div>
                  <div class="count" th:text="${summary['bucket90PlusCount']} + ' fee notes'">0 fee notes</div>
              </div>
          </div>

          <table class="data">
              <thead>
                  <tr>
                      <th>Fee Note #</th>
                      <th>Client</th>
                      <th>Issue Date</th>
                      <th>Due Date</th>
                      <th class="text-right">Amount</th>
                      <th>Currency</th>
                      <th class="text-right">Days Overdue</th>
                      <th>Bucket</th>
                  </tr>
              </thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row['invoiceNumber']}">INV-001</td>
                      <td th:text="${row['customerName']}">Acme Corp</td>
                      <td th:text="${row['issueDate']}">2026-01-01</td>
                      <td th:text="${row['dueDate']}">2026-01-31</td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(row['amount'], 1, 2)}">1000.00</td>
                      <td th:text="${row['currency']}">USD</td>
                      <td class="text-right"
                          th:classappend="${row['daysOverdue'] > 0} ? 'status-overdue' : 'status-current'"
                          th:text="${row['daysOverdue']}">0</td>
                      <td th:text="${row['ageBucketLabel']}">Current</td>
                  </tr>
              </tbody>
              <tfoot th:if="${summary != null}">
                  <tr>
                      <td colspan="4"><strong>Total Outstanding</strong></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary['totalAmount'], 1, 2)}">0.00</td>
                      <td colspan="3"></td>
                  </tr>
              </tfoot>
          </table>

          <div class="footer">
              <div class="footer-left" th:if="${branding != null and branding.footerText != ''}"
                   th:text="${branding.footerText}">Company Footer</div>
              <div class="footer-right"
                   th:text="'Generated ' + ${generatedAt}">
                  Generated 21 Feb 2026 10:30
              </div>
          </div>
      </body>
      </html>
      """;

  private static final String PROJECT_PROFITABILITY_TEMPLATE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
      <head>
          <meta charset="UTF-8"/>
          <title th:text="${report.name}">Matter Profitability Report</title>
          <style>
              body { font-family: 'Helvetica Neue', Arial, sans-serif; font-size: 11px; color: #1a1a2e; margin: 0; padding: 20px; }
              .header { display: table; width: 100%; margin-bottom: 20px; border-bottom: 3px solid; padding-bottom: 15px; }
              .header-left { display: table-cell; vertical-align: middle; }
              .header-right { display: table-cell; text-align: right; vertical-align: middle; }
              .header img.logo { max-height: 50px; max-width: 200px; }
              h1 { font-size: 20px; margin: 0 0 5px 0; }
              .param-summary { font-size: 10px; color: #666; margin-bottom: 20px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; }
              .summary-cards { display: table; width: 100%; margin-bottom: 20px; }
              .summary-card { display: table-cell; padding: 10px 15px; text-align: center; border: 1px solid #e2e8f0; }
              .summary-card .value { font-size: 16px; font-weight: 700; }
              .summary-card .label { font-size: 9px; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
              .margin-positive { color: #16a34a; }
              .margin-negative { color: #dc2626; }
              table.data { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
              table.data th { padding: 8px 10px; text-align: left; font-size: 10px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #fff; }
              table.data td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 11px; }
              table.data tr:nth-child(even) td { background: #f8f9fa; }
              table.data tfoot td { font-weight: 700; border-top: 2px solid #1a1a2e; padding-top: 10px; }
              .text-right { text-align: right; }
              .footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid #e2e8f0; font-size: 9px; color: #999; display: table; width: 100%; }
              .footer-left { display: table-cell; }
              .footer-right { display: table-cell; text-align: right; }
          </style>
          <style th:if="${branding != null and branding.brandColor != ''}"
                 th:inline="text">
              .header { border-bottom-color: [[${branding.brandColor}]]; }
              table.data th { background-color: [[${branding.brandColor}]]; }
          </style>
      </head>
      <body>
          <div class="header">
              <div class="header-left">
                  <img th:if="${branding != null and branding.logoS3Key != ''}"
                       th:src="${branding.logoS3Key}" class="logo" alt="Logo"/>
                  <h1 th:text="${report.name}">Matter Profitability Report</h1>
              </div>
              <div class="header-right">
                  <div th:text="'Generated: ' + ${generatedAt}"
                       style="font-size: 10px; color: #666;">Generated: 21 Feb 2026 10:30</div>
              </div>
          </div>

          <div class="param-summary">
              <strong>Period:</strong> <span th:text="${parameters.dateFrom}">2026-01-01</span>
              to <span th:text="${parameters.dateTo}">2026-01-31</span>
              <span th:if="${parameters.customerId != null}">
                  | <strong>Client:</strong> <span th:text="${parameters.customerName}">Acme Corp</span>
              </span>
          </div>

          <div class="summary-cards" th:if="${summary != null}">
              <div class="summary-card">
                  <div class="value" th:text="${#numbers.formatDecimal(summary.totalRevenue, 1, 2)}">0.00</div>
                  <div class="label">Total Revenue</div>
              </div>
              <div class="summary-card">
                  <div class="value" th:text="${#numbers.formatDecimal(summary.totalCost, 1, 2)}">0.00</div>
                  <div class="label">Total Cost</div>
              </div>
              <div class="summary-card">
                  <div class="value"
                       th:classappend="${summary.totalMargin >= 0} ? 'margin-positive' : 'margin-negative'"
                       th:text="${#numbers.formatDecimal(summary.totalMargin, 1, 2)}">0.00</div>
                  <div class="label">Total Margin</div>
              </div>
              <div class="summary-card">
                  <div class="value"
                       th:classappend="${summary.overallMarginPercent >= 0} ? 'margin-positive' : 'margin-negative'"
                       th:text="${#numbers.formatDecimal(summary.overallMarginPercent, 1, 1)} + '%'">0.0%</div>
                  <div class="label">Overall Margin %</div>
              </div>
          </div>

          <table class="data">
              <thead>
                  <tr>
                      <th>Matter</th>
                      <th>Client</th>
                      <th>Currency</th>
                      <th class="text-right">Billable Hrs</th>
                      <th class="text-right">Revenue</th>
                      <th class="text-right">Cost</th>
                      <th class="text-right">Margin</th>
                      <th class="text-right">Margin %</th>
                  </tr>
              </thead>
              <tbody>
                  <tr th:each="row : ${rows}">
                      <td th:text="${row.projectName}">Matter Alpha</td>
                      <td th:text="${row.customerName != null} ? ${row.customerName} : '-'">Acme Corp</td>
                      <td th:text="${row.currency}">USD</td>
                      <td class="text-right" th:text="${row.billableHours != null} ? ${#numbers.formatDecimal(row.billableHours, 1, 2)} : '-'">0.00</td>
                      <td class="text-right" th:text="${row.revenue != null} ? ${#numbers.formatDecimal(row.revenue, 1, 2)} : '-'">0.00</td>
                      <td class="text-right" th:text="${row.cost != null} ? ${#numbers.formatDecimal(row.cost, 1, 2)} : '-'">0.00</td>
                      <td class="text-right"
                          th:classappend="${row.margin != null and row.margin >= 0} ? 'margin-positive' : 'margin-negative'"
                          th:text="${row.margin != null} ? ${#numbers.formatDecimal(row.margin, 1, 2)} : '-'">0.00</td>
                      <td class="text-right"
                          th:classappend="${row.marginPercent != null and row.marginPercent >= 0} ? 'margin-positive' : 'margin-negative'"
                          th:text="${row.marginPercent != null} ? ${#numbers.formatDecimal(row.marginPercent, 1, 1)} + '%' : '-'">0.0%</td>
                  </tr>
              </tbody>
              <tfoot th:if="${summary != null}">
                  <tr>
                      <td colspan="3"><strong>Total</strong></td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.totalBillableHours, 1, 2)}">0.00</td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.totalRevenue, 1, 2)}">0.00</td>
                      <td class="text-right" th:text="${#numbers.formatDecimal(summary.totalCost, 1, 2)}">0.00</td>
                      <td class="text-right"
                          th:classappend="${summary.totalMargin >= 0} ? 'margin-positive' : 'margin-negative'"
                          th:text="${#numbers.formatDecimal(summary.totalMargin, 1, 2)}">0.00</td>
                      <td class="text-right"
                          th:classappend="${summary.overallMarginPercent >= 0} ? 'margin-positive' : 'margin-negative'"
                          th:text="${#numbers.formatDecimal(summary.overallMarginPercent, 1, 1)} + '%'">0.0%</td>
                  </tr>
              </tfoot>
          </table>

          <div class="footer">
              <div class="footer-left" th:if="${branding != null and branding.footerText != ''}"
                   th:text="${branding.footerText}">Company Footer</div>
              <div class="footer-right"
                   th:text="'Generated ' + ${generatedAt}">
                  Generated 21 Feb 2026 10:30
              </div>
          </div>
      </body>
      </html>
      """;
}
