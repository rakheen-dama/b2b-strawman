package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProjectContextBuilder implements TemplateContextBuilder {

  private static final Logger log = LoggerFactory.getLogger(ProjectContextBuilder.class);

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
    contextHelper.populateLocale(context);
    var fieldDefCache = new EnumMap<EntityType, List<FieldDefinition>>(EntityType.class);

    // project.*
    var projectMap = new LinkedHashMap<String, Object>();
    projectMap.put("id", project.getId());
    projectMap.put("name", project.getName());
    projectMap.put("description", project.getDescription());
    projectMap.put(
        "createdAt", project.getCreatedAt() != null ? project.getCreatedAt().toString() : null);
    // Promoted structural project fields (Epic 460) — direct template variables.
    projectMap.put("referenceNumber", project.getReferenceNumber());
    projectMap.put("priority", project.getPriority() != null ? project.getPriority().name() : null);
    projectMap.put("workType", project.getWorkType());
    Map<String, Object> resolvedProjectCustomFields =
        contextHelper.resolveDropdownLabels(
            project.getCustomFields() != null ? project.getCustomFields() : Map.of(),
            EntityType.PROJECT,
            fieldDefCache);
    // Wrap for mutation — may be immutable Map.of() for empty input.
    var mutableProjectCustomFields = new LinkedHashMap<String, Object>(resolvedProjectCustomFields);
    injectPromotedProjectAliases(mutableProjectCustomFields, project);
    projectMap.put("customFields", mutableProjectCustomFields);
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
      final UUID custId = resolvedCustomerId;
      customerRepository
          .findById(custId)
          .ifPresentOrElse(
              customer -> {
                var customerMap = new LinkedHashMap<String, Object>();
                customerMap.put("id", customer.getId());
                customerMap.put("name", customer.getName());
                customerMap.put("email", customer.getEmail());
                CustomerContextBuilder.populatePromotedCustomerFields(customerMap, customer);
                Map<String, Object> rawCustomFields =
                    customer.getCustomFields() != null ? customer.getCustomFields() : Map.of();
                Map<String, Object> resolvedCustomFields =
                    contextHelper.resolveDropdownLabels(
                        rawCustomFields, EntityType.CUSTOMER, fieldDefCache);
                var mutableCustomFields = new LinkedHashMap<String, Object>(resolvedCustomFields);
                CustomerContextBuilder.injectPromotedCustomerAliases(mutableCustomFields, customer);
                customerMap.put("customFields", mutableCustomFields);
                log.debug(
                    "Project {} customer {} customFields: raw keys={}, resolved keys={}",
                    entityId,
                    custId,
                    rawCustomFields.keySet(),
                    mutableCustomFields.keySet());
                context.put("customer", customerMap);
              },
              () -> {
                log.debug("Project {} customer {} not found in repository", entityId, custId);
                context.put("customer", null);
              });
    } else {
      log.debug("Project {} has no linked customer", entityId);
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

  /**
   * Injects backward-compatible {@code customFields.<slug>} aliases for promoted project fields.
   * Priority is serialized lowercase to match the old pack's dropdown values (low/medium/high);
   * workType is aliased under both accounting and legal slugs.
   */
  private static void injectPromotedProjectAliases(
      Map<String, Object> customFields, Project project) {
    if (project.getReferenceNumber() != null) {
      customFields.put("reference_number", project.getReferenceNumber());
    }
    if (project.getPriority() != null) {
      customFields.put("priority", project.getPriority().name().toLowerCase(Locale.ROOT));
    }
    if (project.getWorkType() != null) {
      customFields.put("engagement_type", project.getWorkType());
      customFields.put("matter_type", project.getWorkType());
    }
  }
}
