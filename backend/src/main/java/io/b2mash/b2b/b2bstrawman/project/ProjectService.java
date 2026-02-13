package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  private final ProjectRepository repository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public ProjectService(
      ProjectRepository repository,
      ProjectMemberRepository projectMemberRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public List<ProjectWithRole> listProjects(UUID memberId, String orgRole) {
    if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
      return repository.findAllProjectsWithRole(memberId);
    }
    return repository.findProjectsForMember(memberId);
  }

  @Transactional(readOnly = true)
  public ProjectWithRole getProject(UUID id, UUID memberId, String orgRole) {
    var project =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireViewAccess(id, memberId, orgRole);
    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public Project createProject(String name, String description, UUID createdBy) {
    var project = repository.save(new Project(name, description, createdBy));
    var lead = new ProjectMember(project.getId(), createdBy, Roles.PROJECT_LEAD, null);
    projectMemberRepository.save(lead);
    log.info("Created project {} with lead member {}", project.getId(), createdBy);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.created")
            .entityType("project")
            .entityId(project.getId())
            .details(Map.of("name", project.getName()))
            .build());

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new ProjectCreatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return project;
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id, String name, String description, UUID memberId, String orgRole) {
    var project =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireEditAccess(id, memberId, orgRole);

    // Capture old values before mutation
    String oldName = project.getName();
    String oldDescription = project.getDescription();

    project.update(name, description);
    project = repository.save(project);

    // Build delta map -- only include changed fields
    var details = new LinkedHashMap<String, Object>();
    if (!Objects.equals(oldName, name)) {
      details.put("name", Map.of("from", oldName, "to", name));
    }
    if (!Objects.equals(oldDescription, description)) {
      details.put(
          "description",
          Map.of(
              "from", oldDescription != null ? oldDescription : "",
              "to", description != null ? description : ""));
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.updated")
            .entityType("project")
            .entityId(project.getId())
            .details(details.isEmpty() ? null : details)
            .build());

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new ProjectUpdatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public void deleteProject(UUID id) {
    var project =
        repository.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    repository.delete(project);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.deleted")
            .entityType("project")
            .entityId(project.getId())
            .details(Map.of("name", project.getName()))
            .build());
  }
}
