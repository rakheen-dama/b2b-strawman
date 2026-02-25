package io.b2mash.b2b.b2bstrawman.setupstatus;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSetupStatusService {

  private final ProjectRepository projectRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final BillingRateRepository billingRateRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;

  public ProjectSetupStatusService(
      ProjectRepository projectRepository,
      CustomerProjectRepository customerProjectRepository,
      BillingRateRepository billingRateRepository,
      ProjectBudgetRepository projectBudgetRepository,
      ProjectMemberRepository projectMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository) {
    this.projectRepository = projectRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.billingRateRepository = billingRateRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
  }

  @Transactional(readOnly = true)
  public ProjectSetupStatus getSetupStatus(UUID projectId) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    boolean customerAssigned = !customerProjectRepository.findByProjectId(projectId).isEmpty();

    // Rate card: project-level overrides exist, OR org-level member defaults exist
    boolean rateCardConfigured =
        !billingRateRepository.findByFilters(null, projectId, null).isEmpty()
            || billingRateRepository.existsByProjectIdIsNullAndCustomerIdIsNull();

    boolean budgetConfigured = projectBudgetRepository.findByProjectId(projectId).isPresent();

    // Team: creator auto-added as PROJECT_LEAD, so >= 2 means at least one other member
    boolean teamAssigned = projectMemberRepository.findByProjectId(projectId).size() >= 2;

    var requiredFields = computeRequiredFields(EntityType.PROJECT, project.getCustomFields());

    // 5 checks, equal weight (20% each)
    boolean requiredFieldsPass =
        requiredFields.total() == 0 || requiredFields.filled() == requiredFields.total();
    int passCount =
        Stream.of(
                customerAssigned,
                rateCardConfigured,
                budgetConfigured,
                teamAssigned,
                requiredFieldsPass)
            .mapToInt(b -> b ? 1 : 0)
            .sum();

    int completionPercentage = (passCount * 100) / 5;
    boolean overallComplete = passCount == 5;

    return new ProjectSetupStatus(
        projectId,
        customerAssigned,
        rateCardConfigured,
        budgetConfigured,
        teamAssigned,
        requiredFields,
        completionPercentage,
        overallComplete);
  }

  private RequiredFieldStatus computeRequiredFields(
      EntityType entityType, Map<String, Object> customFields) {
    var requiredDefs =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType).stream()
            .filter(FieldDefinition::isRequired)
            .toList();

    var fieldStatuses =
        requiredDefs.stream()
            .map(
                fd -> {
                  Object value = customFields != null ? customFields.get(fd.getSlug()) : null;
                  boolean filled = isFieldValueFilled(fd, value);
                  return new FieldStatus(fd.getName(), fd.getSlug(), filled);
                })
            .toList();

    int filled = (int) fieldStatuses.stream().filter(FieldStatus::filled).count();
    return new RequiredFieldStatus(filled, fieldStatuses.size(), fieldStatuses);
  }

  private boolean isFieldValueFilled(FieldDefinition fd, Object value) {
    if (value == null) {
      return false;
    }
    if (fd.getFieldType() == FieldType.CURRENCY) {
      if (!(value instanceof Map<?, ?> map)) {
        return false;
      }
      var amount = map.get("amount");
      var currency = map.get("currency");
      return amount != null && currency != null && !currency.toString().isBlank();
    }
    return !value.toString().isBlank();
  }
}
