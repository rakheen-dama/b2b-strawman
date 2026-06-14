package io.b2mash.b2b.b2bstrawman.integration.ai.skill.complianceaudit;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.LlmJsonParser;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ComplianceAuditSkill implements AiSkill {

  private static final Logger log = LoggerFactory.getLogger(ComplianceAuditSkill.class);
  private static final String SKILL_ID = "compliance-audit";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/compliance-audit/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE =
      "ai/skills/compliance-audit/output-schema.json";

  private final ComplianceDataCollectorService complianceDataCollectorService;
  private final AiExecutionRepository aiExecutionRepository;
  private final AiFirmProfileService firmProfileService;
  private final ObjectMapper objectMapper;
  private final LlmJsonParser llmJsonParser;

  public ComplianceAuditSkill(
      ComplianceDataCollectorService complianceDataCollectorService,
      AiExecutionRepository aiExecutionRepository,
      AiFirmProfileService firmProfileService,
      ObjectMapper objectMapper,
      LlmJsonParser llmJsonParser) {
    this.complianceDataCollectorService = complianceDataCollectorService;
    this.aiExecutionRepository = aiExecutionRepository;
    this.firmProfileService = firmProfileService;
    this.objectMapper = objectMapper;
    this.llmJsonParser = llmJsonParser;
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

    return systemTemplate
        .replace("{firm_profile_block}", profileBlock)
        .replace("{output_schema}", outputSchema);
  }

  @Override
  public String assembleUserPrompt(SkillContext context) {
    // Concurrent audit prevention: reject if another audit is already in progress.
    // Note: the current execution (created moments before this method is called) is already
    // IN_PROGRESS in the same transaction, so we check for MORE THAN 1.
    List<AiExecution> inProgress =
        aiExecutionRepository.findBySkillIdAndStatusForUpdate(SKILL_ID, "IN_PROGRESS");
    if (inProgress.size() > 1) {
      throw new ResourceConflictException(
          "Concurrent audit", "A compliance audit is already in progress");
    }

    ComplianceSnapshot snapshot = complianceDataCollectorService.collectComplianceSnapshot();

    var prompt = new StringBuilder();
    prompt.append("<compliance-data>\n");
    prompt
        .append("Total active customers: ")
        .append(snapshot.totalActiveCustomers())
        .append("\n\n");

    // FICA CDD section
    prompt.append("<fica-cdd>\n");
    prompt.append("Compliant: ").append(snapshot.ficaCdd().compliant()).append("\n");
    prompt.append("Non-compliant: ").append(snapshot.ficaCdd().nonCompliant()).append("\n");
    prompt
        .append("Critically overdue: ")
        .append(snapshot.ficaCdd().criticallyOverdue())
        .append("\n");
    if (!snapshot.ficaCdd().flaggedCustomers().isEmpty()) {
      prompt.append("Flagged customers:\n");
      for (var flagged : snapshot.ficaCdd().flaggedCustomers()) {
        prompt
            .append("  - ")
            .append(flagged.name())
            .append(" (")
            .append(flagged.issue())
            .append(")\n");
      }
    }
    prompt.append("</fica-cdd>\n\n");

    // POPIA section
    prompt.append("<popia>\n");
    prompt
        .append("Registered activities: ")
        .append(snapshot.popia().registeredActivities())
        .append("\n");
    prompt
        .append("Unregistered activities: ")
        .append(snapshot.popia().unregisteredActivities())
        .append("\n");
    prompt.append("Pending DSARs: ").append(snapshot.popia().pendingDsars()).append("\n");
    prompt.append("Overdue DSARs: ").append(snapshot.popia().overdueDsars()).append("\n");
    prompt.append("</popia>\n\n");

    // Trust Accounting section
    prompt.append("<trust-accounting>\n");
    prompt
        .append("Module enabled: ")
        .append(snapshot.trustAccounting().moduleEnabled())
        .append("\n");
    if (snapshot.trustAccounting().moduleEnabled()) {
      prompt
          .append("Account count: ")
          .append(snapshot.trustAccounting().accountCount())
          .append("\n");
      prompt
          .append("Unreconciled items: ")
          .append(snapshot.trustAccounting().unreconciledItems())
          .append("\n");
      if (!snapshot.trustAccounting().boundaryViolations().isEmpty()) {
        prompt.append("Boundary violations:\n");
        for (var violation : snapshot.trustAccounting().boundaryViolations()) {
          prompt.append("  - ").append(violation).append("\n");
        }
      }
    }
    prompt.append("</trust-accounting>\n\n");

    // Prescription section
    prompt.append("<prescription>\n");
    prompt.append("Module enabled: ").append(snapshot.prescription().moduleEnabled()).append("\n");
    if (snapshot.prescription().moduleEnabled()) {
      prompt
          .append("Approaching expiry: ")
          .append(snapshot.prescription().approachingCount())
          .append("\n");
      prompt.append("Expired: ").append(snapshot.prescription().expiredCount()).append("\n");
      if (!snapshot.prescription().flaggedMatters().isEmpty()) {
        prompt.append("Flagged matters:\n");
        for (var matter : snapshot.prescription().flaggedMatters()) {
          prompt
              .append("  - ")
              .append(matter.name())
              .append(" (prescription: ")
              .append(matter.prescriptionDate())
              .append(", ")
              .append(matter.issue())
              .append(")\n");
        }
      }
    }
    prompt.append("</prescription>\n\n");

    // Retention section
    prompt.append("<retention>\n");
    prompt
        .append("Approaching expiry: ")
        .append(snapshot.retention().approachingExpiry())
        .append("\n");
    prompt.append("Past expiry: ").append(snapshot.retention().pastExpiry()).append("\n");
    prompt.append("</retention>\n");

    prompt.append("</compliance-data>\n\n");

    if (snapshot.dataCollectionNotes() != null && !snapshot.dataCollectionNotes().isEmpty()) {
      prompt.append("<notes>").append(snapshot.dataCollectionNotes()).append("</notes>\n\n");
    }

    prompt.append(
        "Conduct a comprehensive compliance audit of the firm's data above against the "
            + "South African regulatory framework. Grade each compliance category and provide "
            + "specific findings with remediation steps. Produce your response as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    ComplianceAuditOutput output =
        llmJsonParser.parse(objectMapper, outputContent, ComplianceAuditOutput.class);

    // Wrap the output under audit_output key for the gate executor
    Map<String, Object> auditOutput =
        objectMapper.convertValue(output, new TypeReference<Map<String, Object>>() {});
    Map<String, Object> proposedAction = Map.of("audit_output", auditOutput);

    String reasoning = buildGateReasoning(output);

    var gate =
        new AiExecutionGate(
            execution,
            "PUBLISH_COMPLIANCE_REPORT",
            proposedAction,
            reasoning,
            Instant.now().plus(Duration.ofHours(72)));

    return List.of(gate);
  }

  @Override
  public boolean requiresVision() {
    return false;
  }

  private String buildGateReasoning(ComplianceAuditOutput output) {
    var sb = new StringBuilder();
    sb.append("Compliance audit completed. Overall grade: ").append(output.overallGrade());
    sb.append(". Findings: ").append(output.findings() != null ? output.findings().size() : 0);
    sb.append(". Recommendations: ");
    sb.append(output.recommendations() != null ? output.recommendations().size() : 0);
    sb.append(".");
    return sb.toString();
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
