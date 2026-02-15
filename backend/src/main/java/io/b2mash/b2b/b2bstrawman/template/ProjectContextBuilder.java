package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
  private final MemberRepository memberRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final EntityTagRepository entityTagRepository;
  private final TagRepository tagRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final S3PresignedUrlService s3PresignedUrlService;

  public ProjectContextBuilder(
      ProjectRepository projectRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectMemberRepository projectMemberRepository,
      MemberRepository memberRepository,
      ProjectBudgetRepository projectBudgetRepository,
      EntityTagRepository entityTagRepository,
      TagRepository tagRepository,
      OrgSettingsRepository orgSettingsRepository,
      S3PresignedUrlService s3PresignedUrlService) {
    this.projectRepository = projectRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.memberRepository = memberRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.entityTagRepository = entityTagRepository;
    this.tagRepository = tagRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.PROJECT;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var project =
        projectRepository
            .findOneById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", entityId));

    var context = new HashMap<String, Object>();

    // project.*
    var projectMap = new LinkedHashMap<String, Object>();
    projectMap.put("id", project.getId());
    projectMap.put("name", project.getName());
    projectMap.put("description", project.getDescription());
    projectMap.put(
        "createdAt", project.getCreatedAt() != null ? project.getCreatedAt().toString() : null);
    projectMap.put(
        "customFields", project.getCustomFields() != null ? project.getCustomFields() : Map.of());
    context.put("project", projectMap);

    // customer.* (via CustomerProject join table — null-safe)
    var customerProjects = customerProjectRepository.findByProjectId(entityId);
    if (!customerProjects.isEmpty()) {
      var firstLink = customerProjects.getFirst();
      customerRepository
          .findOneById(firstLink.getCustomerId())
          .ifPresentOrElse(
              customer -> {
                var customerMap = new LinkedHashMap<String, Object>();
                customerMap.put("id", customer.getId());
                customerMap.put("name", customer.getName());
                customerMap.put("email", customer.getEmail());
                customerMap.put(
                    "customFields",
                    customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
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
    context.put("org", buildOrgContext());

    // budget.*
    projectBudgetRepository
        .findByProjectId(entityId)
        .ifPresentOrElse(
            budget -> context.put("budget", buildBudgetMap(budget)),
            () -> context.put("budget", null));

    // tags[]
    context.put("tags", buildTagsList("PROJECT", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", buildGeneratedByMap(memberId));

    return context;
  }

  private Map<String, Object> buildBudgetMap(ProjectBudget budget) {
    var budgetMap = new LinkedHashMap<String, Object>();
    budgetMap.put("hours", budget.getBudgetHours());
    budgetMap.put("amount", budget.getBudgetAmount());
    budgetMap.put("currency", budget.getBudgetCurrency());
    return budgetMap;
  }

  Map<String, Object> buildOrgContext() {
    var orgMap = new LinkedHashMap<String, Object>();
    orgSettingsRepository
        .findForCurrentTenant()
        .ifPresentOrElse(
            settings -> {
              orgMap.put("defaultCurrency", settings.getDefaultCurrency());
              orgMap.put("brandColor", settings.getBrandColor());
              orgMap.put("documentFooterText", settings.getDocumentFooterText());

              if (settings.getLogoS3Key() != null && !settings.getLogoS3Key().isBlank()) {
                try {
                  var result = s3PresignedUrlService.generateDownloadUrl(settings.getLogoS3Key());
                  orgMap.put("logoUrl", result.url());
                } catch (Exception e) {
                  log.warn("Failed to generate logo URL for key: {}", settings.getLogoS3Key(), e);
                  orgMap.put("logoUrl", null);
                }
              } else {
                orgMap.put("logoUrl", null);
              }
            },
            () -> {
              orgMap.put("defaultCurrency", null);
              orgMap.put("brandColor", null);
              orgMap.put("documentFooterText", null);
              orgMap.put("logoUrl", null);
            });
    return orgMap;
  }

  List<Map<String, Object>> buildTagsList(String entityType, UUID entityId) {
    var entityTags = entityTagRepository.findByEntityTypeAndEntityId(entityType, entityId);
    if (entityTags.isEmpty()) {
      return List.of();
    }
    var tagIds = entityTags.stream().map(et -> et.getTagId()).toList();
    var tags = tagRepository.findAllByIds(tagIds);
    return tags.stream()
        .map(
            tag -> {
              var tagMap = new LinkedHashMap<String, Object>();
              tagMap.put("name", tag.getName());
              tagMap.put("color", tag.getColor());
              return (Map<String, Object>) tagMap;
            })
        .toList();
  }

  Map<String, Object> buildGeneratedByMap(UUID memberId) {
    var generatedBy = new LinkedHashMap<String, Object>();
    memberRepository
        .findOneById(memberId)
        .ifPresentOrElse(
            member -> {
              generatedBy.put("name", member.getName());
              generatedBy.put("email", member.getEmail());
            },
            () -> {
              generatedBy.put("name", "Unknown");
              generatedBy.put("email", null);
            });
    return generatedBy;
  }
}
