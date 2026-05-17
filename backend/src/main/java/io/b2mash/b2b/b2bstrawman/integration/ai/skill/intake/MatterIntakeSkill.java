package io.b2mash.b2b.b2bstrawman.integration.ai.skill.intake;

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
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffSchedule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffScheduleRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * AI skill that performs matter intake analysis — classifying matter type, recommending templates,
 * screening for conflicts, and estimating fees based on the LSSA tariff schedule.
 */
@Component
public class MatterIntakeSkill implements AiSkill {

  private static final Logger log = LoggerFactory.getLogger(MatterIntakeSkill.class);
  private static final String SKILL_ID = "matter-intake";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/matter-intake/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE = "ai/skills/matter-intake/output-schema.json";

  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final ProjectTemplateRepository projectTemplateRepository;
  private final TariffScheduleRepository tariffScheduleRepository;
  private final AiFirmProfileService firmProfileService;
  private final ObjectMapper objectMapper;

  public MatterIntakeSkill(
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      ProjectTemplateRepository projectTemplateRepository,
      TariffScheduleRepository tariffScheduleRepository,
      AiFirmProfileService firmProfileService,
      ObjectMapper objectMapper) {
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.projectTemplateRepository = projectTemplateRepository;
    this.tariffScheduleRepository = tariffScheduleRepository;
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
    UUID customerId = context.entityId();

    // Pre-flight: description must be >= 20 characters
    if (context.description() == null || context.description().length() < 20) {
      throw new InvalidStateException(
          "Description too short",
          "Matter description must be at least 20 characters for meaningful intake analysis");
    }

    // Pre-flight: customer must exist
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Load active templates
    List<ProjectTemplate> templates = projectTemplateRepository.findByActiveOrderByNameAsc(true);

    // Load recent projects (up to 500, sorted by createdAt DESC)
    List<Project> allProjects = projectRepository.findAll();
    List<Project> recentProjects =
        allProjects.stream()
            .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
            .limit(500)
            .toList();

    // Batch-load customer names for project display
    Set<UUID> projectCustomerIds =
        recentProjects.stream()
            .map(Project::getCustomerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> customerNames =
        projectCustomerIds.isEmpty()
            ? Map.of()
            : customerRepository.findByIdIn(projectCustomerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

    // Load active tariff schedule
    List<TariffSchedule> allSchedules = tariffScheduleRepository.findAll();
    TariffSchedule activeSchedule =
        allSchedules.stream().filter(TariffSchedule::isActive).findFirst().orElse(null);

    // Count existing matters for this customer
    long existingMatterCount = projectRepository.countByCustomerId(customerId);

    // Assemble user prompt
    var prompt = new StringBuilder();

    prompt.append("<matter-description>\n");
    prompt.append(context.description()).append("\n");
    prompt.append("</matter-description>\n\n");

    prompt.append("<customer>\n");
    prompt.append("Name: ").append(customer.getName()).append("\n");
    String entityType = customer.getEntityType() != null ? customer.getEntityType() : "INDIVIDUAL";
    prompt.append("Type: ").append(entityType).append("\n");
    prompt.append("Existing matters: ").append(existingMatterCount).append("\n");
    prompt.append("</customer>\n\n");

    prompt.append("<available-templates>\n");
    for (ProjectTemplate t : templates) {
      prompt
          .append("- id=")
          .append(t.getId())
          .append(" name=\"")
          .append(t.getName())
          .append("\" description=\"")
          .append(t.getDescription() != null ? t.getDescription() : "")
          .append("\"\n");
    }
    prompt.append("</available-templates>\n\n");

    prompt.append("<active-matters count=\"").append(recentProjects.size()).append("\">\n");
    for (Project project : recentProjects) {
      String customerName =
          project.getCustomerId() != null
              ? customerNames.getOrDefault(project.getCustomerId(), "Unknown")
              : "Unknown";
      prompt
          .append("- \"")
          .append(project.getName())
          .append("\" for ")
          .append(customerName)
          .append("\n");
    }
    prompt.append("</active-matters>\n\n");

    if (activeSchedule != null) {
      prompt
          .append("<tariff-schedule jurisdiction=\"")
          .append(activeSchedule.getName())
          .append(" / ")
          .append(activeSchedule.getCategory())
          .append("\">\n");
      for (TariffItem item : activeSchedule.getItems()) {
        prompt
            .append("- ")
            .append(item.getItemNumber())
            .append(" ")
            .append(item.getSection())
            .append(": ")
            .append(item.getDescription())
            .append(" — R")
            .append(item.getAmount())
            .append(" per ")
            .append(item.getUnit())
            .append("\n");
      }
      prompt.append("</tariff-schedule>\n\n");
    }

    prompt.append("Analyse this new matter and provide recommendations as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(AiExecution execution, String outputContent) {
    MatterIntakeOutput output;
    try {
      output = objectMapper.readValue(outputContent, MatterIntakeOutput.class);
    } catch (JacksonException e) {
      throw new InvalidStateException(
          "AI response parse failed",
          "AI response could not be parsed as valid matter intake output: " + e.getMessage());
    }

    List<AiExecutionGate> gates = new ArrayList<>();

    // Gate 1: SELECT_MATTER_TEMPLATE — create if template recommendation exists with a templateId
    if (output.templateRecommendation() != null
        && output.templateRecommendation().templateId() != null) {
      String customisationNotes =
          output.templateRecommendation().customisationNotes() != null
              ? output.templateRecommendation().customisationNotes()
              : "";
      var proposedAction =
          Map.<String, Object>of(
              "template_id",
              output.templateRecommendation().templateId().toString(),
              "customisation_notes",
              customisationNotes);
      var gate =
          new AiExecutionGate(
              execution,
              "SELECT_MATTER_TEMPLATE",
              proposedAction,
              output.templateRecommendation().reasoning(),
              Instant.now().plus(Duration.ofHours(72)));
      gates.add(gate);
    }

    // Gate 2: CONFIRM_CONFLICT_SCREEN — create only for CLEAR or POTENTIAL_CONFLICT
    if (output.conflictScreening() != null) {
      String status = output.conflictScreening().status();
      if ("CLEAR".equals(status) || "POTENTIAL_CONFLICT".equals(status)) {
        var proposedAction = Map.<String, Object>of("conflict_status", status);
        var gate =
            new AiExecutionGate(
                execution,
                "CONFIRM_CONFLICT_SCREEN",
                proposedAction,
                "Conflict screening status: " + status,
                Instant.now().plus(Duration.ofHours(72)));
        gates.add(gate);
      }
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
