package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProjectService {

  private static final Logger log = LoggerFactory.getLogger(CustomerProjectService.class);

  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;

  public CustomerProjectService(
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService) {
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
  }

  @Transactional
  public CustomerProject linkCustomerToProject(
      UUID customerId, UUID projectId, UUID linkedBy, UUID memberId, String orgRole) {
    // Validate customer exists in tenant (uses filter-aware JPQL query)
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Validate project exists in tenant (uses filter-aware JPQL query)
    projectRepository
        .findOneById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    // Check permission: Owner/Admin or Project Lead
    requireLinkPermission(projectId, memberId, orgRole);

    // Check not already linked
    if (customerProjectRepository.existsByCustomerIdAndProjectId(customerId, projectId)) {
      throw new ResourceConflictException(
          "Customer already linked",
          "Customer " + customerId + " is already linked to project " + projectId);
    }

    var link = customerProjectRepository.save(new CustomerProject(customerId, projectId, linkedBy));
    log.info("Linked customer {} to project {} by member {}", customerId, projectId, linkedBy);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.linked")
            .entityType("customer")
            .entityId(customerId)
            .details(Map.of("project_id", projectId.toString()))
            .build());

    return link;
  }

  @Transactional
  public void unlinkCustomerFromProject(
      UUID customerId, UUID projectId, UUID memberId, String orgRole) {
    // Validate customer exists in tenant
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Validate project exists in tenant
    projectRepository
        .findOneById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    // Check permission: Owner/Admin or Project Lead
    requireLinkPermission(projectId, memberId, orgRole);

    // Check link exists
    if (!customerProjectRepository.existsByCustomerIdAndProjectId(customerId, projectId)) {
      throw new ResourceNotFoundException("CustomerProject link", customerId);
    }

    customerProjectRepository.deleteByCustomerIdAndProjectId(
        customerId, projectId, RequestScopes.TENANT_ID.get());
    log.info("Unlinked customer {} from project {} by member {}", customerId, projectId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("customer.unlinked")
            .entityType("customer")
            .entityId(customerId)
            .details(Map.of("project_id", projectId.toString()))
            .build());
  }

  @Transactional(readOnly = true)
  public List<Project> listProjectsForCustomer(UUID customerId, UUID memberId, String orgRole) {
    // Validate customer exists in tenant
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var links = customerProjectRepository.findByCustomerId(customerId);
    var projects =
        links.stream()
            .map(link -> projectRepository.findOneById(link.getProjectId()))
            .flatMap(java.util.Optional::stream);

    // Owner/Admin can see all linked projects; regular members only see projects they have access
    // to
    if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
      return projects.toList();
    }
    return projects
        .filter(
            project ->
                projectAccessService.checkAccess(project.getId(), memberId, orgRole).canView())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomersForProject(UUID projectId, UUID memberId, String orgRole) {
    // Check project access via ProjectAccessService
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var links = customerProjectRepository.findByProjectId(projectId);
    return links.stream()
        .map(link -> customerRepository.findOneById(link.getCustomerId()))
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  /**
   * Checks that the caller has permission to link/unlink customers to a project. Owner and Admin
   * org roles can always link. Org members need to be a project lead.
   */
  private void requireLinkPermission(UUID projectId, UUID memberId, String orgRole) {
    if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
      return;
    }
    // For org members, require project lead access (canEdit = true for leads)
    projectAccessService.requireEditAccess(projectId, memberId, orgRole);
  }
}
