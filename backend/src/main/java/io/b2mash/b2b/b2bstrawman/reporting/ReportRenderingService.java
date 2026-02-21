package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.LenientStandardDialect;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Orchestrates report rendering: builds Thymeleaf context from report definition and execution
 * result, renders HTML, and converts to PDF.
 *
 * <p>Uses its own Thymeleaf engine (without TemplateSecurityValidator) because report templates are
 * system-provided and trusted. They use Thymeleaf utility objects like {@code #numbers} and {@code
 * #dates} which the security validator blocks for user-submitted document templates.
 *
 * <p>Report templates are full HTML documents (not fragments like document templates), so no
 * wrapHtml() is needed.
 */
@Service
public class ReportRenderingService {

  private final PdfRenderingService pdfRenderingService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TemplateEngine reportTemplateEngine;

  public ReportRenderingService(
      PdfRenderingService pdfRenderingService, OrgSettingsRepository orgSettingsRepository) {
    this.pdfRenderingService = pdfRenderingService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.reportTemplateEngine = createReportTemplateEngine();
  }

  /**
   * Render HTML preview from report definition + execution result. Report templates are
   * self-contained (full HTML documents with inline styles), so no wrapHtml() needed.
   */
  public String renderHtml(
      ReportDefinition definition, ReportResult result, Map<String, Object> parameters) {
    Map<String, Object> context = buildContext(definition, result, parameters);
    var ctx = new Context();
    context.forEach(ctx::setVariable);
    return reportTemplateEngine.process(definition.getTemplateBody(), ctx);
  }

  /** Render PDF from report definition + full execution result. */
  public byte[] renderPdf(
      ReportDefinition definition, ReportResult result, Map<String, Object> parameters) {
    String html = renderHtml(definition, result, parameters);
    return pdfRenderingService.htmlToPdf(html);
  }

  /**
   * Generate a filename for the exported report based on slug and date parameters. Pattern:
   * {slug}-{dateFrom}-to-{dateTo}.{ext} or {slug}-{asOfDate}.{ext} or {slug}.{ext}
   */
  String generateFilename(String slug, Map<String, Object> parameters, String extension) {
    if (parameters.containsKey("dateFrom") && parameters.containsKey("dateTo")) {
      return slug
          + "-"
          + parameters.get("dateFrom")
          + "-to-"
          + parameters.get("dateTo")
          + "."
          + extension;
    }
    if (parameters.containsKey("asOfDate")) {
      return slug + "-" + parameters.get("asOfDate") + "." + extension;
    }
    return slug + "." + extension;
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
    var columns = (List<Map<String, String>>) colDefs.get("columns");
    return columns.stream()
        .map(
            c ->
                new ColumnDefinition(
                    c.get("key"), c.get("label"), c.get("type"), c.getOrDefault("format", null)))
        .toList();
  }

  /**
   * Creates a dedicated Thymeleaf engine for report templates. Uses LenientStandardDialect (same as
   * PdfRenderingService) but skips TemplateSecurityValidator since report templates are
   * system-provided and trusted.
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
