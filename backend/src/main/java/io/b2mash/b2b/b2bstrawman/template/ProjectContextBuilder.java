package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectContextBuilder implements TemplateContextBuilder {

  private final ProjectRepository projectRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final TemplateContextHelper contextHelper;

  public ProjectContextBuilder(
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectMemberRepository projectMemberRepository,
      ProjectBudgetRepository projectBudgetRepository,
      TemplateContextHelper contextHelper) {
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.contextHelper = contextHelper;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.PROJECT;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var project =
        projectRepository
            .findById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", entityId));

    var context = new HashMap<String, Object>();
    var fieldDefCache = new EnumMap<EntityType, List<FieldDefinition>>(EntityType.class);

    // project.*
    var projectMap = new LinkedHashMap<String, Object>();
    projectMap.put("id", project.getId());
    projectMap.put("name", project.getName());
    projectMap.put("description", project.getDescription());
    projectMap.put(
        "createdAt", project.getCreatedAt() != null ? project.getCreatedAt().toString() : null);
    projectMap.put(
        "customFields",
        contextHelper.resolveDropdownLabels(
            project.getCustomFields() != null ? project.getCustomFields() : Map.of(),
            EntityType.PROJECT,
            fieldDefCache));
    context.put("project", projectMap);

    // customer.* (via CustomerProject join table, fallback to project.customerId)
    var customerProjects = customerProjectRepository.findByProjectId(entityId);
    UUID resolvedCustomerId = null;
    if (!customerProjects.isEmpty()) {
      resolvedCustomerId = customerProjects.getFirst().getCustomerId();
    } else if (project.getCustomerId() != null) {
      // Fallback: project was created with customerId but no join record
      resolvedCustomerId = project.getCustomerId();
    }

    if (resolvedCustomerId != null) {
      customerRepository
          .findById(resolvedCustomerId)
          .ifPresentOrElse(
              customer -> {
                var customerMap = new LinkedHashMap<String, Object>();
                customerMap.put("id", customer.getId());
                customerMap.put("name", customer.getName());
                customerMap.put("email", customer.getEmail());
                customerMap.put(
                    "customFields",
                    contextHelper.resolveDropdownLabels(
                        customer.getCustomFields() != null ? customer.getCustomFields() : Map.of(),
                        EntityType.CUSTOMER,
                        fieldDefCache));
                context.put("customer", customerMap);
              },
              () -> context.put("customer", null));
    } else {
      context.put("customer", null);
    }

    // lead.* and members[]
    var memberInfos = projectMemberRepository.findProjectMembersWithDetails(entityId);
    var membersList =
        memberInfos.stream()
            .map(
                mi -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", mi.memberId());
                  m.put("name", mi.name());
                  m.put("email", mi.email());
                  m.put("role", mi.projectRole());
                  return (Map<String, Object>) m;
                })
            .toList();
    context.put("members", membersList);

    // lead = first member with role "lead"
    memberInfos.stream()
        .filter(mi -> "lead".equalsIgnoreCase(mi.projectRole()))
        .findFirst()
        .ifPresentOrElse(
            lead -> {
              var leadMap = new LinkedHashMap<String, Object>();
              leadMap.put("id", lead.memberId());
              leadMap.put("name", lead.name());
              leadMap.put("email", lead.email());
              context.put("lead", leadMap);
            },
            () -> context.put("lead", null));

    // org.*
    context.put("org", contextHelper.buildOrgContext());

    // budget.*
    projectBudgetRepository
        .findByProjectId(entityId)
        .ifPresentOrElse(
            budget -> context.put("budget", buildBudgetMap(budget)),
            () -> context.put("budget", null));

    // tags[]
    context.put("tags", contextHelper.buildTagsList("PROJECT", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", contextHelper.buildGeneratedByMap(memberId));

    return context;
  }

  private Map<String, Object> buildBudgetMap(ProjectBudget budget) {
    var budgetMap = new LinkedHashMap<String, Object>();
    budgetMap.put("hours", budget.getBudgetHours());
    budgetMap.put("amount", budget.getBudgetAmount());
    budgetMap.put("currency", budget.getBudgetCurrency());
    return budgetMap;
  }
}
