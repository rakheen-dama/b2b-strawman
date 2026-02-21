package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds standard report definitions (Timesheet, Invoice Aging, Project Profitability) for newly
 * provisioned tenants. Idempotent: tracks applied packs in OrgSettings.reportPackStatus.
 */
@Service
public class StandardReportPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(StandardReportPackSeeder.class);
  static final String PACK_ID = "standard-reports";
  static final int PACK_VERSION = 1;

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public StandardReportPackSeeder(
      ReportDefinitionRepository reportDefinitionRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public void seedForTenant(String tenantId, String orgId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .run(() -> transactionTemplate.executeWithoutResult(tx -> doSeed(tenantId)));
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
      log.info("Report pack {} already applied for tenant {}, skipping", PACK_ID, tenantId);
      return;
    }

    upsertReport(timesheetDefinition());
    upsertReport(invoiceAgingDefinition());
    upsertReport(projectProfitabilityDefinition());

    settings.recordReportPackApplication(PACK_ID, PACK_VERSION);
    orgSettingsRepository.save(settings);

    log.info("Applied report pack {} v{} for tenant {}", PACK_ID, PACK_VERSION, tenantId);
  }

  private boolean isPackAlreadyApplied(OrgSettings settings) {
    if (settings.getReportPackStatus() == null) {
      return false;
    }
    return settings.getReportPackStatus().stream()
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

  private ReportDefinition timesheetDefinition() {
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
                    "default",
                    "member"),
                Map.of(
                    "name",
                    "projectId",
                    "type",
                    "uuid",
                    "label",
                    "Project",
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
    def.setDescription("Time entries grouped by member, project, or date for a given period.");
    def.setSortOrder(10);
    return def;
  }

  private ReportDefinition invoiceAgingDefinition() {
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
                    "Customer",
                    "required",
                    false,
                    "entityType",
                    "customer")));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "invoiceNumber", "label", "Invoice #", "type", "string"),
                Map.of("key", "customerName", "label", "Customer", "type", "string"),
                Map.of("key", "issueDate", "label", "Issue Date", "type", "date"),
                Map.of("key", "dueDate", "label", "Due Date", "type", "date"),
                Map.of("key", "amount", "label", "Amount", "type", "currency", "format", "0.00"),
                Map.of("key", "currency", "label", "Currency", "type", "string"),
                Map.of("key", "daysOverdue", "label", "Days Overdue", "type", "integer"),
                Map.of("key", "ageBucketLabel", "label", "Bucket", "type", "string")));

    var def =
        new ReportDefinition(
            "Invoice Aging Report",
            "invoice-aging",
            "FINANCIAL",
            parameterSchema,
            columnDefinitions,
            INVOICE_AGING_TEMPLATE);
    def.setDescription("Outstanding invoices grouped by age bucket.");
    def.setSortOrder(10);
    return def;
  }

  private ReportDefinition projectProfitabilityDefinition() {
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
                    "Project",
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
                    "Customer",
                    "required",
                    false,
                    "entityType",
                    "customer")));

    var columnDefinitions =
        Map.<String, Object>of(
            "columns",
            List.of(
                Map.of("key", "projectName", "label", "Project", "type", "string"),
                Map.of("key", "customerName", "label", "Customer", "type", "string"),
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
            "Project Profitability Report",
            "project-profitability",
            "PROJECT",
            parameterSchema,
            columnDefinitions,
            PROJECT_PROFITABILITY_TEMPLATE);
    def.setDescription("Revenue vs. cost per project for a date range.");
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
                  | <strong>Grouped by:</strong> <span th:text="${parameters.groupBy}">member</span>
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
          <title th:text="${report.name}">Invoice Aging Report</title>
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
                  <h1 th:text="${report.name}">Invoice Aging Report</h1>
              </div>
              <div class="header-right">
                  <div th:text="'Generated: ' + ${generatedAt}"
                       style="font-size: 10px; color: #666;">Generated: 21 Feb 2026 10:30</div>
              </div>
          </div>

          <div class="param-summary">
              <strong>As of:</strong> <span th:text="${parameters.asOfDate}">2026-02-21</span>
              <span th:if="${parameters.customerId != null}">
                  | <strong>Customer:</strong> <span th:text="${parameters.customerName}">Acme Corp</span>
              </span>
          </div>

          <div class="bucket-summary" th:if="${summary != null}">
              <div class="bucket">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['currentAmount'], 1, 2)}">0.00</div>
                  <div class="label">Current</div>
                  <div class="count" th:text="${summary['currentCount']} + ' invoices'">0 invoices</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket1_30Amount'], 1, 2)}">0.00</div>
                  <div class="label">1-30 Days</div>
                  <div class="count" th:text="${summary['bucket1_30Count']} + ' invoices'">0 invoices</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket31_60Amount'], 1, 2)}">0.00</div>
                  <div class="label">31-60 Days</div>
                  <div class="count" th:text="${summary['bucket31_60Count']} + ' invoices'">0 invoices</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket61_90Amount'], 1, 2)}">0.00</div>
                  <div class="label">61-90 Days</div>
                  <div class="count" th:text="${summary['bucket61_90Count']} + ' invoices'">0 invoices</div>
              </div>
              <div class="bucket overdue">
                  <div class="amount" th:text="${#numbers.formatDecimal(summary['bucket90PlusAmount'], 1, 2)}">0.00</div>
                  <div class="label">90+ Days</div>
                  <div class="count" th:text="${summary['bucket90PlusCount']} + ' invoices'">0 invoices</div>
              </div>
          </div>

          <table class="data">
              <thead>
                  <tr>
                      <th>Invoice #</th>
                      <th>Customer</th>
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
          <title th:text="${report.name}">Project Profitability Report</title>
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
                  <h1 th:text="${report.name}">Project Profitability Report</h1>
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
                  | <strong>Customer:</strong> <span th:text="${parameters.customerName}">Acme Corp</span>
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
                       th:classappend="${summary.avgMarginPercent >= 0} ? 'margin-positive' : 'margin-negative'"
                       th:text="${#numbers.formatDecimal(summary.avgMarginPercent, 1, 1)} + '%'">0.0%</div>
                  <div class="label">Avg Margin %</div>
              </div>
          </div>

          <table class="data">
              <thead>
                  <tr>
                      <th>Project</th>
                      <th>Customer</th>
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
                      <td th:text="${row.projectName}">Project Alpha</td>
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
                          th:classappend="${summary.avgMarginPercent >= 0} ? 'margin-positive' : 'margin-negative'"
                          th:text="${#numbers.formatDecimal(summary.avgMarginPercent, 1, 1)} + '%'">0.0%</td>
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
