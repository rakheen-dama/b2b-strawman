package io.b2mash.b2b.b2bstrawman.integration.ai.skill.drafting;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
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
 * AI skill that performs template-guided document drafting. Assembles template structure, variable
 * metadata, matter context, customer context, and clause library information into prompts for the
 * AI to fill template variables, generate narrative sections, and recommend clauses.
 */
@Component
public class DraftingSkill implements AiSkill {

  private static final Logger log = LoggerFactory.getLogger(DraftingSkill.class);
  private static final String SKILL_ID = "drafting";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/drafting/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE = "ai/skills/drafting/output-schema.json";

  private final DocumentTemplateRepository documentTemplateRepository;
  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final ClauseRepository clauseRepository;
  private final AiFirmProfileService firmProfileService;
  private final ObjectMapper objectMapper;

  public DraftingSkill(
      DocumentTemplateRepository documentTemplateRepository,
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      ClauseRepository clauseRepository,
      AiFirmProfileService firmProfileService,
      ObjectMapper objectMapper) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.clauseRepository = clauseRepository;
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

    return systemTemplate
        .replace("{firm_profile_block}", profileBlock)
        .replace("{output_schema}", outputSchema);
  }

  @Override
  public String assembleUserPrompt(SkillContext context) {
    UUID projectId = context.entityId();

    // Load project (the matter being drafted for)
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    // Load template from additional context
    Object templateIdObj = context.additionalContext().get("templateId");
    if (templateIdObj == null) {
      throw new InvalidStateException(
          "MISSING_TEMPLATE_ID", "templateId is required in additionalContext");
    }
    UUID templateId;
    try {
      templateId = templateIdObj instanceof UUID u ? u : UUID.fromString(templateIdObj.toString());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("INVALID_TEMPLATE_ID", "templateId must be a valid UUID");
    }

    DocumentTemplate template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    // Load customer if linked to project
    Customer customer = null;
    if (project.getCustomerId() != null) {
      customer = customerRepository.findById(project.getCustomerId()).orElse(null);
    }

    // Load active clauses for the clause library context
    List<Clause> activeClauses = clauseRepository.findByActiveTrueOrderByCategoryAscSortOrderAsc();

    // Assemble user prompt
    var prompt = new StringBuilder();

    // Template structure and metadata
    prompt.append("<template>\n");
    prompt.append("Name: ").append(template.getName()).append("\n");
    prompt.append("ID: ").append(template.getId()).append("\n");
    if (template.getDescription() != null) {
      prompt.append("Description: ").append(template.getDescription()).append("\n");
    }
    prompt.append("Category: ").append(template.getCategory()).append("\n");
    prompt.append("Format: ").append(template.getFormat()).append("\n");

    // Variable definitions from required context fields
    if (template.getRequiredContextFields() != null
        && !template.getRequiredContextFields().isEmpty()) {
      prompt.append("\nRequired variables:\n");
      for (Map<String, String> field : template.getRequiredContextFields()) {
        prompt.append("  - entity: ").append(field.get("entity"));
        prompt.append(", field: ").append(field.get("field")).append("\n");
      }
    }

    // Discovered fields from template content
    if (template.getDiscoveredFields() != null && !template.getDiscoveredFields().isEmpty()) {
      prompt.append("\nDiscovered template fields:\n");
      for (Map<String, Object> field : template.getDiscoveredFields()) {
        prompt.append("  - ").append(field.getOrDefault("name", "unknown")).append("\n");
      }
    }

    prompt.append("</template>\n\n");

    // Matter context
    prompt.append("<matter>\n");
    prompt.append("Name: ").append(project.getName()).append("\n");
    if (project.getDescription() != null) {
      prompt.append("Description: ").append(project.getDescription()).append("\n");
    }
    prompt.append("Status: ").append(project.getStatus()).append("\n");
    if (project.getWorkType() != null) {
      prompt.append("Work type: ").append(project.getWorkType()).append("\n");
    }
    if (project.getReferenceNumber() != null) {
      prompt.append("Reference: ").append(project.getReferenceNumber()).append("\n");
    }
    prompt.append("</matter>\n\n");

    // Customer context
    if (customer != null) {
      prompt.append("<customer>\n");
      prompt.append("Name: ").append(customer.getName()).append("\n");
      if (customer.getCustomerType() != null) {
        prompt.append("Type: ").append(customer.getCustomerType()).append("\n");
      }
      if (customer.getEmail() != null) {
        prompt.append("Email: ").append(customer.getEmail()).append("\n");
      }
      if (customer.getPhone() != null) {
        prompt.append("Phone: ").append(customer.getPhone()).append("\n");
      }
      if (customer.getIdNumber() != null) {
        prompt.append("ID number: ").append(customer.getIdNumber()).append("\n");
      }
      if (customer.getRegistrationNumber() != null) {
        prompt
            .append("Registration number: ")
            .append(customer.getRegistrationNumber())
            .append("\n");
      }
      if (customer.getAddressLine1() != null) {
        prompt.append("Address: ").append(customer.getAddressLine1());
        if (customer.getAddressLine2() != null) {
          prompt.append(", ").append(customer.getAddressLine2());
        }
        if (customer.getCity() != null) {
          prompt.append(", ").append(customer.getCity());
        }
        if (customer.getStateProvince() != null) {
          prompt.append(", ").append(customer.getStateProvince());
        }
        if (customer.getPostalCode() != null) {
          prompt.append(", ").append(customer.getPostalCode());
        }
        prompt.append("\n");
      }
      if (customer.getTaxNumber() != null) {
        prompt.append("Tax number: ").append(customer.getTaxNumber()).append("\n");
      }
      if (customer.getContactName() != null) {
        prompt.append("Contact person: ").append(customer.getContactName()).append("\n");
      }
      prompt.append("</customer>\n\n");
    }

    // Clause library summary
    if (!activeClauses.isEmpty()) {
      prompt.append("<clause-library>\n");
      prompt.append("Available clauses for recommendation:\n");
      for (Clause clause : activeClauses) {
        prompt.append("  - ID: ").append(clause.getId());
        prompt.append(", Title: ").append(clause.getTitle());
        prompt.append(", Category: ").append(clause.getCategory());
        if (clause.getDescription() != null) {
          prompt.append(", Description: ").append(clause.getDescription());
        }
        prompt.append("\n");
      }
      prompt.append("</clause-library>\n\n");
    }

    prompt.append(
        "Draft the document by filling all template variables from the provided context, "
            + "generating narrative sections where required, and recommending appropriate clauses. "
            + "Produce your response as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    DraftingOutput output;
    try {
      output = objectMapper.readValue(outputContent, DraftingOutput.class);
    } catch (JacksonException e) {
      throw new InvalidStateException(
          "AI response parse failed",
          "AI response could not be parsed as valid drafting output: " + e.getMessage());
    }

    // Extract templateId from execution context (set by controller)
    Object templateIdObj = context.additionalContext().get("templateId");
    if (templateIdObj == null) {
      throw new InvalidStateException(
          "Missing context", "templateId is required for drafting skill");
    }
    UUID templateId =
        templateIdObj instanceof UUID u ? u : UUID.fromString(templateIdObj.toString());
    UUID projectId = execution.getEntityId();

    // Wrap the output with context IDs needed by the gate executor
    Map<String, Object> draftOutput =
        objectMapper.convertValue(output, new TypeReference<Map<String, Object>>() {});
    Map<String, Object> proposedAction =
        Map.of(
            "template_id", templateId.toString(),
            "project_id", projectId.toString(),
            "draft_output", draftOutput);

    String reasoning = buildGateReasoning(output);

    var gate =
        new AiExecutionGate(
            execution,
            "CREATE_DRAFT_DOCUMENT",
            proposedAction,
            reasoning,
            Instant.now().plus(Duration.ofHours(72)));

    return List.of(gate);
  }

  @Override
  public boolean requiresVision() {
    return false;
  }

  private String buildGateReasoning(DraftingOutput output) {
    var sb = new StringBuilder();
    sb.append("Drafted document with ");
    sb.append(output.variableFills() != null ? output.variableFills().size() : 0);
    sb.append(" variable fills, ");
    sb.append(output.narrativeSections() != null ? output.narrativeSections().size() : 0);
    sb.append(" narrative sections, and ");
    sb.append(output.clauseRecommendations() != null ? output.clauseRecommendations().size() : 0);
    sb.append(" clause recommendations.");
    if (output.warnings() != null && !output.warnings().isEmpty()) {
      sb.append(" Warnings: ").append(String.join("; ", output.warnings()));
    }
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
