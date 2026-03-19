package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.OutputFormat;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a PAIA Section 51 manual PDF. Looks up the template by slug, builds context via {@link
 * PaiaManualContextBuilder}, renders HTML via {@link TiptapRenderer}, converts to PDF, uploads to
 * S3, and persists a {@link GeneratedDocument} record.
 */
@Service
public class PaiaManualGenerationService {

  private static final Logger log = LoggerFactory.getLogger(PaiaManualGenerationService.class);
  private static final String PAIA_TEMPLATE_SLUG = "paia-section-51-manual";

  private final PaiaManualContextBuilder contextBuilder;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final PdfRenderingService pdfRenderingService;
  private final TiptapRenderer tiptapRenderer;
  private final StorageService storageService;
  private final AuditService auditService;
  private final OrgSettingsRepository orgSettingsRepository;

  public PaiaManualGenerationService(
      PaiaManualContextBuilder contextBuilder,
      DocumentTemplateRepository documentTemplateRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      PdfRenderingService pdfRenderingService,
      TiptapRenderer tiptapRenderer,
      StorageService storageService,
      AuditService auditService,
      OrgSettingsRepository orgSettingsRepository) {
    this.contextBuilder = contextBuilder;
    this.documentTemplateRepository = documentTemplateRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.pdfRenderingService = pdfRenderingService;
    this.tiptapRenderer = tiptapRenderer;
    this.storageService = storageService;
    this.auditService = auditService;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  /**
   * Generates a PAIA Section 51 manual PDF and returns a response with the generated document
   * details.
   */
  @Transactional
  public PaiaGenerateResponse generate(UUID memberId) {
    // 1. Look up template by slug
    var template =
        documentTemplateRepository
            .findBySlug(PAIA_TEMPLATE_SLUG)
            .orElseThrow(
                () -> new ResourceNotFoundException("DocumentTemplate", PAIA_TEMPLATE_SLUG));

    // 2. Build context
    var context = contextBuilder.buildContext();

    // 3. Render HTML via TiptapRenderer
    var content =
        template.getContent() != null
            ? template.getContent()
            : Map.<String, Object>of("type", "doc");
    String css = template.getCss() != null ? template.getCss() : "";
    String html = tiptapRenderer.render(content, context, Map.of(), css);

    // 4. Convert to PDF
    byte[] pdfBytes = pdfRenderingService.htmlToPdf(html);

    // 5. Upload to S3
    String tenantId = RequestScopes.requireTenantId();
    String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    String uniqueId = UUID.randomUUID().toString().substring(0, 8);
    String fileName = PAIA_TEMPLATE_SLUG + "-" + date + "-" + uniqueId + ".pdf";
    String s3Key = "org/" + tenantId + "/generated/" + fileName;

    try {
      storageService.upload(s3Key, pdfBytes, "application/pdf");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to upload PAIA manual PDF to storage", e);
    }

    // 6. Resolve entityId from OrgSettings
    UUID entityId =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getId)
            .orElseThrow(() -> new ResourceNotFoundException("OrgSettings", "current tenant"));

    // 7. Create GeneratedDocument record
    var contextSnapshot = new HashMap<String, Object>();
    contextSnapshot.put("template_name", template.getName());
    contextSnapshot.put("entity_type", TemplateEntityType.ORGANIZATION.name());
    contextSnapshot.put("template_slug", PAIA_TEMPLATE_SLUG);

    var generatedDoc =
        new GeneratedDocument(
            template.getId(),
            TemplateEntityType.ORGANIZATION,
            entityId,
            fileName,
            s3Key,
            pdfBytes.length,
            memberId);
    generatedDoc.setContextSnapshot(contextSnapshot);
    generatedDoc.setOutputFormat(OutputFormat.PDF);
    generatedDoc = generatedDocumentRepository.save(generatedDoc);

    log.info(
        "Generated PAIA Section 51 manual: id={}, fileName={}, size={}",
        generatedDoc.getId(),
        fileName,
        pdfBytes.length);

    // 8. Audit event
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("paia.manual.generated")
            .entityType("generated_document")
            .entityId(generatedDoc.getId())
            .details(
                Map.of(
                    "jurisdiction",
                    context.getOrDefault("jurisdiction", "ZA"),
                    "template_slug",
                    PAIA_TEMPLATE_SLUG))
            .build());

    return new PaiaGenerateResponse(
        generatedDoc.getId(),
        generatedDoc.getFileName(),
        generatedDoc.getFileSize(),
        generatedDoc.getGeneratedAt());
  }

  /** Response DTO for PAIA manual generation. */
  public record PaiaGenerateResponse(
      UUID id, String fileName, long fileSize, Instant generatedAt) {}
}
