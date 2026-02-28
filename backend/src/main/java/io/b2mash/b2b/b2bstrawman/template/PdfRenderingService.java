package io.b2mash.b2b.b2bstrawman.template;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates template rendering: loads template, builds context, renders HTML via TiptapRenderer,
 * and converts to PDF via OpenHTMLToPDF.
 *
 * <p>All rendering uses the TiptapRenderer JSON tree walker. Thymeleaf rendering has been removed.
 */
@Service
public class PdfRenderingService {

  private static final Logger log = LoggerFactory.getLogger(PdfRenderingService.class);

  private final DocumentTemplateRepository documentTemplateRepository;
  private final List<TemplateContextBuilder> contextBuilders;
  private final TemplateValidationService templateValidationService;
  private final TiptapRenderer tiptapRenderer;

  public PdfRenderingService(
      DocumentTemplateRepository documentTemplateRepository,
      List<TemplateContextBuilder> contextBuilders,
      TemplateValidationService templateValidationService,
      TiptapRenderer tiptapRenderer) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.contextBuilders = contextBuilders;
    this.templateValidationService = templateValidationService;
    this.tiptapRenderer = tiptapRenderer;
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

    String fullHtml = renderTemplateToHtml(template, contextMap, resolvedClauses);
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

    return renderTemplateToHtml(template, contextMap, resolvedClauses);
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

    String html = renderTemplateToHtml(template, contextMap, resolvedClauses);

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

  /** Renders a template to a full HTML document string via {@link TiptapRenderer}. */
  private String renderTemplateToHtml(
      DocumentTemplate template, Map<String, Object> contextMap, List<Clause> resolvedClauses) {
    Map<UUID, Clause> clauseMap = new LinkedHashMap<>();
    if (resolvedClauses != null) {
      for (var clause : resolvedClauses) {
        clauseMap.put(clause.getId(), clause);
      }
    }
    Map<String, Object> content =
        template.getContent() != null ? template.getContent() : Map.of("type", "doc");
    return tiptapRenderer.render(content, contextMap, clauseMap, template.getCss());
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
}
