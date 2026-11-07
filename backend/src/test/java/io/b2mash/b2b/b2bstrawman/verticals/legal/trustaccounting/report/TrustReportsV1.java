package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

/**
 * Verbatim copy of the v1 (Phase 89 630A) {@code section-35-data-pack} template body shipped by
 * {@link TrustReportPackSeeder}: the {@code from} of the v2 reconcile entry (G89-F18). Never edit;
 * a later version appends its own verbatim copy (cf. {@code StandardReportsV1}).
 */
final class TrustReportsV1 {

  private TrustReportsV1() {}

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

  static final String SECTION_35_TEMPLATE =
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
              <p style="font-size: 10px; color: #666;" th:text="'Rows: ' + ${section['rowCount']}"></p>
          </div>
          <div class="footer"><div class="footer-right" th:text="'Generated ' + ${generatedAt}"></div></div>
      </body>
      </html>
      """;
}
