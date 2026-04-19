package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.GenerateStatementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto.StatementSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.statement.event.StatementOfAccountGeneratedEvent;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional orchestrator for Statement of Account generation (Phase 67, Epic 491A, architecture
 * §67.4.3 / ADR-250). Co-gated under the {@code disbursements} module — every public method calls
 * {@link VerticalModuleGuard#requireModule(String)} at the top.
 *
 * <p>Bypasses {@link io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService#generateDocument}
 * because Statement of Account context is period-bound (start/end dates) and the standard {@code
 * TemplateContextBuilder.buildContext(entityId, memberId)} interface is too narrow. We build the
 * context ourselves via {@link StatementOfAccountContextBuilder}, render via {@link
 * TiptapRenderer}, convert to PDF via {@link PdfRenderingService#htmlToPdf(String)}, upload, and
 * persist a {@link GeneratedDocument}. Audit + domain event publish in the same transaction.
 */
@Service
public class StatementService {

  private static final Logger log = LoggerFactory.getLogger(StatementService.class);

  static final String MODULE_ID = "disbursements";
  static final String SYSTEM_TEMPLATE_SLUG = "statement-of-account";
  private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
  private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private final VerticalModuleGuard moduleGuard;
  private final ProjectRepository projectRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final StatementOfAccountContextBuilder contextBuilder;
  private final TiptapRenderer tiptapRenderer;
  private final PdfRenderingService pdfRenderingService;
  private final StorageService storageService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public StatementService(
      VerticalModuleGuard moduleGuard,
      ProjectRepository projectRepository,
      DocumentTemplateRepository documentTemplateRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      StatementOfAccountContextBuilder contextBuilder,
      TiptapRenderer tiptapRenderer,
      PdfRenderingService pdfRenderingService,
      StorageService storageService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.moduleGuard = moduleGuard;
    this.projectRepository = projectRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.contextBuilder = contextBuilder;
    this.tiptapRenderer = tiptapRenderer;
    this.pdfRenderingService = pdfRenderingService;
    this.storageService = storageService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public StatementResponse generate(
      UUID projectId, GenerateStatementRequest request, UUID memberId) {
    moduleGuard.requireModule(MODULE_ID);

    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    var template = resolveTemplate(request.templateId());

    Map<String, Object> context =
        contextBuilder.build(projectId, request.periodStart(), request.periodEnd());

    String html = renderHtml(template, context);
    byte[] pdfBytes = pdfRenderingService.htmlToPdf(html);

    String fileName = buildFileName(template.getSlug(), project, request.periodEnd());
    String tenantId = RequestScopes.requireTenantId();
    String s3Key = "org/" + tenantId + "/generated/" + fileName;
    storageService.upload(s3Key, pdfBytes, "application/pdf");

    StatementSummary summary = extractSummary(context);

    var generatedDoc =
        new GeneratedDocument(
            template.getId(),
            TemplateEntityType.PROJECT,
            projectId,
            fileName,
            s3Key,
            pdfBytes.length,
            memberId);
    var snapshot = new HashMap<String, Object>();
    snapshot.put("template_name", template.getName());
    snapshot.put("entity_type", "PROJECT");
    snapshot.put("entity_id", projectId.toString());
    snapshot.put("period_start", request.periodStart().toString());
    snapshot.put("period_end", request.periodEnd().toString());
    // Persist the summary as-of generation time so GET by id returns the same numbers as the saved
    // PDF even if underlying fees/disbursements/trust data changes afterwards.
    snapshot.put("summary", summarySnapshotMap(summary));
    generatedDoc.setContextSnapshot(snapshot);
    generatedDoc = generatedDocumentRepository.save(generatedDoc);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("statement.generated")
            .entityType("generated_document")
            .entityId(generatedDoc.getId())
            .details(
                Map.of(
                    "project_id", projectId.toString(),
                    "period_start", request.periodStart().toString(),
                    "period_end", request.periodEnd().toString(),
                    "template_id", template.getId().toString(),
                    "file_name", fileName))
            .build());

    eventPublisher.publishEvent(
        StatementOfAccountGeneratedEvent.of(
            projectId, generatedDoc.getId(), request.periodStart(), request.periodEnd(), memberId));

    log.info(
        "Generated Statement of Account: project={}, period={}..{}, generatedDoc={}",
        projectId,
        request.periodStart(),
        request.periodEnd(),
        generatedDoc.getId());

    String pdfUrl = "/api/documents/" + generatedDoc.getId() + "/pdf";
    return new StatementResponse(
        generatedDoc.getId(),
        template.getId(),
        generatedDoc.getGeneratedAt(),
        html,
        pdfUrl,
        new StatementResponse.MatterRef(projectId, project.getName()),
        summary);
  }

  @Transactional(readOnly = true)
  public Page<StatementResponse> list(UUID projectId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    UUID statementTemplateId =
        documentTemplateRepository
            .findBySlug(SYSTEM_TEMPLATE_SLUG)
            .map(DocumentTemplate::getId)
            .orElse(null);

    List<GeneratedDocument> all =
        generatedDocumentRepository
            .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                TemplateEntityType.PROJECT, projectId)
            .stream()
            .filter(
                gd -> statementTemplateId == null || statementTemplateId.equals(gd.getTemplateId()))
            .toList();

    int from = (int) Math.min(pageable.getOffset(), all.size());
    int to = (int) Math.min(from + pageable.getPageSize(), all.size());
    List<StatementResponse> page =
        all.subList(from, to).stream().map(gd -> toResponseSummary(gd, project)).toList();
    return new org.springframework.data.domain.PageImpl<>(page, pageable, all.size());
  }

  @Transactional(readOnly = true)
  public StatementResponse getById(UUID projectId, UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    var generatedDoc =
        generatedDocumentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", id));

    if (!projectId.equals(generatedDoc.getPrimaryEntityId())) {
      throw new ResourceNotFoundException("GeneratedDocument", id);
    }

    // Return snapshot-backed summary only. We deliberately do NOT re-render htmlPreview from
    // current DB state: doing so would diverge from the saved PDF if fees/disbursements/trust data
    // changed after generation. The client fetches the PDF via pdfUrl for the authoritative view.
    StatementSummary summary = extractSnapshotSummary(generatedDoc.getContextSnapshot());

    String pdfUrl = "/api/documents/" + generatedDoc.getId() + "/pdf";
    return new StatementResponse(
        generatedDoc.getId(),
        generatedDoc.getTemplateId(),
        generatedDoc.getGeneratedAt(),
        null,
        pdfUrl,
        new StatementResponse.MatterRef(projectId, project.getName()),
        summary);
  }

  // ---------- helpers ----------

  private DocumentTemplate resolveTemplate(UUID templateId) {
    if (templateId != null) {
      var tpl =
          documentTemplateRepository
              .findById(templateId)
              .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));
      // Guard against callers passing the id of an unrelated template (e.g. engagement-letter).
      // Both base templates (slug = SYSTEM_TEMPLATE_SLUG) and any clones (slug starts with the
      // system slug + "-") are accepted.
      String slug = tpl.getSlug();
      if (slug == null
          || !(slug.equals(SYSTEM_TEMPLATE_SLUG) || slug.startsWith(SYSTEM_TEMPLATE_SLUG + "-"))) {
        throw new InvalidStateException(
            "Template is not a statement-of-account template",
            "templateId " + templateId + " resolves to a template with slug '" + slug + "'.");
      }
      return tpl;
    }
    return documentTemplateRepository
        .findBySlug(SYSTEM_TEMPLATE_SLUG)
        .orElseThrow(
            () ->
                ResourceNotFoundException.withDetail(
                    "Statement template missing",
                    "Default Statement of Account template (slug='"
                        + SYSTEM_TEMPLATE_SLUG
                        + "') is not installed for this tenant."));
  }

  private String renderHtml(DocumentTemplate template, Map<String, Object> context) {
    Map<String, Object> content =
        template.getContent() != null ? template.getContent() : Map.of("type", "doc");
    return tiptapRenderer.render(content, context, Map.of(), template.getCss(), Map.of());
  }

  private String buildFileName(String templateSlug, Project project, LocalDate periodEnd) {
    String base = slugify(project.getName());
    String date = periodEnd.format(FILENAME_DATE);
    return templateSlug + "-" + base + "-" + date + ".pdf";
  }

  private String slugify(String raw) {
    if (raw == null || raw.isBlank()) return "matter";
    String s = raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (s.isEmpty()) return "matter";
    return s.length() > 50 ? s.substring(0, 50) : s;
  }

  @SuppressWarnings("unchecked")
  private StatementSummary extractSummary(Map<String, Object> context) {
    Object raw = context.get("summary");
    if (!(raw instanceof Map<?, ?> map)) {
      return zeroSummary();
    }
    Map<String, Object> summary = (Map<String, Object>) map;
    return new StatementSummary(
        toBigDecimal(summary.get("total_fees")),
        toBigDecimal(summary.get("total_disbursements")),
        toBigDecimal(summary.get("previous_balance_owing")),
        toBigDecimal(summary.get("payments_received")),
        toBigDecimal(summary.get("closing_balance_owing")),
        toBigDecimal(summary.get("trust_balance_held")));
  }

  private static java.math.BigDecimal toBigDecimal(Object o) {
    if (o instanceof java.math.BigDecimal bd) return bd;
    if (o instanceof Number n) return new java.math.BigDecimal(n.toString());
    if (o instanceof String s && !s.isBlank()) {
      try {
        return new java.math.BigDecimal(s);
      } catch (NumberFormatException e) {
        return java.math.BigDecimal.ZERO;
      }
    }
    return java.math.BigDecimal.ZERO;
  }

  private StatementSummary zeroSummary() {
    var z = java.math.BigDecimal.ZERO;
    return new StatementSummary(z, z, z, z, z, z);
  }

  private StatementResponse toResponseSummary(GeneratedDocument gd, Project project) {
    // Snapshot-backed: list + getById must surface the at-generation-time numbers, not live DB
    // state.
    StatementSummary summary = extractSnapshotSummary(gd.getContextSnapshot());
    String pdfUrl = "/api/documents/" + gd.getId() + "/pdf";
    return new StatementResponse(
        gd.getId(),
        gd.getTemplateId(),
        gd.getGeneratedAt(),
        null,
        pdfUrl,
        new StatementResponse.MatterRef(project.getId(), project.getName()),
        summary);
  }

  private Map<String, Object> summarySnapshotMap(StatementSummary s) {
    var m = new LinkedHashMap<String, Object>();
    m.put("total_fees", s.totalFees() != null ? s.totalFees().toPlainString() : "0");
    m.put(
        "total_disbursements",
        s.totalDisbursements() != null ? s.totalDisbursements().toPlainString() : "0");
    m.put(
        "previous_balance_owing",
        s.previousBalanceOwing() != null ? s.previousBalanceOwing().toPlainString() : "0");
    m.put(
        "payments_received",
        s.paymentsReceived() != null ? s.paymentsReceived().toPlainString() : "0");
    m.put(
        "closing_balance_owing",
        s.closingBalanceOwing() != null ? s.closingBalanceOwing().toPlainString() : "0");
    m.put(
        "trust_balance_held",
        s.trustBalanceHeld() != null ? s.trustBalanceHeld().toPlainString() : "0");
    return m;
  }

  @SuppressWarnings("unchecked")
  private StatementSummary extractSnapshotSummary(Map<String, Object> snapshot) {
    if (snapshot == null) {
      return zeroSummary();
    }
    Object raw = snapshot.get("summary");
    if (!(raw instanceof Map<?, ?> map)) {
      return zeroSummary();
    }
    Map<String, Object> summary = (Map<String, Object>) map;
    return new StatementSummary(
        toBigDecimal(summary.get("total_fees")),
        toBigDecimal(summary.get("total_disbursements")),
        toBigDecimal(summary.get("previous_balance_owing")),
        toBigDecimal(summary.get("payments_received")),
        toBigDecimal(summary.get("closing_balance_owing")),
        toBigDecimal(summary.get("trust_balance_held")));
  }
}
