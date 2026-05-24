package io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting;

import static io.b2mash.b2b.b2bstrawman.integration.ai.skill.TiptapNodeBuilder.buildBoldParagraph;
import static io.b2mash.b2b.b2bstrawman.integration.ai.skill.TiptapNodeBuilder.buildDocument;
import static io.b2mash.b2b.b2bstrawman.integration.ai.skill.TiptapNodeBuilder.buildHeading;
import static io.b2mash.b2b.b2bstrawman.integration.ai.skill.TiptapNodeBuilder.buildParagraph;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates a Tiptap JSON document from {@link DraftingOutput} (AI-provided variable fills and
 * narrative sections) applied to a {@link DocumentTemplate}, and persists it as a {@link Document}
 * entity with AI provenance metadata (ADR-289).
 *
 * <p>The generated document is saved as a PROJECT-scoped document attached to the matter, ready for
 * further attorney editing in the Tiptap editor.
 */
@Service
public class AiDraftDocumentGenerator {

  private static final Logger log = LoggerFactory.getLogger(AiDraftDocumentGenerator.class);

  private final DocumentTemplateRepository documentTemplateRepository;
  private final DocumentRepository documentRepository;
  private final ClauseRepository clauseRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  public AiDraftDocumentGenerator(
      DocumentTemplateRepository documentTemplateRepository,
      DocumentRepository documentRepository,
      ClauseRepository clauseRepository,
      StorageService storageService,
      ObjectMapper objectMapper) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.documentRepository = documentRepository;
    this.clauseRepository = clauseRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
  }

  /**
   * Generate a Tiptap JSON document from the drafting output applied to a template, persist it as a
   * Document entity, and upload the content to storage.
   *
   * @param output the structured drafting output from the AI skill
   * @param templateId the template to base the document on
   * @param projectId the matter/project to attach the document to
   * @param executionId the AI execution ID for provenance tracking
   * @param memberId the member who invoked the drafting
   * @return the persisted Document entity
   */
  @Transactional
  public Document generateDraft(
      DraftingOutput output, UUID templateId, UUID projectId, UUID executionId, UUID memberId) {

    String orgId = RequestScopes.requireOrgId();

    DocumentTemplate template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    Map<String, Object> tiptapDoc = buildDraftDocument(output, template);

    byte[] contentBytes;
    try {
      contentBytes = objectMapper.writeValueAsBytes(tiptapDoc);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize draft document to JSON", e);
    }

    String fileName = template.getName() + " - " + LocalDate.now() + ".json";

    var document =
        new Document(
            Document.Scope.PROJECT,
            projectId,
            null,
            fileName,
            "application/json",
            contentBytes.length,
            memberId,
            Document.Visibility.INTERNAL);

    Document saved = documentRepository.save(document);

    String s3Key =
        S3PresignedUrlService.buildKey(orgId, projectId.toString(), saved.getId().toString());
    storageService.upload(s3Key, contentBytes, "application/json");

    saved.assignS3Key(s3Key);
    saved.confirmUpload();
    saved.markAsAiGenerated(executionId);

    saved = documentRepository.save(saved);

    log.info(
        "Generated draft document={} from template={} for project={} execution={}",
        saved.getId(),
        templateId,
        projectId,
        executionId);

    return saved;
  }

  // ── Tiptap Document Builder ─────────────────────────────────────────────────

  private Map<String, Object> buildDraftDocument(DraftingOutput output, DocumentTemplate template) {
    var content = new ArrayList<Map<String, Object>>();

    // Title
    content.add(buildHeading(template.getName(), 1));

    // Variable fills section
    if (output.variableFills() != null && !output.variableFills().isEmpty()) {
      content.add(buildHeading("Document Details", 2));
      for (var fill : output.variableFills()) {
        content.add(buildBoldParagraph(fill.variableName(), fill.value()));
      }
    }

    // Narrative sections
    if (output.narrativeSections() != null && !output.narrativeSections().isEmpty()) {
      for (var section : output.narrativeSections()) {
        content.add(buildHeading(section.sectionName(), 2));
        content.add(buildParagraph(section.content()));
        if (section.notes() != null && !section.notes().isEmpty()) {
          content.add(buildBoldParagraph("Notes", section.notes()));
        }
      }
    }

    // Recommended clauses
    injectRecommendedClauses(output, content);

    return buildDocument(content);
  }

  @SuppressWarnings("unchecked")
  private void injectRecommendedClauses(
      DraftingOutput output, List<Map<String, Object>> contentNodes) {
    if (output.clauseRecommendations() == null || output.clauseRecommendations().isEmpty()) {
      return;
    }

    // Batch-fetch all recommended clauses in a single query (avoids N+1)
    List<UUID> clauseIds =
        output.clauseRecommendations().stream()
            .map(DraftingOutput.ClauseRecommendation::clauseId)
            .toList();

    Map<UUID, Clause> clauseMap =
        clauseRepository.findAllById(clauseIds).stream()
            .collect(Collectors.toMap(Clause::getId, Function.identity()));

    boolean headingAdded = false;

    for (var recommendation : output.clauseRecommendations()) {
      Clause clause = clauseMap.get(recommendation.clauseId());
      if (clause == null) {
        log.warn(
            "Recommended clause not found: id={}, name={}",
            recommendation.clauseId(),
            recommendation.clauseName());
        continue;
      }

      if (!headingAdded) {
        contentNodes.add(buildHeading("Recommended Clauses", 2));
        headingAdded = true;
      }

      contentNodes.add(buildHeading(clause.getTitle(), 3));

      // Extract content nodes from clause body (Tiptap JSON)
      Map<String, Object> clauseBody = clause.getBody();
      if (clauseBody != null && clauseBody.containsKey("content")) {
        Object bodyContent = clauseBody.get("content");
        if (bodyContent instanceof List<?> bodyNodes) {
          for (Object node : bodyNodes) {
            if (node instanceof Map<?, ?> nodeMap) {
              contentNodes.add((Map<String, Object>) nodeMap);
            }
          }
        }
      }
    }
  }
}
