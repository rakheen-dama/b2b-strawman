package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * AI skill that verifies FICA/KYC document compliance for a customer. Reads customer documents from
 * S3, assembles a compliance-checking prompt, and creates execution gates for recommended actions.
 */
@Component
public class FicaVerificationSkill implements AiSkill {

  private static final Logger log = LoggerFactory.getLogger(FicaVerificationSkill.class);
  private static final String SKILL_ID = "fica-verification";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/fica-verification/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE =
      "ai/skills/fica-verification/output-schema.json";

  private final CustomerRepository customerRepository;
  private final DocumentRepository documentRepository;
  private final ChecklistInstanceRepository checklistInstanceRepository;
  private final ChecklistInstanceItemRepository checklistInstanceItemRepository;
  private final AiFirmProfileService firmProfileService;
  private final FicaDocumentReader ficaDocumentReader;
  private final ObjectMapper objectMapper;

  public FicaVerificationSkill(
      CustomerRepository customerRepository,
      DocumentRepository documentRepository,
      ChecklistInstanceRepository checklistInstanceRepository,
      ChecklistInstanceItemRepository checklistInstanceItemRepository,
      AiFirmProfileService firmProfileService,
      FicaDocumentReader ficaDocumentReader,
      ObjectMapper objectMapper) {
    this.customerRepository = customerRepository;
    this.documentRepository = documentRepository;
    this.checklistInstanceRepository = checklistInstanceRepository;
    this.checklistInstanceItemRepository = checklistInstanceItemRepository;
    this.firmProfileService = firmProfileService;
    this.ficaDocumentReader = ficaDocumentReader;
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
    UUID customerId = context.entityId();

    // Pre-flight: customer must exist
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Pre-flight: customer must have uploaded documents
    List<Document> allDocs =
        documentRepository.findByScopeAndCustomerId(Document.Scope.CUSTOMER, customerId);
    List<Document> uploadedDocs =
        allDocs.stream().filter(d -> d.getStatus() == Document.Status.UPLOADED).toList();
    if (uploadedDocs.isEmpty()) {
      throw new InvalidStateException(
          "No documents", "Customer has no uploaded documents for FICA verification");
    }

    // Pre-flight: customer must have an active checklist with PENDING items
    List<ChecklistInstance> instances = checklistInstanceRepository.findByCustomerId(customerId);
    ChecklistInstance activeInstance =
        instances.stream()
            .filter(i -> "IN_PROGRESS".equals(i.getStatus()))
            .findFirst()
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "No active checklist",
                        "Customer has no active compliance checklist with PENDING items"));

    List<ChecklistInstanceItem> allItems =
        checklistInstanceItemRepository.findByInstanceIdOrderBySortOrder(activeInstance.getId());
    List<ChecklistInstanceItem> pendingItems =
        allItems.stream().filter(i -> "PENDING".equals(i.getStatus())).toList();
    if (pendingItems.isEmpty()) {
      throw new InvalidStateException(
          "No active checklist", "Customer has no active compliance checklist with PENDING items");
    }

    // Read documents (text extraction + vision fallback)
    FicaDocumentReader.DocumentReadResult readResult =
        ficaDocumentReader.readDocuments(uploadedDocs);

    // Assemble user prompt
    var prompt = new StringBuilder();
    prompt.append("<customer>\n");
    prompt.append("Name: ").append(customer.getName()).append("\n");
    String entityType =
        customer.getCustomFields().getOrDefault("acct_entity_type", "INDIVIDUAL").toString();
    prompt.append("Type: ").append(entityType).append("\n");
    prompt.append("</customer>\n\n");

    prompt.append("<checklist template=\"").append(activeInstance.getTemplateId()).append("\">\n");
    for (ChecklistInstanceItem item : allItems) {
      prompt
          .append("- [")
          .append(item.getStatus())
          .append("] ")
          .append(item.getName())
          .append(" (requires document: ")
          .append(item.isRequiresDocument())
          .append(") id=")
          .append(item.getId())
          .append("\n");
    }
    prompt.append("</checklist>\n\n");

    prompt.append("<documents>\n");
    prompt.append(readResult.textContent());
    prompt.append("</documents>\n\n");

    prompt.append(
        "Review the uploaded documents against the FICA checklist items listed above. "
            + "For each checklist item, assess whether the uploaded documents satisfy the requirement. "
            + "Produce your response as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    FicaVerificationOutput output;
    try {
      output = objectMapper.readValue(outputContent, FicaVerificationOutput.class);
    } catch (JacksonException e) {
      throw new InvalidStateException(
          "AI response parse failed",
          "AI response could not be parsed as valid FICA verification output: " + e.getMessage());
    }

    List<AiExecutionGate> gates = new ArrayList<>();
    for (FicaVerificationOutput.RecommendedAction action : output.recommendedActions()) {
      if ("MARK_ITEMS_COMPLETE".equals(action.action())) {
        var proposedAction =
            Map.<String, Object>of(
                "checklist_item_ids",
                action.items().stream().map(UUID::toString).toList(),
                "completion_notes",
                "AI-verified: " + action.reasoning());
        var gate =
            new AiExecutionGate(
                execution,
                "MARK_KYC_COMPLETE",
                proposedAction,
                action.reasoning(),
                Instant.now().plus(Duration.ofHours(72)));
        gates.add(gate);
      }
      // REQUEST_ADDITIONAL_DOCUMENT actions are informational — no gate created
    }
    return gates;
  }

  @Override
  public boolean requiresVision() {
    return false;
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
