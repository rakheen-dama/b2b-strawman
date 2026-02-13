package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.HashSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortalResyncService {

  private static final Logger log = LoggerFactory.getLogger(PortalResyncService.class);

  private final PortalReadModelRepository readModelRepo;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final DocumentRepository documentRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;

  public PortalResyncService(
      PortalReadModelRepository readModelRepo,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      DocumentRepository documentRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository) {
    this.readModelRepo = readModelRepo;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.documentRepository = documentRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
  }

  public ResyncResult resyncOrg(String orgId) {
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Organization not found", "No organization found with orgId " + orgId));

    String schema = mapping.getSchemaName();
    log.info("Starting portal resync for org={}, schema={}", orgId, schema);

    // Step 1: Wipe all existing portal data for this org
    readModelRepo.deletePortalDocumentsByOrg(orgId);
    readModelRepo.deletePortalCommentsByOrg(orgId);
    readModelRepo.deletePortalProjectSummariesByOrg(orgId);
    readModelRepo.deletePortalProjectsByOrg(orgId);

    // Step 2: Rebuild portal data within tenant scope
    var carrier =
        ScopedValue.where(RequestScopes.TENANT_ID, schema).where(RequestScopes.ORG_ID, orgId);

    int[] projectsProjected = {0};
    int[] documentsProjected = {0};

    carrier.run(
        () -> {
          // Load all customer-project links
          var allLinks = customerProjectRepository.findAll();

          // Group by projectId to avoid re-loading projects for each customer
          var processedProjects = new HashSet<UUID>();

          for (var link : allLinks) {
            var projectId = link.getProjectId();
            var customerId = link.getCustomerId();

            // Load and upsert the project for this customer
            var projectOpt = projectRepository.findOneById(projectId);
            if (projectOpt.isEmpty()) {
              log.warn("Project not found during resync: projectId={}", projectId);
              continue;
            }
            var project = projectOpt.get();

            readModelRepo.upsertPortalProject(
                project.getId(),
                customerId,
                orgId,
                project.getName(),
                "ACTIVE",
                project.getDescription(),
                project.getCreatedAt());
            projectsProjected[0]++;

            // Load SHARED documents for this project (only once per project)
            if (!processedProjects.contains(projectId)) {
              processedProjects.add(projectId);
            }

            var documents = documentRepository.findProjectScopedByProjectId(projectId);
            int sharedDocCount = 0;
            for (var doc : documents) {
              if (Document.Visibility.SHARED.equals(doc.getVisibility())) {
                readModelRepo.upsertPortalDocument(
                    doc.getId(),
                    orgId,
                    customerId,
                    projectId,
                    doc.getFileName(),
                    doc.getContentType(),
                    doc.getSize(),
                    doc.getScope(),
                    doc.getS3Key(),
                    doc.getUploadedAt());
                documentsProjected[0]++;
                sharedDocCount++;
              }
            }

            // Set document count for this project-customer pair
            if (sharedDocCount > 0) {
              // The upsertPortalProject sets document_count to default (0) via INSERT.
              // We need to set it to the actual count. We can do this by incrementing.
              for (int i = 0; i < sharedDocCount; i++) {
                readModelRepo.incrementDocumentCount(projectId, customerId);
              }
            }
          }
        });

    log.info(
        "Portal resync completed for org={}: projects={}, documents={}",
        orgId,
        projectsProjected[0],
        documentsProjected[0]);

    return new ResyncResult(projectsProjected[0], documentsProjected[0]);
  }

  public record ResyncResult(int projectsProjected, int documentsProjected) {}
}
