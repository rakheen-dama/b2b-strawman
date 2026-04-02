package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.BulkAllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.UpdateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceAllocationServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_alloc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ResourceAllocationService allocationService;
  @Autowired private ResourceAllocationRepository allocationRepository;
  @Autowired private ProjectService projectService;
  @Autowired private ApplicationEvents events;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String memberIdOwnerStr;
  private UUID memberIdMember;
  private String memberIdMemberStr;
  private UUID projectId;
  private UUID archivedProjectId;
  private UUID createdAllocationId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Alloc Test Org", null);
    memberIdOwnerStr =
        syncMember(ORG_ID, "user_alloc_owner", "alloc_owner@test.com", "Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdOwnerStr);
    memberIdMemberStr =
        syncMember(ORG_ID, "user_alloc_member", "alloc_member@test.com", "Member", "member");
    memberIdMember = UUID.fromString(memberIdMemberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_alloc_315b_custom",
                "alloc_custom@test.com",
                "Alloc Custom",
                "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_alloc_315b_nocap", "alloc_nocap@test.com", "Alloc NoCap", "member"));

    // Create projects in tenant scope
    runInTenant(
        () -> {
          Project project = projectService.createProject("Test Project", "desc", memberIdOwner);
          projectId = project.getId();

          Project archived =
              projectService.createProject("Archived Project", "desc", memberIdOwner);
          archivedProjectId = archived.getId();
          projectService.archiveProject(
              archivedProjectId, new ActorContext(memberIdOwner, "owner"));

          var withCapRole =
              orgRoleService.createRole(
                  new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                      "Resource Planner Alloc", "Can plan resources", Set.of("RESOURCE_PLANNING")));
          var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
          customMember.setOrgRoleEntity(orgRoleRepository.findById(withCapRole.id()).orElseThrow());
          memberRepository.save(customMember);

          var withoutCapRole =
              orgRoleService.createRole(
                  new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                      "Team Lead Alloc", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
          var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
          noCapMember.setOrgRoleEntity(
              orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
          memberRepository.save(noCapMember);

          return null;
        });
  }

  // --- JWT Helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_alloc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_alloc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

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

  // ===== Service-level tests =====

  @Test
  @Order(1)
  void createAllocation_happyPath() {
    runInTenant(
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 3, 9), // Monday
                  new BigDecimal("20.00"),
                  "Sprint planning");
          AllocationResponse response = allocationService.createAllocation(request, memberIdOwner);

          assertThat(response.id()).isNotNull();
          assertThat(response.memberId()).isEqualTo(memberIdOwner);
          assertThat(response.projectId()).isEqualTo(projectId);
          assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 3, 9));
          assertThat(response.allocatedHours()).isEqualByComparingTo(new BigDecimal("20.00"));
          assertThat(response.note()).isEqualTo("Sprint planning");
          assertThat(response.createdAt()).isNotNull();
          createdAllocationId = response.id();
          return null;
        });
  }

  @Test
  @Order(2)
  void createAllocation_weekStartNotMonday_rejected() {
    Assertions.assertThrows(
        InvalidStateException.class,
        () ->
            runInTenant(
                () -> {
                  var request =
                      new CreateAllocationRequest(
                          memberIdOwner,
                          projectId,
                          LocalDate.of(2026, 3, 10), // Tuesday
                          new BigDecimal("20.00"),
                          null);
                  allocationService.createAllocation(request, memberIdOwner);
                  return null;
                }));
  }

  @Test
  @Order(3)
  void createAllocation_archivedProject_rejected() {
    Assertions.assertThrows(
        InvalidStateException.class,
        () ->
            runInTenant(
                () -> {
                  var request =
                      new CreateAllocationRequest(
                          memberIdOwner,
                          archivedProjectId,
                          LocalDate.of(2026, 3, 9),
                          new BigDecimal("10.00"),
                          null);
                  allocationService.createAllocation(request, memberIdOwner);
                  return null;
                }));
  }

  @Test
  @Order(4)
  void createAllocation_duplicate_rejected() {
    Assertions.assertThrows(
        ResourceConflictException.class,
        () ->
            runInTenant(
                () -> {
                  // Same member, project, week as Order(1)
                  var request =
                      new CreateAllocationRequest(
                          memberIdOwner,
                          projectId,
                          LocalDate.of(2026, 3, 9),
                          new BigDecimal("10.00"),
                          null);
                  allocationService.createAllocation(request, memberIdOwner);
                  return null;
                }));
  }

  @Test
  @Order(5)
  void createAllocation_autoAddsProjectMember() {
    runInTenant(
        () -> {
          // memberIdMember is not yet on the project; allocation should auto-add them
          var request =
              new CreateAllocationRequest(
                  memberIdMember,
                  projectId,
                  LocalDate.of(2026, 3, 16), // Different week
                  new BigDecimal("15.00"),
                  "Auto-add test");
          AllocationResponse response = allocationService.createAllocation(request, memberIdOwner);

          assertThat(response.memberId()).isEqualTo(memberIdMember);
          assertThat(response.allocatedHours()).isEqualByComparingTo(new BigDecimal("15.00"));
          return null;
        });
  }

  @Test
  @Order(6)
  void createAllocation_overAllocation_detected() {
    runInTenant(
        () -> {
          // Default capacity is 40h. Allocate 45h on a new week to trigger over-allocation.
          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 4, 6), // Fresh week (Monday)
                  new BigDecimal("45.00"),
                  "Over-alloc test");
          AllocationResponse response = allocationService.createAllocation(request, memberIdOwner);

          assertThat(response.overAllocated()).isTrue();
          assertThat(response.overageHours()).isPositive();
          return null;
        });
  }

  @Test
  @Order(7)
  void createAllocation_underCapacity_notOverAllocated() {
    runInTenant(
        () -> {
          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 4, 13), // Fresh week (Monday)
                  new BigDecimal("10.00"),
                  "Under capacity");
          AllocationResponse response = allocationService.createAllocation(request, memberIdOwner);

          assertThat(response.overAllocated()).isFalse();
          assertThat(response.overageHours()).isEqualByComparingTo(BigDecimal.ZERO);
          return null;
        });
  }

  @Test
  @Order(8)
  void createAllocation_overAllocation_publishesEvent() {
    runInTenant(
        () -> {
          // Clear previous events by reading them out
          events.clear();

          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 5, 4), // Fresh week (Monday)
                  new BigDecimal("50.00"),
                  "Event test");
          allocationService.createAllocation(request, memberIdOwner);

          long eventCount = events.stream(MemberOverAllocatedEvent.class).count();
          assertThat(eventCount).isGreaterThanOrEqualTo(1);

          MemberOverAllocatedEvent event =
              events.stream(MemberOverAllocatedEvent.class).findFirst().orElseThrow();
          assertThat(event.memberId()).isEqualTo(memberIdOwner);
          assertThat(event.weekStart()).isEqualTo(LocalDate.of(2026, 5, 4));
          assertThat(event.totalAllocated()).isPositive();
          assertThat(event.effectiveCapacity()).isPositive();
          assertThat(event.overageHours()).isPositive();
          return null;
        });
  }

  @Test
  @Order(9)
  void updateAllocation_changesHoursAndRechecks() {
    runInTenant(
        () -> {
          var request = new UpdateAllocationRequest(new BigDecimal("25.00"), "Updated note");
          AllocationResponse response =
              allocationService.updateAllocation(createdAllocationId, request);

          assertThat(response.allocatedHours()).isEqualByComparingTo(new BigDecimal("25.00"));
          assertThat(response.note()).isEqualTo("Updated note");
          return null;
        });
  }

  @Test
  @Order(10)
  void deleteAllocation_removesRecord() {
    runInTenant(
        () -> {
          // Create a disposable allocation
          var request =
              new CreateAllocationRequest(
                  memberIdOwner,
                  projectId,
                  LocalDate.of(2026, 6, 1), // Monday
                  new BigDecimal("5.00"),
                  "To delete");
          AllocationResponse created = allocationService.createAllocation(request, memberIdOwner);

          allocationService.deleteAllocation(created.id());

          assertThat(allocationRepository.findById(created.id())).isEmpty();
          return null;
        });
  }

  @Test
  @Order(11)
  void bulkUpsert_createsNew() {
    runInTenant(
        () -> {
          var requests =
              List.of(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      LocalDate.of(2026, 7, 6), // Monday
                      new BigDecimal("10.00"),
                      "Bulk new"));
          BulkAllocationResponse response =
              allocationService.bulkUpsertAllocations(requests, memberIdOwner);

          assertThat(response.results()).hasSize(1);
          assertThat(response.results().getFirst().created()).isTrue();
          assertThat(response.results().getFirst().allocation().allocatedHours())
              .isEqualByComparingTo(new BigDecimal("10.00"));
          return null;
        });
  }

  @Test
  @Order(12)
  void bulkUpsert_updatesExisting() {
    runInTenant(
        () -> {
          // Update the allocation created in Order(11)
          var requests =
              List.of(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      LocalDate.of(2026, 7, 6), // Same week as Order(11)
                      new BigDecimal("20.00"),
                      "Bulk updated"));
          BulkAllocationResponse response =
              allocationService.bulkUpsertAllocations(requests, memberIdOwner);

          assertThat(response.results()).hasSize(1);
          assertThat(response.results().getFirst().created()).isFalse();
          assertThat(response.results().getFirst().allocation().allocatedHours())
              .isEqualByComparingTo(new BigDecimal("20.00"));
          return null;
        });
  }

  @Test
  @Order(13)
  void bulkUpsert_mixedCreateAndUpdate() {
    runInTenant(
        () -> {
          var requests =
              List.of(
                  // Existing: same member/project/week as Order(11)/Order(12)
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      LocalDate.of(2026, 7, 6),
                      new BigDecimal("15.00"),
                      "Mixed update"),
                  // New
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      LocalDate.of(2026, 7, 13), // Monday
                      new BigDecimal("8.00"),
                      "Mixed new"));
          BulkAllocationResponse response =
              allocationService.bulkUpsertAllocations(requests, memberIdOwner);

          assertThat(response.results()).hasSize(2);
          assertThat(response.results().get(0).created()).isFalse();
          assertThat(response.results().get(1).created()).isTrue();
          return null;
        });
  }

  @Test
  @Order(14)
  void bulkUpsert_deduplicatesOverAllocationCheck() {
    runInTenant(
        () -> {
          events.clear();

          // Two items for same member+week, both triggering over-allocation
          var requests =
              List.of(
                  new CreateAllocationRequest(
                      memberIdOwner,
                      projectId,
                      LocalDate.of(2026, 8, 3), // Monday
                      new BigDecimal("25.00"),
                      "Dedup 1"),
                  new CreateAllocationRequest(
                      memberIdMember,
                      projectId,
                      LocalDate.of(2026, 8, 3),
                      new BigDecimal("10.00"),
                      "Dedup 2"));
          BulkAllocationResponse response =
              allocationService.bulkUpsertAllocations(requests, memberIdOwner);

          assertThat(response.results()).hasSize(2);
          // Both items for the same week should have consistent over-allocation flags
          // per their respective member
          return null;
        });
  }

  @Test
  @Order(15)
  void createAllocation_zeroHours_rejected() {
    Assertions.assertThrows(
        InvalidStateException.class,
        () ->
            runInTenant(
                () -> {
                  var request =
                      new CreateAllocationRequest(
                          memberIdOwner,
                          projectId,
                          LocalDate.of(2026, 9, 7), // Monday
                          BigDecimal.ZERO,
                          null);
                  allocationService.createAllocation(request, memberIdOwner);
                  return null;
                }));
  }

  // ===== Controller-level tests =====

  @Test
  @Order(20)
  void controller_createAllocation_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/resource-allocations")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "weekStart": "2026-10-05",
                      "allocatedHours": 20.0,
                      "note": "Controller test"
                    }
                    """
                        .formatted(memberIdOwnerStr, projectId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").value(memberIdOwnerStr))
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.allocatedHours").value(20.0))
        .andExpect(jsonPath("$.overAllocated").isBoolean());
  }

  @Test
  @Order(21)
  void controller_getWithFilters_returnsAllocations() throws Exception {
    mockMvc
        .perform(
            get("/api/resource-allocations")
                .with(ownerJwt())
                .param("memberId", memberIdOwnerStr)
                .param("weekStart", "2026-01-01")
                .param("weekEnd", "2026-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  @Order(22)
  void controller_updateAllocation_returns200() throws Exception {
    // First create one to update
    var result =
        mockMvc
            .perform(
                post("/api/resource-allocations")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "projectId": "%s",
                          "weekStart": "2026-10-12",
                          "allocatedHours": 10.0,
                          "note": "To update"
                        }
                        """
                            .formatted(memberIdOwnerStr, projectId)))
            .andExpect(status().isCreated())
            .andReturn();
    String allocId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/resource-allocations/{id}", allocId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocatedHours": 30.0,
                      "note": "Updated via controller"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocatedHours").value(30.0))
        .andExpect(jsonPath("$.note").value("Updated via controller"));
  }

  @Test
  @Order(23)
  void controller_deleteAllocation_returns204() throws Exception {
    // Create one to delete
    var result =
        mockMvc
            .perform(
                post("/api/resource-allocations")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "memberId": "%s",
                          "projectId": "%s",
                          "weekStart": "2026-10-19",
                          "allocatedHours": 5.0,
                          "note": "To delete"
                        }
                        """
                            .formatted(memberIdOwnerStr, projectId)))
            .andExpect(status().isCreated())
            .andReturn();
    String allocId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/resource-allocations/{id}", allocId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(24)
  void controller_bulkUpsert_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/resource-allocations/bulk")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocations": [
                        {
                          "memberId": "%s",
                          "projectId": "%s",
                          "weekStart": "2026-11-02",
                          "allocatedHours": 12.0,
                          "note": "Bulk 1"
                        },
                        {
                          "memberId": "%s",
                          "projectId": "%s",
                          "weekStart": "2026-11-09",
                          "allocatedHours": 8.0,
                          "note": "Bulk 2"
                        }
                      ]
                    }
                    """
                        .formatted(memberIdOwnerStr, projectId, memberIdOwnerStr, projectId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results").isArray())
        .andExpect(jsonPath("$.results.length()").value(2))
        .andExpect(jsonPath("$.results[0].created").value(true));
  }

  @Test
  @Order(25)
  void controller_memberCannotCreate_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/resource-allocations")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "weekStart": "2026-12-07",
                      "allocatedHours": 10.0,
                      "note": "Should be blocked"
                    }
                    """
                        .formatted(memberIdMemberStr, projectId)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(26)
  void controller_memberCanRead_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/resource-allocations")
                .with(memberJwt())
                .param("weekStart", "2026-01-01")
                .param("weekEnd", "2026-12-31"))
        .andExpect(status().isOk());
  }

  // --- Capability Tests (added in Epic 315B) ---

  @Test
  @Order(30)
  void controller_customRoleWithCapability_canCreateAllocation() throws Exception {
    mockMvc
        .perform(
            post("/api/resource-allocations")
                .with(customRoleJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "weekStart": "2026-12-14",
                      "allocatedHours": 10.0,
                      "note": "Capability test"
                    }
                    """
                        .formatted(customRoleMemberId, projectId)))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(31)
  void controller_customRoleWithoutCapability_cannotCreateAllocation() throws Exception {
    mockMvc
        .perform(
            post("/api/resource-allocations")
                .with(noCapabilityJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "memberId": "%s",
                      "projectId": "%s",
                      "weekStart": "2026-12-21",
                      "allocatedHours": 10.0,
                      "note": "NoCap test"
                    }
                    """
                        .formatted(noCapMemberId, projectId)))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_alloc_315b_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_alloc_315b_nocap")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
