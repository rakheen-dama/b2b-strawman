package io.b2mash.b2b.b2bstrawman.integration.ai.skill.contractreview;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.DocumentTextExtractorService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.ExtractedText;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AI skill that reviews contracts against South African legal frameworks. Extracts text from an
 * uploaded document (PDF/DOCX), auto-classifies the review type from content and matter type, and
 * creates an execution gate for the review report.
 */
@Component
public class ContractReviewSkill implements AiSkill {

  private static final Logger log = LoggerFactory.getLogger(ContractReviewSkill.class);
  private static final String SKILL_ID = "contract-review";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/contract-review/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE =
      "ai/skills/contract-review/output-schema.json";

  private final DocumentTextExtractorService documentTextExtractorService;
  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final AiFirmProfileService firmProfileService;
  private final ObjectMapper objectMapper;

  public ContractReviewSkill(
      DocumentTextExtractorService documentTextExtractorService,
      DocumentRepository documentRepository,
      ProjectRepository projectRepository,
      AiFirmProfileService firmProfileService,
      ObjectMapper objectMapper) {
    this.documentTextExtractorService = documentTextExtractorService;
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.firmProfileService = firmProfileService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String skillId() {
    return SKILL_ID;
  }

  @Override
  public String assembleSystemPrompt(AiFirmProfile profile) {
    String systemTemplate = loadClasspathResource(SYSTEM_PROMPT_RESOURCE);
    String outputSchema = loadClasspathResource(OUTPUT_SCHEMA_RESOURCE);
    String profileBlock = firmProfileService.assembleProfileBlock();
    String riskCalibration =
        profile.getRiskCalibration() != null ? profile.getRiskCalibration() : "MODERATE";

    return systemTemplate
        .replace("{firm_profile_block}", profileBlock)
        .replace("{output_schema}", outputSchema)
        .replace("{risk_calibration}", riskCalibration);
  }

  @Override
  public String assembleUserPrompt(SkillContext context) {
    UUID documentId = context.entityId();

    // Pre-flight: document must exist
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

    // Extract text from the document (throws InvalidStateException for unsupported types)
    ExtractedText extractedText = documentTextExtractorService.extractText(document);

    // Load matter context if projectId is provided
    String matterContext = "";
    String matterType = "";
    Object projectIdObj = context.additionalContext().get("projectId");
    if (projectIdObj != null) {
      UUID projectId;
      try {
        projectId = projectIdObj instanceof UUID u ? u : UUID.fromString(projectIdObj.toString());
      } catch (IllegalArgumentException e) {
        throw new InvalidStateException("INVALID_PROJECT_ID", "projectId must be a valid UUID");
      }
      Project project =
          projectRepository
              .findById(projectId)
              .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
      matterContext = buildMatterContext(project);
      matterType = project.getWorkType() != null ? project.getWorkType() : "";
    }

    // Auto-classify review type from document content and matter type
    String reviewType = classifyReviewType(extractedText.content(), matterType);

    // Assemble user prompt
    var prompt = new StringBuilder();
    prompt.append("<document>\n");
    prompt.append("File: ").append(document.getFileName()).append("\n");
    prompt.append("Type: ").append(document.getContentType()).append("\n");
    if (extractedText.wasTruncated()) {
      prompt.append("Note: ").append(extractedText.truncationWarning()).append("\n");
    }
    prompt.append("</document>\n\n");

    if (!matterContext.isEmpty()) {
      prompt.append(matterContext).append("\n");
    }

    prompt.append("<review-type>").append(reviewType).append("</review-type>\n\n");

    prompt.append("<contract-text>\n");
    prompt.append(extractedText.content());
    prompt.append("\n</contract-text>\n\n");

    prompt.append(
        "Review the contract text above against the applicable South African legal framework "
            + "for the identified review type. Identify risks, missing protections, and "
            + "recommended actions. Produce your response as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    ContractReviewOutput output;
    try {
      output = objectMapper.readValue(outputContent, ContractReviewOutput.class);
    } catch (JacksonException e) {
      throw new InvalidStateException(
          "AI response parse failed",
          "AI response could not be parsed as valid contract review output: " + e.getMessage());
    }

    // Extract projectId: prefer context, fall back to document's project
    Object projectIdObj = context.additionalContext().get("projectId");
    UUID projectId;
    if (projectIdObj != null) {
      projectId = projectIdObj instanceof UUID u ? u : UUID.fromString(projectIdObj.toString());
    } else {
      // Fall back: look up the document to find its project
      UUID documentId = execution.getEntityId();
      Document document =
          documentRepository
              .findById(documentId)
              .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
      projectId = document.getProjectId();
    }

    // Wrap the output with context IDs needed by the gate executor
    UUID documentId = execution.getEntityId();
    Map<String, Object> reviewOutput =
        objectMapper.convertValue(output, new TypeReference<Map<String, Object>>() {});
    Map<String, Object> proposedAction =
        Map.of(
            "project_id", projectId.toString(),
            "document_id", documentId.toString(),
            "review_output", reviewOutput);

    var gate =
        new AiExecutionGate(
            execution,
            "CREATE_REVIEW_REPORT",
            proposedAction,
            output.executiveSummary(),
            Instant.now().plus(Duration.ofHours(72)));

    return List.of(gate);
  }

  @Override
  public boolean requiresVision() {
    return false;
  }

  /**
   * Auto-classifies the review type based on document content keywords and matter type. Falls back
   * to GENERAL when uncertain.
   */
  private String classifyReviewType(String content, String matterType) {
    String lowerContent = content.toLowerCase();
    String lowerMatterType = matterType.toLowerCase();

    // Employment indicators
    if (lowerContent.contains("employment")
        || lowerContent.contains("employee")
        || lowerContent.contains("employer")
        || lowerContent.contains("restraint of trade")
        || lowerContent.contains("bcea")
        || lowerMatterType.contains("employment")
        || lowerMatterType.contains("labour")) {
      return "EMPLOYMENT_CONTRACT";
    }

    // Corporate document indicators
    if (lowerContent.contains("memorandum of incorporation")
        || lowerContent.contains("shareholders agreement")
        || lowerContent.contains("board resolution")
        || lowerContent.contains("companies act")
        || lowerMatterType.contains("corporate")
        || lowerMatterType.contains("company")) {
      return "CORPORATE_DOCUMENT";
    }

    // Commercial contract indicators
    if (lowerContent.contains("service level agreement")
        || lowerContent.contains("supply agreement")
        || lowerContent.contains("lease agreement")
        || lowerContent.contains("consumer protection")
        || lowerContent.contains("purchase")
        || lowerContent.contains("vendor")
        || lowerMatterType.contains("commercial")
        || lowerMatterType.contains("contract")) {
      return "COMMERCIAL_CONTRACT";
    }

    return "GENERAL";
  }

  private String buildMatterContext(Project project) {
    var ctx = new StringBuilder();
    ctx.append("<matter>\n");
    ctx.append("Name: ").append(project.getName()).append("\n");
    if (project.getDescription() != null) {
      ctx.append("Description: ").append(project.getDescription()).append("\n");
    }
    ctx.append("Status: ").append(project.getStatus()).append("\n");
    if (project.getWorkType() != null) {
      ctx.append("Work type: ").append(project.getWorkType()).append("\n");
    }
    ctx.append("</matter>\n");
    return ctx.toString();
  }

  private String loadClasspathResource(String path) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalStateException("Classpath resource not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load classpath resource: " + path, e);
    }
  }
}
