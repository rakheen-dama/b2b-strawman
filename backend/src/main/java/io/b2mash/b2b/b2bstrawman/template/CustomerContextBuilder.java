package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
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
import org.springframework.stereotype.Service;

@Service
public class CustomerContextBuilder implements TemplateContextBuilder {

  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final EntityTagRepository entityTagRepository;
  private final TagRepository tagRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final MemberRepository memberRepository;
  private final S3PresignedUrlService s3PresignedUrlService;

  public CustomerContextBuilder(
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      EntityTagRepository entityTagRepository,
      TagRepository tagRepository,
      OrgSettingsRepository orgSettingsRepository,
      MemberRepository memberRepository,
      S3PresignedUrlService s3PresignedUrlService) {
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.entityTagRepository = entityTagRepository;
    this.tagRepository = tagRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.memberRepository = memberRepository;
    this.s3PresignedUrlService = s3PresignedUrlService;
  }

  @Override
  public TemplateEntityType supports() {
    return TemplateEntityType.CUSTOMER;
  }

  @Override
  public Map<String, Object> buildContext(UUID entityId, UUID memberId) {
    var customer =
        customerRepository
            .findOneById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", entityId));

    var context = new HashMap<String, Object>();

    // customer.*
    var customerMap = new LinkedHashMap<String, Object>();
    customerMap.put("id", customer.getId());
    customerMap.put("name", customer.getName());
    customerMap.put("email", customer.getEmail());
    customerMap.put("phone", customer.getPhone());
    customerMap.put("status", customer.getStatus());
    customerMap.put(
        "customFields", customer.getCustomFields() != null ? customer.getCustomFields() : Map.of());
    context.put("customer", customerMap);

    // projects[] (linked via CustomerProject)
    var customerProjects = customerProjectRepository.findByCustomerId(entityId);
    if (!customerProjects.isEmpty()) {
      var projectIds = customerProjects.stream().map(cp -> cp.getProjectId()).toList();
      var projects = projectRepository.findAllByIds(projectIds);
      var projectsList =
          projects.stream()
              .map(
                  p -> {
                    var pm = new LinkedHashMap<String, Object>();
                    pm.put("id", p.getId());
                    pm.put("name", p.getName());
                    return (Map<String, Object>) pm;
                  })
              .toList();
      context.put("projects", projectsList);
    } else {
      context.put("projects", List.of());
    }

    // org.*
    context.put("org", buildOrgContext());

    // tags[]
    context.put("tags", buildTagsList("CUSTOMER", entityId));

    // generatedAt, generatedBy
    context.put("generatedAt", Instant.now().toString());
    context.put("generatedBy", buildGeneratedByMap(memberId));

    return context;
  }

  private Map<String, Object> buildOrgContext() {
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

  private List<Map<String, Object>> buildTagsList(String entityType, UUID entityId) {
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

  private Map<String, Object> buildGeneratedByMap(UUID memberId) {
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
