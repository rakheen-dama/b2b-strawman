package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PortalResyncService {

  private static final Logger log = LoggerFactory.getLogger(PortalResyncService.class);

  private final PortalReadModelRepository readModelRepo;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final DocumentRepository documentRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate portalTxTemplate;

  public PortalResyncService(
      PortalReadModelRepository readModelRepo,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      DocumentRepository documentRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.readModelRepo = readModelRepo;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.documentRepository = documentRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.portalTxTemplate = new TransactionTemplate(portalTxManager);
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

    // Step 1: Load all tenant data within ScopedValue binding
    var carrier =
        ScopedValue.where(RequestScopes.TENANT_ID, schema).where(RequestScopes.ORG_ID, orgId);

    var projections = new ArrayList<ProjectionData>();

    carrier.run(
        () -> {
          var allLinks = customerProjectRepository.findAll();
          var sharedDocsCache = new HashMap<UUID, List<Document>>();

          for (var link : allLinks) {
            var projectId = link.getProjectId();
            var customerId = link.getCustomerId();

            var projectOpt = projectRepository.findById(projectId);
            if (projectOpt.isEmpty()) {
              log.warn("Project not found during resync: projectId={}", projectId);
              continue;
            }
            var project = projectOpt.get();

            var sharedDocs =
                sharedDocsCache.computeIfAbsent(
                    projectId,
                    pid -> {
                      var docs = documentRepository.findProjectScopedByProjectId(pid);
                      return docs.stream()
                          .filter(d -> Document.Visibility.SHARED.equals(d.getVisibility()))
                          .toList();
                    });

            projections.add(new ProjectionData(project, customerId, sharedDocs));
          }
        });

    // Step 2: Wipe and rebuild portal data atomically within a portal transaction
    portalTxTemplate.executeWithoutResult(
        status -> {
          // Wipe portal projects and documents for this org.
          // Comments and summaries are NOT wiped because resync does not rebuild them —
          // deleting them would cause permanent data loss.
          readModelRepo.deletePortalDocumentsByOrg(orgId);
          readModelRepo.deletePortalProjectsByOrg(orgId);

          // Rebuild from collected tenant data
          for (var data : projections) {
            var project = data.project();
            var customerId = data.customerId();

            readModelRepo.upsertPortalProject(
                project.getId(),
                customerId,
                orgId,
                project.getName(),
                "ACTIVE",
                project.getDescription(),
                project.getCreatedAt());

            for (var doc : data.sharedDocs()) {
              readModelRepo.upsertPortalDocument(
                  doc.getId(),
                  orgId,
                  customerId,
                  project.getId(),
                  doc.getFileName(),
                  doc.getContentType(),
                  doc.getSize(),
                  doc.getScope(),
                  doc.getS3Key(),
                  doc.getUploadedAt());
            }

            readModelRepo.setDocumentCount(project.getId(), customerId, data.sharedDocs().size());
          }
        });

    int projectsProjected = projections.size();
    int documentsProjected = projections.stream().mapToInt(d -> d.sharedDocs().size()).sum();

    log.info(
        "Portal resync completed for org={}: projects={}, documents={}",
        orgId,
        projectsProjected,
        documentsProjected);

    return new ResyncResult(projectsProjected, documentsProjected);
  }

  public record ResyncResult(int projectsProjected, int documentsProjected) {}

  private record ProjectionData(
      io.b2mash.b2b.b2bstrawman.project.Project project,
      UUID customerId,
      List<Document> sharedDocs) {}
}
