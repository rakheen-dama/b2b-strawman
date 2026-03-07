package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.UpdateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.CreateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CapacityNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cap_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ResourceAllocationService allocationService;
  @Autowired private LeaveBlockService leaveBlockService;
  @Autowired private CapacityService capacityService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private AuditService auditService;
  @Autowired private ProjectService projectService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cap Notif Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    String ownerIdStr =
        syncMember(ORG_ID, "user_cn_owner", "cn_owner@test.com", "CN Owner", "owner");
    memberIdOwner = UUID.fromString(ownerIdStr);
    String memberIdStr =
        syncMember(ORG_ID, "user_cn_member", "cn_member@test.com", "CN Member", "member");
    memberIdMember = UUID.fromString(memberIdStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          Project project =
              projectService.createProject("Notif Test Project", "desc", memberIdOwner);
          projectId = project.getId();
        });
  }

  // ===== Notification Tests =====

  @Test
  @Order(1)
  void createAllocation_sendsNotificationWhenAllocatingSomeoneElse() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdMember,
                  projectId,
                  LocalDate.of(2026, 6, 1), // Monday
                  new BigDecimal("20.00"),
                  "Sprint work");
          allocationService.createAllocation(request, memberIdOwner);
        });

    // Verify notification sent to the allocated member
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(
                  memberIdMember, PageRequest.of(0, 100));
          assertThat(notifs.getContent())
              .anyMatch(
                  n ->
                      "ALLOCATION_CHANGED".equals(n.getType())
                          && n.getTitle().contains("Notif Test Project")
                          && n.getTitle().contains("20.00"));
        });
  }

  @Test
  @Order(2)
  void createAllocation_noNotificationWhenAllocatingSelf() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 6, 8), // Monday
                  new BigDecimal("15.00"),
                  "Self allocation");
          allocationService.createAllocation(request, memberIdOwner);
        });

    // Verify no ALLOCATION_CHANGED notification sent to owner for self-allocation
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(memberIdOwner, PageRequest.of(0, 100));
          assertThat(notifs.getContent())
              .noneMatch(
                  n ->
                      "ALLOCATION_CHANGED".equals(n.getType())
                          && n.getTitle().contains("Self allocation"));
        });
  }

  @Test
  @Order(3)
  void updateAllocation_sendsNotificationWhenUpdatingSomeoneElse() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          // Find the allocation created in test 1
          var allocations =
              allocationService.listAllocations(
                  memberIdMember, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
          assertThat(allocations).isNotEmpty();
          UUID allocationId = allocations.getFirst().id();

          var updateRequest = new UpdateAllocationRequest(new BigDecimal("25.00"), "Updated hours");
          allocationService.updateAllocation(allocationId, updateRequest);
        });

    // Verify notification sent for update
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(
                  memberIdMember, PageRequest.of(0, 100));
          assertThat(notifs.getContent())
              .anyMatch(
                  n ->
                      "ALLOCATION_CHANGED".equals(n.getType()) && n.getTitle().contains("updated"));
        });
  }

  @Test
  @Order(4)
  void overAllocation_sendsNotificationToMemberAndAdmins() {
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          // Create allocation that causes over-allocation (capacity default is 40h)
          var request =
              new CreateAllocationRequest(
                  memberIdMember,
                  projectId,
                  LocalDate.of(2026, 7, 6), // Monday
                  new BigDecimal("45.00"),
                  "Over-allocated");
          allocationService.createAllocation(request, memberIdMember);
        });

    // Verify MEMBER_OVER_ALLOCATED notification sent to owner (admin/owner)
    // The member is excluded because they are the actor
    runInTenant(
        () -> {
          var ownerNotifs =
              notificationRepository.findByRecipientMemberId(memberIdOwner, PageRequest.of(0, 100));
          assertThat(ownerNotifs.getContent())
              .anyMatch(
                  n ->
                      "MEMBER_OVER_ALLOCATED".equals(n.getType())
                          && n.getTitle().contains("CN Member")
                          && n.getTitle().contains("over capacity"));
        });
  }

  @Test
  @Order(5)
  void leaveCreated_sendsNotificationWhenAdminCreatesForOther() {
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), "Annual leave");
          leaveBlockService.createLeaveBlock(memberIdMember, request, memberIdOwner);
        });

    // Verify LEAVE_CREATED notification sent to the member
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(
                  memberIdMember, PageRequest.of(0, 100));
          assertThat(notifs.getContent())
              .anyMatch(
                  n ->
                      "LEAVE_CREATED".equals(n.getType())
                          && n.getTitle().contains("2026-08-03")
                          && n.getTitle().contains("2026-08-07"));
        });
  }

  @Test
  @Order(6)
  void leaveCreated_noNotificationWhenSelfService() {
    runInTenantAs(
        memberIdMember,
        "member",
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11), "Personal leave");
          leaveBlockService.createLeaveBlock(memberIdMember, request, memberIdMember);
        });

    // Verify no LEAVE_CREATED notification sent for self-service
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(
                  memberIdMember, PageRequest.of(0, 100));
          assertThat(notifs.getContent())
              .noneMatch(
                  n -> "LEAVE_CREATED".equals(n.getType()) && n.getTitle().contains("2026-09-07"));
        });
  }

  @Test
  @Order(7)
  void notificationMetadata_containsCorrectFields() {
    // Verify notification from test 1 has correct metadata
    runInTenant(
        () -> {
          var notifs =
              notificationRepository.findByRecipientMemberId(
                  memberIdMember, PageRequest.of(0, 100));
          var allocChangedNotif =
              notifs.getContent().stream()
                  .filter(n -> "ALLOCATION_CHANGED".equals(n.getType()))
                  .findFirst()
                  .orElseThrow();
          assertThat(allocChangedNotif.getReferenceEntityType()).isEqualTo("RESOURCE_ALLOCATION");
          assertThat(allocChangedNotif.getReferenceEntityId()).isNotNull();
          assertThat(allocChangedNotif.getRecipientMemberId()).isEqualTo(memberIdMember);
        });
  }

  // ===== Audit Event Tests =====

  @Test
  @Order(10)
  void createCapacity_producesAuditEvent() {
    UUID[] recordId = new UUID[1];
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateCapacityRequest(
                  new BigDecimal("32.00"), LocalDate.of(2026, 10, 5), null, "Part-time");
          var response =
              capacityService.createCapacityRecord(memberIdMember, request, memberIdOwner);
          recordId[0] = response.id();
        });

    // Verify audit event
    runInTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "member_capacity", recordId[0], null, "member_capacity.created", null, null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("member_capacity.created");
          assertThat(event.getEntityType()).isEqualTo("member_capacity");
          assertThat(event.getDetails()).containsEntry("member_id", memberIdMember.toString());
          assertThat(event.getDetails()).containsEntry("weekly_hours", "32.00");
        });
  }

  @Test
  @Order(11)
  void createAllocation_producesAuditEvent() {
    UUID[] allocId = new UUID[1];
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdMember,
                  projectId,
                  LocalDate.of(2026, 10, 12), // Monday
                  new BigDecimal("10.00"),
                  "Audit test");
          var response = allocationService.createAllocation(request, memberIdOwner);
          allocId[0] = response.id();
        });

    // Verify audit event
    runInTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "resource_allocation",
                      allocId[0],
                      null,
                      "resource_allocation.created",
                      null,
                      null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("resource_allocation.created");
          assertThat(event.getEntityType()).isEqualTo("resource_allocation");
          assertThat(event.getDetails()).containsEntry("member_id", memberIdMember.toString());
          assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
          assertThat(event.getDetails()).containsEntry("allocated_hours", "10.00");
        });
  }

  @Test
  @Order(12)
  void updateAllocation_auditIncludesOldAndNewHours() {
    UUID[] allocId = new UUID[1];
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          // Find the allocation from test 11
          var allocations =
              allocationService.listAllocations(
                  memberIdMember, null, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
          assertThat(allocations).isNotEmpty();
          allocId[0] = allocations.getFirst().id();

          var updateRequest = new UpdateAllocationRequest(new BigDecimal("18.00"), "Updated");
          allocationService.updateAllocation(allocId[0], updateRequest);
        });

    // Verify audit event includes old and new hours
    runInTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "resource_allocation",
                      allocId[0],
                      null,
                      "resource_allocation.updated",
                      null,
                      null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getDetails()).containsEntry("old_hours", "10.00");
          assertThat(event.getDetails()).containsEntry("new_hours", "18.00");
        });
  }

  @Test
  @Order(13)
  void deleteLeave_producesAuditEvent() {
    UUID[] blockId = new UUID[1];
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var createRequest =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6), "To delete");
          var response =
              leaveBlockService.createLeaveBlock(memberIdOwner, createRequest, memberIdOwner);
          blockId[0] = response.id();

          leaveBlockService.deleteLeaveBlock(memberIdOwner, blockId[0]);
        });

    // Verify audit event
    runInTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "leave_block", blockId[0], null, "leave_block.deleted", null, null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("leave_block.deleted");
          assertThat(event.getDetails()).containsEntry("member_id", memberIdOwner.toString());
          assertThat(event.getDetails()).containsEntry("start_date", "2026-11-02");
          assertThat(event.getDetails()).containsEntry("end_date", "2026-11-06");
        });
  }

  @Test
  @Order(14)
  void auditEvents_haveCorrectActorFromRequestScopes() {
    UUID[] allocId = new UUID[1];
    runInTenantAs(
        memberIdOwner,
        "owner",
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdMember,
                  projectId,
                  LocalDate.of(2026, 11, 9), // Monday
                  new BigDecimal("8.00"),
                  "Actor test");
          var response = allocationService.createAllocation(request, memberIdOwner);
          allocId[0] = response.id();
        });

    // Verify actor is set from RequestScopes
    runInTenant(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "resource_allocation",
                      allocId[0],
                      null,
                      "resource_allocation.created",
                      null,
                      null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getActorId()).isEqualTo(memberIdOwner);
          assertThat(event.getActorType()).isEqualTo("USER");
        });
  }

  // ===== Helpers =====

  private void runInTenantAs(UUID actorId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, actorId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "%s",
                      "email": "%s",
                      "name": "%s",
                      "avatarUrl": null,
                      "orgRole": "%s"
                    }
                    """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
