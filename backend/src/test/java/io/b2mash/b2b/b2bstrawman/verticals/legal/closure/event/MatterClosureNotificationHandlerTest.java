package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link MatterClosureNotificationHandler} (Epic 489B, task 489.20). Verifies
 * that {@link MatterClosedEvent} fanout notifies owners + admins (but not plain members) via the
 * shared {@code notifyAdminsAndOwners} path in {@code NotificationService}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterClosureNotificationHandlerTest {

  private static final String ORG_ID = "org_matter_closure_notif";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private PortalReadModelRepository portalReadModelRepository;
  @Autowired private PortalReadModelService portalReadModelService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID adminMemberId;
  private UUID memberMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Closure Notif Firm", "legal-za");

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_closure_notif_owner",
                "closure_notif_owner@test.com",
                "Closure Notif Owner",
                "owner"));
    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_closure_notif_admin",
                "closure_notif_admin@test.com",
                "Closure Notif Admin",
                "admin"));
    memberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_closure_notif_member",
                "closure_notif_member@test.com",
                "Closure Notif Member",
                "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void matterClosedEvent_notifiesOwnerAndAdmin_butNotMember() {
    UUID projectId = UUID.randomUUID();
    UUID closureLogId = UUID.randomUUID();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        MatterClosedEvent.of(
                            projectId, closureLogId, "CONCLUDED", false, ownerMemberId))));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  List<Notification> all = notificationRepository.findAll();
                  var closureNotifs =
                      all.stream()
                          .filter(n -> "MATTER_CLOSED".equals(n.getType()))
                          .filter(n -> projectId.equals(n.getReferenceEntityId()))
                          .toList();

                  // Owner + admin both receive the MATTER_CLOSED notification.
                  assertThat(closureNotifs)
                      .anyMatch(n -> ownerMemberId.equals(n.getRecipientMemberId()));
                  assertThat(closureNotifs)
                      .anyMatch(n -> adminMemberId.equals(n.getRecipientMemberId()));

                  // The plain member role does NOT.
                  assertThat(closureNotifs)
                      .noneMatch(n -> memberMemberId.equals(n.getRecipientMemberId()));

                  // Title contains "Matter closed"; no override flag branch for this event.
                  assertThat(closureNotifs)
                      .allSatisfy(n -> assertThat(n.getTitle()).contains("Matter closed"));
                }));
  }

  @Test
  void matterClosedEvent_withOverrideTrue_includesOverrideFlagInTitle() {
    UUID projectId = UUID.randomUUID();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        MatterClosedEvent.of(
                            projectId,
                            UUID.randomUUID(),
                            "CLIENT_TERMINATED",
                            /* override */ true,
                            ownerMemberId))));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  List<Notification> all = notificationRepository.findAll();
                  var overrideNotifs =
                      all.stream()
                          .filter(n -> "MATTER_CLOSED".equals(n.getType()))
                          .filter(n -> projectId.equals(n.getReferenceEntityId()))
                          .toList();
                  assertThat(overrideNotifs).isNotEmpty();
                  assertThat(overrideNotifs)
                      .allSatisfy(n -> assertThat(n.getTitle()).contains("override"));
                }));
  }

  @Test
  void matterReopenedEvent_notifiesAdminsAndOwners() {
    UUID projectId = UUID.randomUUID();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        MatterReopenedEvent.of(
                            projectId, ownerMemberId, "Client returned with new instructions."))));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  List<Notification> all = notificationRepository.findAll();
                  var reopenNotifs =
                      all.stream()
                          .filter(n -> "MATTER_REOPENED".equals(n.getType()))
                          .filter(n -> projectId.equals(n.getReferenceEntityId()))
                          .toList();
                  assertThat(reopenNotifs)
                      .anyMatch(n -> ownerMemberId.equals(n.getRecipientMemberId()));
                  assertThat(reopenNotifs)
                      .anyMatch(n -> adminMemberId.equals(n.getRecipientMemberId()));
                  assertThat(reopenNotifs)
                      .noneMatch(n -> memberMemberId.equals(n.getRecipientMemberId()));
                  assertThat(reopenNotifs)
                      .allSatisfy(n -> assertThat(n.getTitle()).contains("reopened"));
                }));
  }

  // ==========================================================================
  // GAP-L-73 — portal projection status sync
  // ==========================================================================

  @Test
  void matterClosedEvent_flipsPortalProjectionStatus_toClosed() {
    // Seed a real Project + portal_projects projection row (the handler resolves project
    // name + description by re-reading projectRepository.findById, so the project must exist
    // in the tenant schema).
    UUID customerId = UUID.randomUUID();
    UUID[] projectIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project("L-73 Closed Matter", "L-73 status sync test", ownerMemberId);
                  project.setCustomerId(customerId);
                  projectIdHolder[0] = projectRepository.saveAndFlush(project).getId();
                }));
    UUID projectId = projectIdHolder[0];

    runInTenant(
        () ->
            portalReadModelRepository.upsertPortalProject(
                projectId,
                customerId,
                ORG_ID,
                "L-73 Closed Matter",
                "ACTIVE",
                "L-73 status sync test",
                Instant.now()));

    // Sanity check: portal projection starts at ACTIVE.
    runInTenant(
        () -> {
          var view = portalReadModelService.getProjectDetail(projectId, customerId, ORG_ID);
          assertThat(view.status()).isEqualTo("ACTIVE");
        });

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        MatterClosedEvent.of(
                            projectId, UUID.randomUUID(), "CONCLUDED", false, ownerMemberId))));

    runInTenant(
        () -> {
          var view = portalReadModelService.getProjectDetail(projectId, customerId, ORG_ID);
          assertThat(view.status()).isEqualTo("CLOSED");
        });
  }

  @Test
  void matterReopenedEvent_flipsPortalProjectionStatus_backToActive() {
    UUID customerId = UUID.randomUUID();
    UUID[] projectIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project("L-73 Reopen Matter", "L-73 reopen sync test", ownerMemberId);
                  project.setCustomerId(customerId);
                  projectIdHolder[0] = projectRepository.saveAndFlush(project).getId();
                }));
    UUID projectId = projectIdHolder[0];

    // Seed projection at "CLOSED" to simulate the post-close state.
    runInTenant(
        () ->
            portalReadModelRepository.upsertPortalProject(
                projectId,
                customerId,
                ORG_ID,
                "L-73 Reopen Matter",
                "CLOSED",
                "L-73 reopen sync test",
                Instant.now()));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        MatterReopenedEvent.of(
                            projectId, ownerMemberId, "Client returned with new instructions."))));

    runInTenant(
        () -> {
          var view = portalReadModelService.getProjectDetail(projectId, customerId, ORG_ID);
          assertThat(view.status()).isEqualTo("ACTIVE");
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
