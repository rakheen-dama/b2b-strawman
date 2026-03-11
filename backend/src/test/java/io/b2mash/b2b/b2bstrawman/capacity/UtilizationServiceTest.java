package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.MemberUtilizationSummary;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.TeamUtilizationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.WeekUtilization;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UtilizationServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_util_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private UtilizationService utilizationService;
  @Autowired private ResourceAllocationService allocationService;
  @Autowired private LeaveBlockService leaveBlockService;
  @Autowired private ProjectService projectService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryRepository timeEntryRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID memberIdMember;
  private UUID projectId;
  private UUID projectId2;
  private UUID taskId;

  // Week boundaries: Monday 2026-03-09 to Monday 2026-03-16
  private static final LocalDate WEEK1_START = LocalDate.of(2026, 3, 9);
  private static final LocalDate WEEK2_START = LocalDate.of(2026, 3, 16);

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Util Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    String ownerStr =
        syncMember(ORG_ID, "user_util_owner", "util_owner@test.com", "Util Owner", "owner");
    memberIdOwner = UUID.fromString(ownerStr);
    String memberStr =
        syncMember(ORG_ID, "user_util_member", "util_member@test.com", "Util Member", "member");
    memberIdMember = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () -> {
          Project project = projectService.createProject("Util Project", "desc", memberIdOwner);
          projectId = project.getId();
          Project project2 = projectService.createProject("Util Project 2", "desc", memberIdOwner);
          projectId2 = project2.getId();
          Task task =
              taskService.createTask(
                  projectId,
                  "Test Task",
                  "desc",
                  "MEDIUM",
                  "TASK",
                  null,
                  new ActorContext(memberIdOwner, "owner"));
          taskId = task.getId();
          return null;
        });
  }

  @Test
  @Order(1)
  void memberUtilization_plannedOnly() {
    runInTenant(
        () -> {
          // Allocate 20 hours in week 1
          allocationService.createAllocation(
              new CreateAllocationRequest(
                  memberIdOwner, projectId, WEEK1_START, new BigDecimal("20.00"), "test"),
              memberIdOwner);

          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdOwner, WEEK1_START, WEEK1_START);

          assertThat(result).hasSize(1);
          WeekUtilization week = result.getFirst();
          assertThat(week.weekStart()).isEqualTo(WEEK1_START);
          assertThat(week.plannedHours()).isEqualByComparingTo("20.00");
          assertThat(week.actualHours()).isEqualByComparingTo("0");
          assertThat(week.effectiveCapacity()).isEqualByComparingTo("40.00");
          assertThat(week.plannedUtilizationPct()).isEqualByComparingTo("50.00");
          assertThat(week.actualUtilizationPct()).isEqualByComparingTo("0");
          return null;
        });
  }

  @Test
  @Order(2)
  void memberUtilization_actualOnly() {
    runInTenant(
        () -> {
          // Log 4 hours (240 minutes) of time on Tuesday of week 1
          TimeEntry entry =
              new TimeEntry(
                  taskId, memberIdMember, WEEK1_START.plusDays(1), 240, true, null, "work");
          timeEntryRepository.save(entry);

          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdMember, WEEK1_START, WEEK1_START);

          assertThat(result).hasSize(1);
          WeekUtilization week = result.getFirst();
          assertThat(week.actualHours()).isEqualByComparingTo("4.0");
          assertThat(week.billableActualHours()).isEqualByComparingTo("4.0");
          assertThat(week.plannedHours()).isEqualByComparingTo("0");
          assertThat(week.actualUtilizationPct()).isEqualByComparingTo("10.00");
          return null;
        });
  }

  @Test
  @Order(3)
  void memberUtilization_plannedAndActual() {
    runInTenant(
        () -> {
          // Member already has 4 hours actual from test 2
          // Add allocation for memberIdMember
          allocationService.createAllocation(
              new CreateAllocationRequest(
                  memberIdMember, projectId, WEEK1_START, new BigDecimal("30.00"), "test"),
              memberIdOwner);

          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdMember, WEEK1_START, WEEK1_START);

          assertThat(result).hasSize(1);
          WeekUtilization week = result.getFirst();
          assertThat(week.plannedHours()).isEqualByComparingTo("30.00");
          assertThat(week.actualHours()).isEqualByComparingTo("4.0");
          assertThat(week.plannedUtilizationPct()).isEqualByComparingTo("75.00");
          return null;
        });
  }

  @Test
  @Order(4)
  void memberUtilization_billableVsNonBillable() {
    runInTenant(
        () -> {
          // Add non-billable time entry in week 2 for memberIdMember
          TimeEntry nonBillable =
              new TimeEntry(
                  taskId, memberIdMember, WEEK2_START.plusDays(2), 120, false, null, "admin");
          timeEntryRepository.save(nonBillable);

          // Add billable time entry in week 2
          TimeEntry billable =
              new TimeEntry(
                  taskId, memberIdMember, WEEK2_START.plusDays(3), 180, true, null, "client work");
          timeEntryRepository.save(billable);

          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdMember, WEEK2_START, WEEK2_START);

          assertThat(result).hasSize(1);
          WeekUtilization week = result.getFirst();
          // Total actual = 120 + 180 = 300 minutes = 5.0 hours
          assertThat(week.actualHours()).isEqualByComparingTo("5.0");
          // Billable actual = 180 minutes = 3.0 hours
          assertThat(week.billableActualHours()).isEqualByComparingTo("3.0");
          assertThat(week.billableUtilizationPct()).isEqualByComparingTo("7.50");
          return null;
        });
  }

  @Test
  @Order(5)
  void memberUtilization_zeroCapacity_returnsZeroPct() {
    runInTenant(
        () -> {
          // Create leave block covering full week 2 for owner (Mon-Fri)
          leaveBlockService.createLeaveBlock(
              memberIdOwner,
              new CreateLeaveRequest(WEEK2_START, WEEK2_START.plusDays(4), "vacation"),
              memberIdOwner);

          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdOwner, WEEK2_START, WEEK2_START);

          assertThat(result).hasSize(1);
          WeekUtilization week = result.getFirst();
          assertThat(week.effectiveCapacity()).isEqualByComparingTo("0");
          assertThat(week.plannedUtilizationPct()).isEqualByComparingTo("0");
          assertThat(week.actualUtilizationPct()).isEqualByComparingTo("0");
          return null;
        });
  }

  @Test
  @Order(6)
  void memberUtilization_multipleWeeks() {
    runInTenant(
        () -> {
          List<WeekUtilization> result =
              utilizationService.getMemberUtilization(memberIdMember, WEEK1_START, WEEK2_START);

          assertThat(result).hasSize(2);
          assertThat(result.get(0).weekStart()).isEqualTo(WEEK1_START);
          assertThat(result.get(1).weekStart()).isEqualTo(WEEK2_START);
          return null;
        });
  }

  @Test
  @Order(7)
  void teamUtilization_aggregation() {
    runInTenant(
        () -> {
          TeamUtilizationResponse result =
              utilizationService.getTeamUtilization(WEEK1_START, WEEK1_START);

          assertThat(result.members()).hasSize(2);
          assertThat(result.teamAverages()).isNotNull();
          assertThat(result.teamAverages().avgPlannedUtilizationPct())
              .isGreaterThanOrEqualTo(BigDecimal.ZERO);
          return null;
        });
  }

  @Test
  @Order(8)
  void teamUtilization_memberSummaries() {
    runInTenant(
        () -> {
          TeamUtilizationResponse result =
              utilizationService.getTeamUtilization(WEEK1_START, WEEK1_START);

          // Owner has 20h planned, member has 30h planned in week 1
          MemberUtilizationSummary ownerSummary =
              result.members().stream()
                  .filter(m -> m.memberId().equals(memberIdOwner))
                  .findFirst()
                  .orElseThrow();
          assertThat(ownerSummary.totalPlannedHours()).isEqualByComparingTo("20.00");
          assertThat(ownerSummary.memberName()).isEqualTo("Util Owner");

          MemberUtilizationSummary memberSummary =
              result.members().stream()
                  .filter(m -> m.memberId().equals(memberIdMember))
                  .findFirst()
                  .orElseThrow();
          assertThat(memberSummary.totalPlannedHours()).isEqualByComparingTo("30.00");
          assertThat(memberSummary.totalActualHours()).isEqualByComparingTo("4.0");
          return null;
        });
  }

  @Test
  @Order(9)
  void teamUtilization_overAllocatedWeeks() {
    runInTenant(
        () -> {
          // Allocate owner to exceed capacity in week 1 (already has 20h on project1, add 25h on
          // project2)
          allocationService.createAllocation(
              new CreateAllocationRequest(
                  memberIdOwner, projectId2, WEEK1_START, new BigDecimal("25.00"), "over-allocate"),
              memberIdOwner);

          TeamUtilizationResponse result =
              utilizationService.getTeamUtilization(WEEK1_START, WEEK1_START);

          MemberUtilizationSummary ownerSummary =
              result.members().stream()
                  .filter(m -> m.memberId().equals(memberIdOwner))
                  .findFirst()
                  .orElseThrow();
          // 20 + 25 = 45 > 40 capacity => over-allocated
          assertThat(ownerSummary.overAllocatedWeeks()).isEqualTo(1);
          assertThat(ownerSummary.totalPlannedHours()).isEqualByComparingTo("45.00");
          return null;
        });
  }

  @Test
  @Order(10)
  void teamUtilization_emptyRange() {
    runInTenant(
        () -> {
          // Use a far-future date range with no data
          LocalDate future = LocalDate.of(2028, 1, 5); // Monday
          TeamUtilizationResponse result = utilizationService.getTeamUtilization(future, future);

          assertThat(result.members()).hasSize(2);
          for (MemberUtilizationSummary summary : result.members()) {
            assertThat(summary.totalPlannedHours()).isEqualByComparingTo("0");
            assertThat(summary.totalActualHours()).isEqualByComparingTo("0");
          }
          return null;
        });
  }

  // --- Helpers ---

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, memberIdOwner)
          .where(RequestScopes.ORG_ROLE, "owner")
          .where(RequestScopes.ORG_ID, ORG_ID)
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
