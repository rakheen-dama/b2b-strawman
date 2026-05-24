package io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates a structured Tiptap JSON review report from {@link ContractReviewOutput} and persists
 * it as a {@link Document} entity with AI provenance metadata (ADR-288, ADR-292).
 *
 * <p>The report is saved as a PROJECT-scoped document attached to the matter, appearing alongside
 * the reviewed contract in the documents tab.
 */
@Service
public class AiReviewReportGenerator {

  private static final Logger log = LoggerFactory.getLogger(AiReviewReportGenerator.class);

  private static final List<String> SEVERITY_ORDER = List.of("HIGH", "MEDIUM", "LOW", "INFO");

  private final DocumentRepository documentRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  public AiReviewReportGenerator(
      DocumentRepository documentRepository,
      StorageService storageService,
      ObjectMapper objectMapper) {
    this.documentRepository = documentRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
  }

  /**
   * Generate a Tiptap JSON review report from the contract review output, persist it as a Document
   * entity, and upload the content to storage.
   *
   * @param output the structured review output from the contract review AI skill
   * @param projectId the matter/project to attach the report to
   * @param executionId the AI execution ID for provenance tracking
   * @param memberId the member who invoked the review
   * @return the persisted Document entity
   */
  @Transactional
  public Document generateReviewReport(
      ContractReviewOutput output, UUID projectId, UUID executionId, UUID memberId) {

    String orgId = RequestScopes.requireOrgId();
    Map<String, Object> tiptapDoc = buildReportDocument(output);

    byte[] contentBytes = objectMapper.writeValueAsBytes(tiptapDoc);
    String fileName = "Contract Review Report - " + LocalDate.now() + ".json";

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
        "Generated contract review report document={} for project={} execution={}",
        saved.getId(),
        projectId,
        executionId);

    return saved;
  }

  // ── Tiptap Report Builder ──────────────────────────────────────────────────

  private Map<String, Object> buildReportDocument(ContractReviewOutput output) {
    var content = new ArrayList<Map<String, Object>>();

    // Title
    content.add(buildHeading("Contract Review Report", 1));

    // Document Classification
    content.add(buildHeading("Document Classification", 2));
    var classification = output.documentClassification();
    if (classification != null) {
      content.add(buildParagraph(classification.type() + " — " + classification.subtype()));
      if (classification.partiesIdentified() != null
          && !classification.partiesIdentified().isEmpty()) {
        content.add(
            buildBoldParagraph("Parties", String.join(", ", classification.partiesIdentified())));
      }
    }

    // Executive Summary
    content.add(buildHeading("Executive Summary", 2));
    content.add(buildParagraph(output.executiveSummary()));

    // Findings grouped by severity
    content.add(buildHeading("Findings", 2));
    buildFindingsSection(output.findings(), content);

    // Missing Protections
    content.add(buildHeading("Missing Protections", 2));
    if (output.missingProtections() != null) {
      for (var protection : output.missingProtections()) {
        content.add(buildHeading(protection.protection() + " (" + protection.priority() + ")", 4));
        content.add(buildParagraph(protection.reasoning()));
        content.add(buildBoldParagraph("Recommendation", protection.recommendation()));
      }
    }

    // Overall Risk Assessment
    content.add(buildHeading("Overall Risk Assessment", 2));
    content.add(buildParagraph(output.overallRiskAssessment()));

    // Recommended Actions
    content.add(buildHeading("Recommended Actions", 2));
    if (output.recommendedActions() != null && !output.recommendedActions().isEmpty()) {
      var items =
          output.recommendedActions().stream()
              .map(action -> action.action() + " — " + action.reasoning())
              .toList();
      content.add(buildBulletList(items));
    }

    return buildDocument(content);
  }

  private void buildFindingsSection(
      List<ContractReviewOutput.Finding> findings, List<Map<String, Object>> content) {
    if (findings == null || findings.isEmpty()) {
      return;
    }

    // Group by severity, preserving the defined order
    Map<String, List<ContractReviewOutput.Finding>> grouped =
        findings.stream()
            .sorted(
                Comparator.comparingInt(
                    f -> {
                      int idx = SEVERITY_ORDER.indexOf(f.severity());
                      return idx >= 0 ? idx : SEVERITY_ORDER.size();
                    }))
            .collect(
                Collectors.groupingBy(
                    ContractReviewOutput.Finding::severity,
                    LinkedHashMap::new,
                    Collectors.toList()));

    for (var entry : grouped.entrySet()) {
      String severity = entry.getKey();
      List<ContractReviewOutput.Finding> group = entry.getValue();

      content.add(buildHeading(severity + " Risk Findings", 3));

      for (var finding : group) {
        content.addAll(buildFindingSection(finding));
      }
    }
  }

  private List<Map<String, Object>> buildFindingSection(ContractReviewOutput.Finding finding) {
    var nodes = new ArrayList<Map<String, Object>>();

    nodes.add(buildHeading(finding.clauseReference() + " — " + finding.title(), 4));
    nodes.add(buildBoldParagraph("Category", finding.category()));
    nodes.add(buildParagraph(finding.description()));
    nodes.add(buildBoldParagraph("Risk", finding.riskExplanation()));
    nodes.add(buildBoldParagraph("Legal Basis", finding.statutoryReference()));
    nodes.add(buildBoldParagraph("Recommendation", finding.recommendation()));

    return nodes;
  }

  // ── Tiptap Node Builders ───────────────────────────────────────────────────

  private Map<String, Object> buildDocument(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "doc");
    node.put("content", content);
    return node;
  }

  private Map<String, Object> buildHeading(String text, int level) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "heading");
    node.put("attrs", Map.of("level", level));
    node.put("content", List.of(text(text)));
    return node;
  }

  private Map<String, Object> buildParagraph(String text) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    if (text != null && !text.isEmpty()) {
      node.put("content", List.of(text(text)));
    }
    return node;
  }

  private Map<String, Object> buildBoldParagraph(String label, String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    node.put("content", List.of(boldText(label + ": "), text(value)));
    return node;
  }

  private Map<String, Object> buildBulletList(List<String> items) {
    var listItems =
        items.stream()
            .map(
                item -> {
                  var listItem = new LinkedHashMap<String, Object>();
                  listItem.put("type", "listItem");
                  listItem.put("content", List.of(buildParagraph(item)));
                  return (Map<String, Object>) listItem;
                })
            .toList();

    var node = new LinkedHashMap<String, Object>();
    node.put("type", "bulletList");
    node.put("content", listItems);
    return node;
  }

  private static Map<String, Object> text(String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("text", value);
    return node;
  }

  private static Map<String, Object> boldText(String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("marks", List.of(Map.of("type", "bold")));
    node.put("text", value);
    return node;
  }
}
