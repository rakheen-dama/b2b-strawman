package io.b2mash.b2b.b2bstrawman.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link ActivityService}. Tests service-level behavior including event
 * ordering, batch actor name resolution, unknown actor fallback, and cross-project filtering.
 * Creates real projects and members in the database to exercise the full service flow.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityServiceIntegrationTest {

  private static final String ORG_ID = "org_activity_svc_test";

  @Autowired private ActivityService activityService;
  @Autowired private AuditService auditService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String tenantSchema;
  private UUID memberId;
  private UUID projectId;
  private UUID otherProjectId;
  private UUID unknownActorId;

  @BeforeAll
  void provisionTenantAndSeed() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Activity Svc Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    unknownActorId = UUID.randomUUID();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create a member via repository
              var member =
                  new Member("clerk_as_owner", "as_owner@test.com", "AS Owner", null, "owner");
              memberRepository.save(member);
              memberId = member.getId();

              // Create project 1 (main test project)
              var project = new Project("Activity Svc Project 1", "Main test project", memberId);
              projectRepository.save(project);
              projectId = project.getId();

              // Create project 2 (other project for cross-project filtering)
              var otherProject = new Project("Activity Svc Project 2", "Other project", memberId);
              projectRepository.save(otherProject);
              otherProjectId = otherProject.getId();

              // Seed audit events for project 1
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      memberId,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "First Task", "project_id", projectId.toString())));

              sleep(50);

              auditService.log(
                  new AuditEventRecord(
                      "task.claimed",
                      "task",
                      UUID.randomUUID(),
                      memberId,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "First Task", "project_id", projectId.toString())));

              sleep(50);

              auditService.log(
                  new AuditEventRecord(
                      "document.uploaded",
                      "document",
                      UUID.randomUUID(),
                      memberId,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("file_name", "design.pdf", "project_id", projectId.toString())));

              sleep(50);

              // Seed event with unknown actor (UUID that doesn't match any member)
              auditService.log(
                  new AuditEventRecord(
                      "task.updated",
                      "task",
                      UUID.randomUUID(),
                      unknownActorId,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Unknown Actor Task", "project_id", projectId.toString())));

              // Seed audit events for project 2 (should not appear in project 1 queries)
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      memberId,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of(
                          "title", "Other Project Task", "project_id", otherProjectId.toString())));
            });
  }

  @Test
  void queryReturnsEventsOrderedByOccurredAtDesc() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var page =
                  activityService.getProjectActivity(
                      projectId, null, null, PageRequest.of(0, 20), memberId, "owner");

              assertThat(page.getTotalElements()).isEqualTo(4);
              assertThat(page.getContent()).hasSize(4);

              // Verify ordering: most recent first (document.uploaded event was last for project 1
              // among known-actor events, then unknown actor event)
              var items = page.getContent();
              for (int i = 0; i < items.size() - 1; i++) {
                assertThat(items.get(i).occurredAt())
                    .isAfterOrEqualTo(items.get(i + 1).occurredAt());
              }
            });
  }

  @Test
  void batchActorNameResolutionWorks() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var page =
                  activityService.getProjectActivity(
                      projectId, "task", null, PageRequest.of(0, 20), memberId, "owner");

              // Should have 3 task events (created, claimed, updated by unknown actor)
              assertThat(page.getTotalElements()).isEqualTo(3);

              // Events by known member should have resolved actor name
              var knownActorEvents =
                  page.getContent().stream()
                      .filter(item -> "AS Owner".equals(item.actorName()))
                      .toList();
              assertThat(knownActorEvents).hasSizeGreaterThanOrEqualTo(2);

              // Verify the message includes the actor name
              assertThat(knownActorEvents).allMatch(item -> item.message().startsWith("AS Owner"));
            });
  }

  @Test
  void unknownActorFallsBackToUuidString() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var page =
                  activityService.getProjectActivity(
                      projectId, null, null, PageRequest.of(0, 20), memberId, "owner");

              // Find the event with the unknown actor
              var unknownActorItem =
                  page.getContent().stream()
                      .filter(item -> unknownActorId.toString().equals(item.actorName()))
                      .findFirst();

              assertThat(unknownActorItem).isPresent();
              assertThat(unknownActorItem.get().actorName()).isEqualTo(unknownActorId.toString());
              assertThat(unknownActorItem.get().actorAvatarUrl()).isNull();
            });
  }

  @Test
  void crossProjectFilteringReturnsCorrectResults() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Query project 1 should return 4 events
              var project1Page =
                  activityService.getProjectActivity(
                      projectId, null, null, PageRequest.of(0, 20), memberId, "owner");
              assertThat(project1Page.getTotalElements()).isEqualTo(4);

              // Query project 2 should return 1 event
              var project2Page =
                  activityService.getProjectActivity(
                      otherProjectId, null, null, PageRequest.of(0, 20), memberId, "owner");
              assertThat(project2Page.getTotalElements()).isEqualTo(1);
              assertThat(project2Page.getContent().getFirst().message())
                  .contains("Other Project Task");

              // Query non-existent project should throw ResourceNotFoundException
              UUID nonExistentProjectId = UUID.randomUUID();
              assertThatThrownBy(
                      () ->
                          activityService.getProjectActivity(
                              nonExistentProjectId,
                              null,
                              null,
                              PageRequest.of(0, 20),
                              memberId,
                              "owner"))
                  .isInstanceOf(ResourceNotFoundException.class);
            });
  }

  // --- Helpers ---

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
