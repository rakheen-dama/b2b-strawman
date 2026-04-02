package io.b2mash.b2b.b2bstrawman.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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

/**
 * Plan enforcement integration tests. After Epic 419A, the Tier model is removed and a flat
 * 10-member limit applies to all orgs. This test verifies the 10-member limit and the enforcement
 * error shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanEnforcementIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG = "org_plan_enforce_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void provisionTenants() {
    provisioningService.provisionTenant(ORG, "Plan Enforcement Test Org", null);
  }

  // --- Member limit tests (ordered) ---

  @Test
  @Order(1)
  void syncTenMembers_succeeds() throws Exception {
    for (int i = 1; i <= 10; i++) {
      syncMember(
              ORG,
              "user_enforce_" + i,
              "enforce" + i + "@example.com",
              "Enforce Member " + i,
              i == 1 ? "owner" : "member")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.action").value("created"));
    }
  }

  @Test
  @Order(2)
  void eleventhMember_rejected() throws Exception {
    syncMember(ORG, "user_enforce_11", "enforce11@example.com", "Enforce Member 11", "member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Plan limit exceeded"))
        .andExpect(
            jsonPath("$.detail")
                .value("Member limit reached (10/10). Upgrade to add more members."));
  }

  @Test
  @Order(3)
  void updateExistingMemberAtLimit_succeeds() throws Exception {
    syncMember(ORG, "user_enforce_1", "enforce1@example.com", "Enforce One Updated", "admin")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("updated"));
  }

  @Test
  @Order(4)
  void planLimitError_includesUpgradeUrl() throws Exception {
    syncMember(ORG, "user_enforce_blocked", "blocked@example.com", "Blocked", "member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.upgradeUrl").value("/settings/billing"));
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
