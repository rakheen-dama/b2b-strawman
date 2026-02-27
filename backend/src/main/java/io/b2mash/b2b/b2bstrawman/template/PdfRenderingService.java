package io.b2mash.b2b.b2bstrawman.template;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseAssembler;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Orchestrates template rendering: loads template, builds context, merges CSS, renders HTML via
 * Thymeleaf, and converts to PDF via OpenHTMLToPDF.
 *
 * <p>Uses a dedicated Thymeleaf TemplateEngine with a StringTemplateResolver (separate from
 * Spring's autoconfigured engine which uses classpath resolvers) so that document template content
 * stored in the database can be rendered directly as Thymeleaf markup.
 */
@Service
public class PdfRenderingService {

  private static final Logger log = LoggerFactory.getLogger(PdfRenderingService.class);
  private static final String DEFAULT_CSS_PATH = "templates/document-default.css";

  private final DocumentTemplateRepository documentTemplateRepository;
  private final List<TemplateContextBuilder> contextBuilders;
  private final TemplateValidationService templateValidationService;
  private final ClauseAssembler clauseAssembler;
  private final TemplateEngine stringTemplateEngine;
  private final String defaultCss;

  public PdfRenderingService(
      DocumentTemplateRepository documentTemplateRepository,
      List<TemplateContextBuilder> contextBuilders,
      TemplateValidationService templateValidationService,
      ClauseAssembler clauseAssembler) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.contextBuilders = contextBuilders;
    this.templateValidationService = templateValidationService;
    this.clauseAssembler = clauseAssembler;
    this.stringTemplateEngine = createStringTemplateEngine();
    this.defaultCss = loadDefaultCss();
  }

  /**
   * Generates a PDF from the given template and entity (no clauses).
   *
   * @param templateId the document template to render
   * @param entityId the entity to populate the template with
   * @param memberId the member triggering the generation
   * @return PdfResult containing PDF bytes, filename, and HTML preview
   */
  @Transactional(readOnly = true)
  public PdfResult generatePdf(UUID templateId, UUID entityId, UUID memberId) {
    return generatePdf(templateId, entityId, memberId, List.of());
  }

  /**
   * Generates a PDF with optional clause content injected into the template context.
   *
   * @param templateId the document template to render
   * @param entityId the entity to populate the template with
   * @param memberId the member triggering the generation
   * @param resolvedClauses the clauses to inject, or empty list for none
   * @return PdfResult containing PDF bytes, filename, and HTML preview
   */
  @Transactional(readOnly = true)
  public PdfResult generatePdf(
      UUID templateId, UUID entityId, UUID memberId, List<Clause> resolvedClauses) {
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    var builder = findBuilder(template.getPrimaryEntityType());
    var contextMap = new HashMap<>(builder.buildContext(entityId, memberId));

    // Inject clause context
    String templateContent =
        injectClauseContext(resolvedClauses, contextMap, template.getContent());

    String customCss = template.getCss() != null ? template.getCss() : "";
    String mergedCss = defaultCss + "\n" + customCss;

    String renderedBody = renderThymeleaf(templateContent, contextMap);
    String fullHtml = wrapHtml(renderedBody, mergedCss);
    byte[] pdfBytes = htmlToPdf(fullHtml);
    String fileName =
        generateFilename(template.getSlug(), template.getPrimaryEntityType(), contextMap);

    log.info(
        "Generated PDF: template={}, entity={}, clauses={}, size={}bytes",
        template.getSlug(),
        entityId,
        resolvedClauses != null ? resolvedClauses.size() : 0,
        pdfBytes.length);

    return new PdfResult(pdfBytes, fileName, fullHtml);
  }

  /**
   * Renders only the HTML preview (no PDF conversion). Used by the preview endpoint to avoid the
   * cost and potential errors of PDF generation.
   */
  @Transactional(readOnly = true)
  public String previewHtml(UUID templateId, UUID entityId, UUID memberId) {
    return previewHtml(templateId, entityId, memberId, List.of());
  }

  /**
   * Renders only the HTML preview with optional clause content injected.
   *
   * @param templateId the document template to render
   * @param entityId the entity to populate the template with
   * @param memberId the member triggering the generation
   * @param resolvedClauses the clauses to inject, or empty list for none
   * @return the rendered HTML string
   */
  @Transactional(readOnly = true)
  public String previewHtml(
      UUID templateId, UUID entityId, UUID memberId, List<Clause> resolvedClauses) {
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    var builder = findBuilder(template.getPrimaryEntityType());
    var contextMap = new HashMap<>(builder.buildContext(entityId, memberId));

    String templateContent =
        injectClauseContext(resolvedClauses, contextMap, template.getContent());

    String customCss = template.getCss() != null ? template.getCss() : "";
    String mergedCss = defaultCss + "\n" + customCss;

    String renderedBody = renderThymeleaf(templateContent, contextMap);
    return wrapHtml(renderedBody, mergedCss);
  }

  /**
   * Renders HTML preview AND validates required context fields in a single pass. Returns a
   * PreviewResponse containing both the HTML and the validation result.
   */
  @Transactional(readOnly = true)
  public PreviewResponse previewWithValidation(UUID templateId, UUID entityId, UUID memberId) {
    return previewWithValidation(templateId, entityId, memberId, List.of());
  }

  /**
   * Renders HTML preview with optional clauses AND validates required context fields in a single
   * pass.
   */
  @Transactional(readOnly = true)
  public PreviewResponse previewWithValidation(
      UUID templateId, UUID entityId, UUID memberId, List<Clause> resolvedClauses) {
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    var builder = findBuilder(template.getPrimaryEntityType());
    var contextMap = new HashMap<>(builder.buildContext(entityId, memberId));

    // Validate required fields
    var validationResult =
        templateValidationService.validateRequiredFields(
            template.getRequiredContextFields(), contextMap);

    String templateContent =
        injectClauseContext(resolvedClauses, contextMap, template.getContent());

    String customCss = template.getCss() != null ? template.getCss() : "";
    String mergedCss = defaultCss + "\n" + customCss;
    String renderedBody = renderThymeleaf(templateContent, contextMap);
    String html = wrapHtml(renderedBody, mergedCss);

    return new PreviewResponse(html, validationResult);
  }

  /** DTO returned by {@link #previewWithValidation}. Lives in the service layer to avoid a */
  // upward dependency from service -> controller.
  public record PreviewResponse(
      String html, TemplateValidationService.TemplateValidationResult validationResult) {}

  /**
   * Builds context for a template without rendering. Used by GeneratedDocumentService to validate
   * required fields before rendering.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> buildContext(UUID templateId, UUID entityId, UUID memberId) {
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));
    var builder = findBuilder(template.getPrimaryEntityType());
    return builder.buildContext(entityId, memberId);
  }

  /**
   * Renders a Thymeleaf fragment (e.g., a single clause body) with the given context. The fragment
   * is wrapped in a minimal HTML shell for Thymeleaf processing, then the wrapper is stripped from
   * the output, returning only the rendered content.
   *
   * @param templateContent the Thymeleaf fragment content (not a full HTML document)
   * @param context the template variable context
   * @return the rendered HTML fragment (without html/body wrapper)
   */
  public String renderFragment(String templateContent, Map<String, Object> context) {
    String wrapped = "<html><body>" + templateContent + "</body></html>";
    String rendered = renderThymeleaf(wrapped, context);
    // Strip the wrapper to return just the fragment content
    int bodyStart = rendered.indexOf("<body>");
    int bodyEnd = rendered.indexOf("</body>");
    if (bodyStart >= 0 && bodyEnd > bodyStart) {
      return rendered.substring(bodyStart + "<body>".length(), bodyEnd).trim();
    }
    return rendered;
  }

  private TemplateContextBuilder findBuilder(TemplateEntityType entityType) {
    return contextBuilders.stream()
        .filter(b -> b.supports() == entityType)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No context builder registered for entity type: " + entityType));
  }

  public String renderThymeleaf(String templateContent, Map<String, Object> contextMap) {
    TemplateSecurityValidator.validate(templateContent);
    var ctx = new Context();
    contextMap.forEach(ctx::setVariable);
    return stringTemplateEngine.process(templateContent, ctx);
  }

  String wrapHtml(String renderedHtml, String css) {
    String styleBlock = "<style>\n" + css + "\n</style>\n";

    // If the rendered output is already a full HTML document, inject CSS into the existing <head>
    int headClose = renderedHtml.indexOf("</head>");
    if (headClose >= 0) {
      return renderedHtml.substring(0, headClose) + styleBlock + renderedHtml.substring(headClose);
    }

    // Otherwise, wrap the fragment in a full HTML document
    return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\"/>\n"
        + styleBlock
        + "</head>\n<body>\n"
        + renderedHtml
        + "\n</body>\n</html>";
  }

  public byte[] htmlToPdf(String html) {
    try (var outputStream = new ByteArrayOutputStream()) {
      var builder = new PdfRendererBuilder();
      builder.withHtmlContent(html, null);
      builder.toStream(outputStream);
      builder.run();
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new PdfGenerationException("Failed to generate PDF from rendered HTML", e);
    }
  }

  /**
   * Validates clause bodies, assembles clause HTML, injects into context, and handles template
   * fallback for missing ${clauses} placeholder.
   */
  private String injectClauseContext(
      List<Clause> resolvedClauses, Map<String, Object> contextMap, String templateContent) {
    if (resolvedClauses == null || resolvedClauses.isEmpty()) {
      contextMap.put("clauses", "");
      contextMap.put("clauseCount", 0);
      return templateContent;
    }

    // Defense in depth: validate each clause body
    for (var clause : resolvedClauses) {
      TemplateSecurityValidator.validate(clause.getBody());
    }

    String clauseHtml = clauseAssembler.assembleClauseBlock(resolvedClauses);
    contextMap.put("clauses", clauseHtml);
    contextMap.put("clauseCount", resolvedClauses.size());

    // Fallback: if template doesn't have ${clauses} placeholder, append section
    if (!clauseHtml.isEmpty() && !templateContent.contains("${clauses}")) {
      templateContent =
          templateContent
              + "\n<div class=\"clauses-section\">\n"
              + "<hr/>\n<h2>Terms and Conditions</h2>\n"
              + "<div th:utext=\"${clauses}\"></div>\n</div>";
    }

    return templateContent;
  }

  String generateFilename(
      String templateSlug, TemplateEntityType entityType, Map<String, Object> context) {
    String entityName = extractEntityName(entityType, context);
    String slugifiedName = slugify(entityName);
    String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    return templateSlug + "-" + slugifiedName + "-" + date + ".pdf";
  }

  @SuppressWarnings("unchecked")
  private String extractEntityName(TemplateEntityType entityType, Map<String, Object> context) {
    return switch (entityType) {
      case PROJECT -> {
        var project = (Map<String, Object>) context.get("project");
        yield project != null ? (String) project.get("name") : "document";
      }
      case CUSTOMER -> {
        var customer = (Map<String, Object>) context.get("customer");
        yield customer != null ? (String) customer.get("name") : "document";
      }
      case INVOICE -> {
        var invoice = (Map<String, Object>) context.get("invoice");
        yield invoice != null && invoice.get("invoiceNumber") != null
            ? (String) invoice.get("invoiceNumber")
            : "document";
      }
    };
  }

  private String slugify(String text) {
    if (text == null || text.isBlank()) {
      return "document";
    }
    return text.toLowerCase()
        .replaceAll("[\\s]+", "-")
        .replaceAll("[^a-z0-9-]", "")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  private static TemplateEngine createStringTemplateEngine() {
    var engine = new TemplateEngine();
    engine.setDialect(new LenientStandardDialect());
    var resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private static String loadDefaultCss() {
    try (InputStream is = new ClassPathResource(DEFAULT_CSS_PATH).getInputStream()) {
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("Could not load default CSS from {}, using empty CSS", DEFAULT_CSS_PATH, e);
      return "";
    }
  }
}
