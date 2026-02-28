package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.LenientStandardDialect;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Orchestrates report rendering: builds Thymeleaf context from report definition and execution
 * result, renders HTML, and converts to PDF.
 *
 * <p>Uses its own Thymeleaf engine because report templates are system-provided and trusted. They
 * use Thymeleaf utility objects like {@code #numbers} and {@code #dates}.
 *
 * <p>Report templates are full HTML documents (not fragments like document templates), so no
 * wrapHtml() is needed.
 */
@Service
public class ReportRenderingService {

  private static final int PREVIEW_ROW_LIMIT = 50;

  private final PdfRenderingService pdfRenderingService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportExecutionService reportExecutionService;
  private final TemplateEngine reportTemplateEngine;

  public ReportRenderingService(
      PdfRenderingService pdfRenderingService,
      OrgSettingsRepository orgSettingsRepository,
      ReportDefinitionRepository reportDefinitionRepository,
      ReportExecutionService reportExecutionService) {
    this.pdfRenderingService = pdfRenderingService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.reportExecutionService = reportExecutionService;
    this.reportTemplateEngine = createReportTemplateEngine();
  }

  /**
   * Render HTML preview from report definition + execution result. Report templates are
   * self-contained (full HTML documents with inline styles), so no wrapHtml() needed.
   */
  @Transactional(readOnly = true)
  public String renderHtml(
      ReportDefinition definition, ReportResult result, Map<String, Object> parameters) {
    Map<String, Object> context = buildContext(definition, result, parameters);
    var ctx = new Context();
    context.forEach(ctx::setVariable);
    return reportTemplateEngine.process(definition.getTemplateBody(), ctx);
  }

  /** Render PDF from report definition + full execution result. */
  @Transactional(readOnly = true)
  public byte[] renderPdf(
      ReportDefinition definition, ReportResult result, Map<String, Object> parameters) {
    String html = renderHtml(definition, result, parameters);
    return pdfRenderingService.htmlToPdf(html);
  }

  /**
   * Render an HTML preview for a report, limited to {@value #PREVIEW_ROW_LIMIT} rows. Loads the
   * definition by slug, executes the query, and renders the result.
   */
  @Transactional(readOnly = true)
  public String renderPreviewHtml(String slug, Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var result = reportExecutionService.executeForExport(slug, parameters);
    var limitedRows =
        result.rows().size() > PREVIEW_ROW_LIMIT
            ? result.rows().subList(0, PREVIEW_ROW_LIMIT)
            : result.rows();
    var limitedResult =
        new ReportResult(
            limitedRows, result.summary(), result.totalElements(), result.totalPages());

    return renderHtml(definition, limitedResult, parameters);
  }

  /**
   * Generate a filename for the exported report based on slug and date parameters. Pattern:
   * {slug}-{dateFrom}-to-{dateTo}.{ext} or {slug}-{asOfDate}.{ext} or {slug}.{ext}
   */
  String generateFilename(String slug, Map<String, Object> parameters, String extension) {
    String filename;
    if (parameters.containsKey("dateFrom") && parameters.containsKey("dateTo")) {
      filename =
          slug
              + "-"
              + parameters.get("dateFrom")
              + "-to-"
              + parameters.get("dateTo")
              + "."
              + extension;
    } else if (parameters.containsKey("asOfDate")) {
      filename = slug + "-" + parameters.get("asOfDate") + "." + extension;
    } else {
      filename = slug + "." + extension;
    }
    return filename.replaceAll("[^a-zA-Z0-9._-]", "");
  }

  /** Write CSV to output stream from execution result + column definitions. */
  public void writeCsv(
      ReportDefinition definition,
      ReportResult result,
      Map<String, Object> parameters,
      OutputStream outputStream)
      throws IOException {
    var writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

    // Metadata header
    writer.write("# " + definition.getName());
    writer.newLine();
    writer.write("# Generated: " + Instant.now().toString());
    writer.newLine();
    writer.write("# Parameters: " + formatParametersForCsv(parameters));
    writer.newLine();

    // Column headers
    var columns = getColumns(definition);
    writer.write(columns.stream().map(c -> escapeCsv(c.label())).collect(Collectors.joining(",")));
    writer.newLine();

    // Data rows
    for (var row : result.rows()) {
      writer.write(
          columns.stream()
              .map(c -> escapeCsv(formatValue(row.get(c.key()), c.type(), c.format())))
              .collect(Collectors.joining(",")));
      writer.newLine();
    }

    writer.flush();
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    // Defuse CSV formula injection (OWASP recommendation)
    if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
      value = "'" + value;
    }
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("\n")
        || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private String formatValue(Object value, String type, String format) {
    if (value == null) {
      return "";
    }
    return switch (type) {
      case "decimal", "currency" -> {
        if (value instanceof Number number) {
          if (format != null && format.contains(".")) {
            int decimals = format.length() - format.indexOf('.') - 1;
            yield String.format("%." + decimals + "f", number.doubleValue());
          }
          yield String.format("%.2f", number.doubleValue());
        }
        yield value.toString();
      }
      case "integer" -> {
        if (value instanceof Number number) {
          yield String.valueOf(number.longValue());
        }
        yield value.toString();
      }
      default -> value.toString();
    };
  }

  private String formatParametersForCsv(Map<String, Object> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    return parameters.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(", "));
  }

  private Map<String, Object> buildContext(
      ReportDefinition definition, ReportResult result, Map<String, Object> parameters) {
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    var context = new HashMap<String, Object>();
    context.put(
        "report",
        Map.of(
            "name",
            definition.getName(),
            "description",
            definition.getDescription() != null ? definition.getDescription() : ""));
    context.put("parameters", parameters);
    context.put("rows", result.rows());
    context.put("summary", result.summary());
    context.put(
        "generatedAt",
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now()));
    context.put("columns", getColumns(definition));
    if (settings != null) {
      context.put(
          "branding",
          Map.of(
              "logoS3Key",
              settings.getLogoS3Key() != null ? settings.getLogoS3Key() : "",
              "brandColor",
              settings.getBrandColor() != null ? settings.getBrandColor() : "#1a1a2e",
              "footerText",
              settings.getDocumentFooterText() != null ? settings.getDocumentFooterText() : ""));
    }
    return context;
  }

  @SuppressWarnings("unchecked")
  private List<ColumnDefinition> getColumns(ReportDefinition definition) {
    var colDefs = definition.getColumnDefinitions();
    if (colDefs == null) {
      return List.of();
    }
    var columns = (List<Map<String, String>>) colDefs.get("columns");
    if (columns == null) {
      return List.of();
    }
    return columns.stream()
        .map(
            c ->
                new ColumnDefinition(
                    c.get("key"), c.get("label"), c.get("type"), c.getOrDefault("format", null)))
        .toList();
  }

  /**
   * Creates a dedicated Thymeleaf engine for report templates. Uses LenientStandardDialect. Report
   * templates are system-provided and trusted.
   */
  private static TemplateEngine createReportTemplateEngine() {
    var engine = new TemplateEngine();
    engine.setDialect(new LenientStandardDialect());
    var resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
