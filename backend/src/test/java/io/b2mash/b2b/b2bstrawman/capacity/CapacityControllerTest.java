package io.b2mash.b2b.b2bstrawman.capacity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CapacityControllerTest {
  private static final String ORG_ID = "org_cap_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ResourceAllocationService allocationService;
  @Autowired private ProjectService projectService;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String memberIdOwnerStr;
  private UUID projectId;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 6); // Monday
  private static final LocalDate WEEK_END = LocalDate.of(2026, 4, 13); // Monday

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cap Ctrl Test Org", null);
    memberIdOwnerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_cap_ctrl_owner",
            "cap_ctrl_owner@test.com",
            "Cap Owner",
            "owner");
    memberIdOwner = UUID.fromString(memberIdOwnerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cap_315b_custom",
                "cap_custom@test.com",
                "Cap Custom User",
                "member"));
    noCapMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cap_315b_nocap",
                "cap_nocap@test.com",
                "Cap NoCap User",
                "member"));

    runInTenant(
        () -> {
          Project project = projectService.createProject("Cap Ctrl Project", "desc", memberIdOwner);
          projectId = project.getId();

          // Create allocation for testing
          allocationService.createAllocation(
              new CreateAllocationRequest(
                  memberIdOwner, projectId, WEEK_START, new BigDecimal("25.00"), "sprint"),
              memberIdOwner);

          var withCapRole =
              orgRoleService.createRole(
                  new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                      "Resource Planner", "Can plan resources", Set.of("RESOURCE_PLANNING")));
          var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
          customMember.setOrgRoleEntity(orgRoleRepository.findById(withCapRole.id()).orElseThrow());
          memberRepository.save(customMember);

          var withoutCapRole =
              orgRoleService.createRole(
                  new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                      "Team Lead Cap", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
          var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
          noCapMember.setOrgRoleEntity(
              orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
          memberRepository.save(noCapMember);

          return null;
        });
  }

  @Test
  @Order(1)
  void teamCapacityGrid_returnsGridStructure() throws Exception {
    mockMvc
        .perform(
            get("/api/capacity/team")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cap_ctrl_owner"))
                .param("weekStart", WEEK_START.toString())
                .param("weekEnd", WEEK_END.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.members[0].memberId").value(memberIdOwnerStr))
        .andExpect(jsonPath("$.members[0].memberName").value("Cap Owner"))
        .andExpect(jsonPath("$.members[0].weeks").isArray())
        .andExpect(jsonPath("$.weekSummaries").isArray());
  }

  @Test
  @Order(2)
  void teamCapacityGrid_weekCellsHaveUtilization() throws Exception {
    mockMvc
        .perform(
            get("/api/capacity/team")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cap_ctrl_owner"))
                .param("weekStart", WEEK_START.toString())
                .param("weekEnd", WEEK_START.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].weeks[0].totalAllocated").value(25.0))
        .andExpect(jsonPath("$.members[0].weeks[0].effectiveCapacity").value(40.0))
        .andExpect(jsonPath("$.members[0].weeks[0].remainingCapacity").value(15.0))
        .andExpect(jsonPath("$.members[0].weeks[0].utilizationPct").value(62.5))
        .andExpect(jsonPath("$.members[0].weeks[0].overAllocated").value(false));
  }

  @Test
  @Order(3)
  void projectStaffing_returnsAllocatedMembers() throws Exception {
    mockMvc
        .perform(
            get("/api/capacity/projects/{projectId}", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cap_ctrl_owner"))
                .param("weekStart", WEEK_START.toString())
                .param("weekEnd", WEEK_END.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.projectName").value("Cap Ctrl Project"))
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.members[0].memberId").value(memberIdOwnerStr))
        .andExpect(jsonPath("$.totalPlannedHours").value(25.0));
  }

  @Test
  @Order(4)
  void teamUtilization_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/utilization/team")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cap_ctrl_owner"))
                .param("weekStart", WEEK_START.toString())
                .param("weekEnd", WEEK_START.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.teamAverages").exists())
        .andExpect(jsonPath("$.teamAverages.avgPlannedUtilizationPct").isNumber());
  }

  @Test
  @Order(5)
  void memberCapacityDetail_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/capacity/members/{memberId}", memberIdOwner)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cap_ctrl_owner"))
                .param("weekStart", WEEK_START.toString())
                .param("weekEnd", WEEK_START.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(memberIdOwnerStr))
        .andExpect(jsonPath("$.memberName").value("Cap Owner"))
        .andExpect(jsonPath("$.weeks").isArray())
        .andExpect(jsonPath("$.weeks[0].allocations[0].projectName").value("Cap Ctrl Project"));
  }

  // --- Capability Tests (added in Epic 315B) ---

  @Test
  @Order(10)
  void customRoleWithCapability_accessesMemberCapacity_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/members/{memberId}/capacity", memberIdOwner)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cap_315b_custom")))
        .andExpect(status().isOk());
  }

  @Test
  @Order(11)
  void customRoleWithoutCapability_accessesMemberCapacity_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/members/{memberId}/capacity", memberIdOwner)
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_cap_315b_nocap")))
        .andExpect(status().isForbidden());
  } // --- Helpers ---

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
}
