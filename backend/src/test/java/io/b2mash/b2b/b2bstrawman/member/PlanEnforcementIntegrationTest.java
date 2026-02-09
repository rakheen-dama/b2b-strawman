package io.b2mash.b2b.b2bstrawman.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
class PlanEnforcementIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String STARTER_ORG = "org_plan_enforce_starter";
  private static final String PRO_ORG = "org_plan_enforce_pro";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void provisionTenants() {
    provisioningService.provisionTenant(STARTER_ORG, "Starter Enforcement Org");
    provisioningService.provisionTenant(PRO_ORG, "Pro Enforcement Org");
    planSyncService.syncPlan(PRO_ORG, "pro-plan");
  }

  // --- Starter tier tests (ordered) ---

  @Test
  @Order(1)
  void starterOrg_syncTwoMembers_succeeds() throws Exception {
    syncMember(STARTER_ORG, "user_starter_1", "starter1@example.com", "Starter One", "member")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action").value("created"));

    syncMember(STARTER_ORG, "user_starter_2", "starter2@example.com", "Starter Two", "admin")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action").value("created"));
  }

  @Test
  @Order(2)
  void starterOrg_thirdMember_rejected() throws Exception {
    // Re-sync to ensure 2 members exist (idempotent)
    syncMember(STARTER_ORG, "user_starter_1", "starter1@example.com", "Starter One", "member");
    syncMember(STARTER_ORG, "user_starter_2", "starter2@example.com", "Starter Two", "admin");

    syncMember(STARTER_ORG, "user_starter_3", "starter3@example.com", "Starter Three", "member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Plan limit exceeded"))
        .andExpect(
            jsonPath("$.detail").value("Member limit reached (2/2). Upgrade to add more members."));
  }

  @Test
  @Order(3)
  void starterOrg_updateExistingMemberAtLimit_succeeds() throws Exception {
    syncMember(
            STARTER_ORG, "user_starter_1", "starter1@example.com", "Starter One Updated", "admin")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("updated"));
  }

  @Test
  @Order(4)
  void planLimitError_includesUpgradeUrl() throws Exception {
    syncMember(STARTER_ORG, "user_starter_blocked", "blocked@example.com", "Blocked", "member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.upgradeUrl").value("/settings/billing"));
  }

  // --- Pro tier tests (ordered) ---

  @Test
  @Order(10)
  void proOrg_syncTenMembers_succeeds() throws Exception {
    for (int i = 1; i <= 10; i++) {
      syncMember(
              PRO_ORG,
              "user_pro_" + i,
              "pro" + i + "@example.com",
              "Pro Member " + i,
              i == 1 ? "owner" : "member")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.action").value("created"));
    }
  }

  @Test
  @Order(11)
  void proOrg_eleventhMember_rejected() throws Exception {
    syncMember(PRO_ORG, "user_pro_11", "pro11@example.com", "Pro Member 11", "member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Plan limit exceeded"))
        .andExpect(
            jsonPath("$.detail")
                .value("Member limit reached (10/10). Upgrade to add more members."));
  }

  private org.springframework.test.web.servlet.ResultActions syncMember(
      String orgId, String userId, String email, String name, String role) throws Exception {
    return mockMvc.perform(
        post("/internal/members/sync")
            .header("X-API-KEY", API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
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
                    .formatted(orgId, userId, email, name, role)));
  }
}
