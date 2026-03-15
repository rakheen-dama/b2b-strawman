package io.b2mash.b2b.b2bstrawman.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CapacityServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_capacity_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CapacityService capacityService;
  @Autowired private MemberCapacityRepository memberCapacityRepository;
  @Autowired private LeaveBlockRepository leaveBlockRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String memberIdOwnerStr;
  // Additional members for service-level tests that need FK-valid member IDs
  private UUID memberFallback;
  private UUID memberOverlap;
  private UUID memberExpired;
  private UUID memberNoLeave;
  private UUID memberLeave2;
  private UUID memberFullLeave;
  private UUID memberOverlapLeave;
  private UUID memberWeekend;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Capacity Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwnerStr = syncMember(ORG_ID, "user_cap_owner", "cap_owner@test.com", "Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdOwnerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    memberFallback =
        UUID.fromString(syncMember(ORG_ID, "user_cap_fb", "cap_fb@test.com", "Fallback", "member"));
    memberOverlap =
        UUID.fromString(syncMember(ORG_ID, "user_cap_ol", "cap_ol@test.com", "Overlap", "member"));
    memberExpired =
        UUID.fromString(syncMember(ORG_ID, "user_cap_ex", "cap_ex@test.com", "Expired", "member"));
    memberNoLeave =
        UUID.fromString(syncMember(ORG_ID, "user_cap_nl", "cap_nl@test.com", "NoLeave", "member"));
    memberLeave2 =
        UUID.fromString(syncMember(ORG_ID, "user_cap_l2", "cap_l2@test.com", "Leave2", "member"));
    memberFullLeave =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_fl", "cap_fl@test.com", "FullLeave", "member"));
    memberOverlapLeave =
        UUID.fromString(
            syncMember(ORG_ID, "user_cap_orl", "cap_orl@test.com", "OverlapLeave", "member"));
    memberWeekend =
        UUID.fromString(syncMember(ORG_ID, "user_cap_wk", "cap_wk@test.com", "Weekend", "member"));
  }

  // --- JWT Helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cap_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
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

  // ===== Service-level tests (290.6) =====

  @Test
  @Order(1)
  void getMemberCapacity_withMatchingRecord() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 3, 2); // Monday
          var record =
              new MemberCapacity(
                  memberIdOwner, new BigDecimal("32.00"), monday, null, "Part-time", memberIdOwner);
          memberCapacityRepository.save(record);

          BigDecimal capacity = capacityService.getMemberCapacity(memberIdOwner, monday);
          assertThat(capacity).isEqualByComparingTo(new BigDecimal("32.00"));
          return null;
        });
  }

  @Test
  @Order(2)
  void getMemberCapacity_fallbackToOrgSettings() {
    // memberFallback has no capacity records, so fallback to org default (40.00)
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 3, 2);
          BigDecimal capacity = capacityService.getMemberCapacity(memberFallback, monday);
          assertThat(capacity).isEqualByComparingTo(new BigDecimal("40.00"));
          return null;
        });
  }

  @Test
  @Order(3)
  void getMemberCapacity_hardDefault40() {
    // memberFallback has no capacity records and no org settings => hard default 40.0
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 6, 1); // Monday
          BigDecimal capacity = capacityService.getMemberCapacity(memberFallback, monday);
          assertThat(capacity).isEqualByComparingTo(new BigDecimal("40.00"));
          return null;
        });
  }

  @Test
  @Order(4)
  void getMemberCapacity_overlappingDates_latestEffectiveFromWins() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 4, 6); // Monday
          // Older record
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberOverlap,
                  new BigDecimal("40.00"),
                  LocalDate.of(2026, 3, 2),
                  null,
                  "Full-time",
                  memberIdOwner));
          // Newer record (should win)
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberOverlap,
                  new BigDecimal("24.00"),
                  LocalDate.of(2026, 3, 30),
                  null,
                  "Reduced",
                  memberIdOwner));

          BigDecimal capacity = capacityService.getMemberCapacity(memberOverlap, monday);
          assertThat(capacity).isEqualByComparingTo(new BigDecimal("24.00"));
          return null;
        });
  }

  @Test
  @Order(5)
  void getMemberCapacity_effectiveToInPast_recordSkipped() {
    runInTenant(
        () -> {
          LocalDate queryMonday = LocalDate.of(2026, 5, 4); // Monday
          // Record expired before query date
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberExpired,
                  new BigDecimal("20.00"),
                  LocalDate.of(2026, 3, 2),
                  LocalDate.of(2026, 4, 30),
                  "Expired",
                  memberIdOwner));

          BigDecimal capacity = capacityService.getMemberCapacity(memberExpired, queryMonday);
          // Falls back to default since record expired
          assertThat(capacity).isEqualByComparingTo(new BigDecimal("40.00"));
          return null;
        });
  }

  @Test
  @Order(6)
  void getMemberEffectiveCapacity_noLeave_fullCapacity() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 3, 9);
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberNoLeave, new BigDecimal("40.00"), monday, null, null, memberIdOwner));

          BigDecimal effective = capacityService.getMemberEffectiveCapacity(memberNoLeave, monday);
          assertThat(effective).isEqualByComparingTo(new BigDecimal("40.00"));
          return null;
        });
  }

  @Test
  @Order(7)
  void getMemberEffectiveCapacity_with2LeaveDays() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 3, 16);
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberLeave2, new BigDecimal("40.00"), monday, null, null, memberIdOwner));
          // Leave on Tuesday and Wednesday
          leaveBlockRepository.save(
              new LeaveBlock(
                  memberLeave2,
                  LocalDate.of(2026, 3, 17), // Tue
                  LocalDate.of(2026, 3, 18), // Wed
                  "Sick leave",
                  memberIdOwner));

          BigDecimal effective = capacityService.getMemberEffectiveCapacity(memberLeave2, monday);
          // 40.00 * 3/5 = 24.00
          assertThat(effective).isEqualByComparingTo(new BigDecimal("24.00"));
          return null;
        });
  }

  @Test
  @Order(8)
  void getMemberEffectiveCapacity_fullWeekLeave_zeroCapacity() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 3, 23);
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberFullLeave, new BigDecimal("40.00"), monday, null, null, memberIdOwner));
          // Full week leave
          leaveBlockRepository.save(
              new LeaveBlock(
                  memberFullLeave,
                  LocalDate.of(2026, 3, 23), // Mon
                  LocalDate.of(2026, 3, 27), // Fri
                  "Vacation",
                  memberIdOwner));

          BigDecimal effective =
              capacityService.getMemberEffectiveCapacity(memberFullLeave, monday);
          assertThat(effective).isEqualByComparingTo(BigDecimal.ZERO);
          return null;
        });
  }

  @Test
  @Order(9)
  void getMemberEffectiveCapacity_overlappingLeaveBlocks_deduplicated() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 4, 13);
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberOverlapLeave, new BigDecimal("40.00"), monday, null, null, memberIdOwner));
          // Block 1: Mon-Wed
          leaveBlockRepository.save(
              new LeaveBlock(
                  memberOverlapLeave,
                  LocalDate.of(2026, 4, 13),
                  LocalDate.of(2026, 4, 15),
                  "Leave 1",
                  memberIdOwner));
          // Block 2: Tue-Thu (overlaps with block 1 on Tue-Wed)
          leaveBlockRepository.save(
              new LeaveBlock(
                  memberOverlapLeave,
                  LocalDate.of(2026, 4, 14),
                  LocalDate.of(2026, 4, 16),
                  "Leave 2",
                  memberIdOwner));

          BigDecimal effective =
              capacityService.getMemberEffectiveCapacity(memberOverlapLeave, monday);
          // Unique leave days: Mon, Tue, Wed, Thu = 4 days. 40.00 * 1/5 = 8.00
          assertThat(effective).isEqualByComparingTo(new BigDecimal("8.00"));
          return null;
        });
  }

  @Test
  @Order(10)
  void getMemberEffectiveCapacity_weekendOnlyLeave_noReduction() {
    runInTenant(
        () -> {
          LocalDate monday = LocalDate.of(2026, 4, 20);
          memberCapacityRepository.save(
              new MemberCapacity(
                  memberWeekend, new BigDecimal("40.00"), monday, null, null, memberIdOwner));
          // Leave on Sat-Sun only (falls outside Mon-Fri range for leave counting)
          leaveBlockRepository.save(
              new LeaveBlock(
                  memberWeekend,
                  LocalDate.of(2026, 4, 25), // Sat
                  LocalDate.of(2026, 4, 26), // Sun
                  "Weekend",
                  memberIdOwner));

          BigDecimal effective = capacityService.getMemberEffectiveCapacity(memberWeekend, monday);
          // No weekday leave, full capacity
          assertThat(effective).isEqualByComparingTo(new BigDecimal("40.00"));
          return null;
        });
  }

  // ===== Controller-level tests (290.7) =====

  @Test
  @Order(20)
  void controller_createCapacityRecord_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/capacity", memberIdOwnerStr)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "weeklyHours": 35.00,
                      "effectiveFrom": "2026-05-04",
                      "effectiveTo": null,
                      "note": "Standard"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.weeklyHours").value(35.00))
        .andExpect(jsonPath("$.memberId").value(memberIdOwnerStr))
        .andExpect(jsonPath("$.effectiveFrom").value("2026-05-04"));
  }

  @Test
  @Order(21)
  void controller_listCapacityRecords_returnsRecords() throws Exception {
    mockMvc
        .perform(get("/api/members/{memberId}/capacity", memberIdOwnerStr).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @Order(22)
  void controller_updateCapacityRecord() throws Exception {
    // Create a record first
    var createResult =
        mockMvc
            .perform(
                post("/api/members/{memberId}/capacity", memberIdOwnerStr)
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "weeklyHours": 30.00,
                      "effectiveFrom": "2026-06-01",
                      "note": "To update"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String recordId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update
    mockMvc
        .perform(
            put("/api/members/{memberId}/capacity/{id}", memberIdOwnerStr, recordId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "weeklyHours": 28.00,
                      "effectiveTo": "2026-12-31",
                      "note": "Updated"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeklyHours").value(28.00))
        .andExpect(jsonPath("$.note").value("Updated"));
  }

  @Test
  @Order(23)
  void controller_deleteCapacityRecord() throws Exception {
    // Create a record first
    var createResult =
        mockMvc
            .perform(
                post("/api/members/{memberId}/capacity", memberIdOwnerStr)
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "weeklyHours": 20.00,
                      "effectiveFrom": "2026-07-06",
                      "note": "To delete"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String recordId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Delete
    mockMvc
        .perform(
            delete("/api/members/{memberId}/capacity/{id}", memberIdOwnerStr, recordId)
                .with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(24)
  void controller_memberRole_gets403() throws Exception {
    mockMvc
        .perform(get("/api/members/{memberId}/capacity", memberIdOwnerStr).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(25)
  void controller_effectiveFromNotMonday_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/capacity", memberIdOwnerStr)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "weeklyHours": 40.00,
                      "effectiveFrom": "2026-03-04",
                      "note": "Wednesday"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(26)
  void controller_weeklyHoursZero_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/members/{memberId}/capacity", memberIdOwnerStr)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "weeklyHours": 0,
                      "effectiveFrom": "2026-03-02",
                      "note": "Zero hours"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  // ===== OrgSettings extension tests (290.8) =====

  @Test
  @Order(30)
  void orgSettings_defaultCapacityIs40() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultWeeklyCapacityHours").value(40.00));
  }

  @Test
  @Order(31)
  void orgSettings_updateDefaultCapacityTo32() throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/capacity")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultWeeklyCapacityHours": 32.00
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultWeeklyCapacityHours").value(32.00));
  }

  @Test
  @Order(32)
  void orgSettings_readBackUpdatedCapacity() throws Exception {
    mockMvc
        .perform(get("/api/settings").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultWeeklyCapacityHours").value(32.00));
  }
}
