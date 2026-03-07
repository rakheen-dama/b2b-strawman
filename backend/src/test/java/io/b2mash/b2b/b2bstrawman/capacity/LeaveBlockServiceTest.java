package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.CreateLeaveRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.LeaveDtos.LeaveBlockResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaveBlockServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_leave_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private LeaveBlockService leaveBlockService;
  @Autowired private LeaveBlockRepository leaveBlockRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String memberIdOwnerStr;
  private UUID memberIdMember;
  private String memberIdMemberStr;
  private String createdLeaveBlockId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Leave Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwnerStr =
        syncMember(ORG_ID, "user_leave_owner", "leave_owner@test.com", "Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdOwnerStr);
    memberIdMemberStr =
        syncMember(ORG_ID, "user_leave_member", "leave_member@test.com", "Member", "member");
    memberIdMember = UUID.fromString(memberIdMemberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // --- JWT Helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_leave_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_leave_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, memberIdOwner)
          .where(RequestScopes.ORG_ROLE, "owner")
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private <T> T runInTenantAsMember(java.util.concurrent.Callable<T> callable) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.MEMBER_ID, memberIdMember)
          .where(RequestScopes.ORG_ROLE, "member")
          .call(() -> callable.call());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // --- Member Sync Helper ---
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
  void createLeaveBlock_happyPath() {
    runInTenant(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 10), "Holiday");
          LeaveBlockResponse response =
              leaveBlockService.createLeaveBlock(memberIdOwner, request, memberIdOwner);

          assertThat(response.id()).isNotNull();
          assertThat(response.memberId()).isEqualTo(memberIdOwner);
          assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 4, 6));
          assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 4, 10));
          assertThat(response.note()).isEqualTo("Holiday");
          assertThat(response.createdBy()).isEqualTo(memberIdOwner);
          return null;
        });
  }

  @Test
  @Order(2)
  void createLeaveBlock_endDateBeforeStartDate_rejected() {
    runInTenant(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 6), "Bad dates");
          org.junit.jupiter.api.Assertions.assertThrows(
              io.b2mash.b2b.b2bstrawman.exception.InvalidStateException.class,
              () -> leaveBlockService.createLeaveBlock(memberIdOwner, request, memberIdOwner));
          return null;
        });
  }

  @Test
  @Order(3)
  void listLeaveForMember_orderedByStartDateDesc() {
    runInTenant(
        () -> {
          // Create a second leave block with earlier dates
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), "Earlier leave");
          leaveBlockService.createLeaveBlock(memberIdOwner, request, memberIdOwner);

          List<LeaveBlockResponse> leaves = leaveBlockService.listLeaveForMember(memberIdOwner);
          assertThat(leaves).hasSizeGreaterThanOrEqualTo(2);
          // First item should have later startDate
          assertThat(leaves.get(0).startDate()).isAfterOrEqualTo(leaves.get(1).startDate());
          return null;
        });
  }

  @Test
  @Order(4)
  void listAllLeave_teamCalendar_filtersDateRange() {
    runInTenant(
        () -> {
          List<LeaveBlockResponse> leaves =
              leaveBlockService.listAllLeave(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
          // Should include the April leave block but not the March one
          assertThat(leaves).anyMatch(l -> l.startDate().equals(LocalDate.of(2026, 4, 6)));
          assertThat(leaves).noneMatch(l -> l.startDate().equals(LocalDate.of(2026, 3, 2)));
          return null;
        });
  }

  @Test
  @Order(5)
  void overlapAllowed_twoBlocksSameDates() {
    runInTenant(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 10), "Overlapping leave");
          LeaveBlockResponse response =
              leaveBlockService.createLeaveBlock(memberIdOwner, request, memberIdOwner);
          assertThat(response.id()).isNotNull();
          return null;
        });
  }

  @Test
  @Order(6)
  void selfServiceCreateOwnLeave_memberRole() {
    runInTenantAsMember(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 8), "Member leave");
          LeaveBlockResponse response =
              leaveBlockService.createLeaveBlock(memberIdMember, request, memberIdMember);
          assertThat(response.memberId()).isEqualTo(memberIdMember);
          return null;
        });
  }

  @Test
  @Order(7)
  void selfServiceCreateOthersLeave_memberRole_rejected() {
    runInTenantAsMember(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 15), "Not allowed");
          org.junit.jupiter.api.Assertions.assertThrows(
              io.b2mash.b2b.b2bstrawman.exception.ForbiddenException.class,
              () -> leaveBlockService.createLeaveBlock(memberIdOwner, request, memberIdMember));
          return null;
        });
  }

  @Test
  @Order(8)
  void adminCreateAnyMembersLeave() {
    // Run as owner (admin-equivalent) creating leave for the member
    runInTenant(
        () -> {
          var request =
              new CreateLeaveRequest(
                  LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), "Admin-created");
          LeaveBlockResponse response =
              leaveBlockService.createLeaveBlock(memberIdMember, request, memberIdOwner);
          assertThat(response.memberId()).isEqualTo(memberIdMember);
          assertThat(response.createdBy()).isEqualTo(memberIdOwner);
          return null;
        });
  }

  // ===== Controller-level tests =====

  @Test
  @Order(20)
  void controller_createLeaveBlock_returns201() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/members/{memberId}/leave", memberIdOwnerStr)
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "startDate": "2026-07-06",
                      "endDate": "2026-07-10",
                      "note": "Summer holiday"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberId").value(memberIdOwnerStr))
            .andExpect(jsonPath("$.startDate").value("2026-07-06"))
            .andExpect(jsonPath("$.endDate").value("2026-07-10"))
            .andExpect(jsonPath("$.note").value("Summer holiday"))
            .andReturn();
    createdLeaveBlockId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(21)
  void controller_listLeaveForMember_returns200() throws Exception {
    mockMvc
        .perform(get("/api/members/{memberId}/leave", memberIdOwnerStr).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(22)
  void controller_updateLeaveBlock_returns200() throws Exception {
    mockMvc
        .perform(
            put("/api/members/{memberId}/leave/{id}", memberIdOwnerStr, createdLeaveBlockId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-07-06",
                      "endDate": "2026-07-17",
                      "note": "Extended holiday"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endDate").value("2026-07-17"))
        .andExpect(jsonPath("$.note").value("Extended holiday"));
  }

  @Test
  @Order(23)
  void controller_deleteLeaveBlock_returns204() throws Exception {
    mockMvc
        .perform(
            delete("/api/members/{memberId}/leave/{id}", memberIdOwnerStr, createdLeaveBlockId)
                .with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(24)
  void controller_teamCalendar_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/leave")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-12-31")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @Order(25)
  void controller_memberSelfService_createOwnLeave_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/leave", memberIdMemberStr)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-08-03",
                      "endDate": "2026-08-07",
                      "note": "Personal time"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").value(memberIdMemberStr));
  }

  @Test
  @Order(26)
  void controller_memberSelfService_createOthersLeave_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/leave", memberIdOwnerStr)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-08-10",
                      "endDate": "2026-08-14",
                      "note": "Should be blocked"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(27)
  void controller_endDateBeforeStartDate_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/leave", memberIdOwnerStr)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startDate": "2026-08-14",
                      "endDate": "2026-08-10",
                      "note": "Bad dates"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
