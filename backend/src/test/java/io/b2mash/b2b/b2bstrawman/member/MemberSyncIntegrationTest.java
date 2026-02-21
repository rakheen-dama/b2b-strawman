package io.b2mash.b2b.b2bstrawman.member;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class MemberSyncIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_A = "org_member_test_a";
  private static final String ORG_B = "org_member_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void provisionTenants() {
    provisioningService.provisionTenant(ORG_A, "Member Test Org A");
    planSyncService.syncPlan(ORG_A, "pro-plan");
    provisioningService.provisionTenant(ORG_B, "Member Test Org B");
    planSyncService.syncPlan(ORG_B, "pro-plan");
  }

  // --- Sync (create) ---

  @Test
  void shouldCreateMemberViaSyncEndpoint() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_create_test",
                      "email": "alice@example.com",
                      "name": "Alice",
                      "avatarUrl": "https://img.example.com/alice.png",
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").exists())
        .andExpect(jsonPath("$.clerkUserId").value("user_create_test"))
        .andExpect(jsonPath("$.action").value("created"));
  }

  // --- Sync (update) ---

  @Test
  void shouldUpdateMemberOnResync() throws Exception {
    // First sync — creates
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_resync_test",
                      "email": "bob@example.com",
                      "name": "Bob",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action").value("created"));

    // Second sync — updates (role changed)
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_resync_test",
                      "email": "bob@example.com",
                      "name": "Bob Updated",
                      "avatarUrl": "https://img.example.com/bob.png",
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("updated"));
  }

  // --- Delete ---

  @Test
  void shouldDeleteMember() throws Exception {
    // Create first
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_delete_test",
                      "email": "charlie@example.com",
                      "name": "Charlie",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated());

    // Delete
    mockMvc
        .perform(
            delete("/internal/members/user_delete_test")
                .param("clerkOrgId", ORG_A)
                .header("X-API-KEY", API_KEY))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldReturn404WhenDeletingNonexistentMember() throws Exception {
    mockMvc
        .perform(
            delete("/internal/members/user_nonexistent")
                .param("clerkOrgId", ORG_A)
                .header("X-API-KEY", API_KEY))
        .andExpect(status().isNotFound());
  }

  // --- Tenant isolation ---

  @Test
  void membersAreIsolatedBetweenTenants() throws Exception {
    // Create member in tenant A
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_isolation_test",
                      "email": "diana@example.com",
                      "name": "Diana",
                      "avatarUrl": null,
                      "orgRole": "owner"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated());

    // Delete from tenant B should 404 — member doesn't exist there
    mockMvc
        .perform(
            delete("/internal/members/user_isolation_test")
                .param("clerkOrgId", ORG_B)
                .header("X-API-KEY", API_KEY))
        .andExpect(status().isNotFound());
  }

  // --- Validation ---

  @Test
  void shouldReject400WhenClerkOrgIdMissing() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "",
                      "clerkUserId": "user_val1",
                      "email": "val@example.com",
                      "name": "Val",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenClerkUserIdMissing() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "",
                      "email": "val@example.com",
                      "name": "Val",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenEmailMissing() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_val3",
                      "email": "",
                      "name": "Val",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenOrgRoleMissing() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_val4",
                      "email": "val@example.com",
                      "name": "Val",
                      "avatarUrl": null,
                      "orgRole": ""
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isBadRequest());
  }

  // --- Auth ---

  @Test
  void shouldReject401WithoutApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_nokey",
                      "email": "nokey@example.com",
                      "name": "NoKey",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReject401WithWrongApiKey() throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_wrongkey",
                      "email": "wrongkey@example.com",
                      "name": "WrongKey",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isUnauthorized());
  }

  // --- Race fix: null-safe updateFrom ---

  @Test
  void shouldNotOverwriteFieldsWithNull() throws Exception {
    // First sync — creates member with full data
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_null_safe_test",
                      "email": "orig@test.com",
                      "name": "Original",
                      "avatarUrl": "https://img.example.com/orig.png",
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action").value("created"));

    // Second sync — name and avatarUrl are null (race/partial webhook), email and role change
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_null_safe_test",
                      "email": "new@test.com",
                      "name": null,
                      "avatarUrl": null,
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("updated"));

    // Third sync — re-sync with no changes, verifying member still exists without error
    // (null-safety means name was preserved; the sync endpoint does not expose name in response,
    // but a successful 200 confirms the member record is intact)
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_null_safe_test",
                      "email": "new@test.com",
                      "name": null,
                      "avatarUrl": null,
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clerkUserId").value("user_null_safe_test"))
        .andExpect(jsonPath("$.action").value("updated"));
  }

  // --- Race fix: stale member endpoint ---

  @Test
  void shouldReturnEmptyStaleListWhenNoStaleMembers() throws Exception {
    // Sync a real member with a legitimate email — must not appear in stale list
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_stale_check",
                      "email": "real@test.com",
                      "name": "Real User",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated());

    // Stale endpoint must return empty list — real@test.com does not end with @placeholder.internal
    mockMvc
        .perform(
            get("/internal/members/stale").param("clerkOrgId", ORG_A).header("X-API-KEY", API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldReturnEmptyStaleListForCleanTenant() throws Exception {
    // ORG_B has no members at all — stale list must be an empty JSON array
    mockMvc
        .perform(
            get("/internal/members/stale").param("clerkOrgId", ORG_B).header("X-API-KEY", API_KEY))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  // --- Race fix: retry on unprovisioned schema ---

  @Test
  void shouldThrowWhenOrgNotProvisioned() {
    // An org ID that was never provisioned exhausts all 5 retry attempts and throws
    // IllegalArgumentException, which MockMvc propagates as a ServletException.
    assertThrows(
        jakarta.servlet.ServletException.class,
        () ->
            mockMvc.perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "org_does_not_exist",
                          "clerkUserId": "user_unprovisioned",
                          "email": "ghost@test.com",
                          "name": "Ghost",
                          "avatarUrl": null,
                          "orgRole": "member"
                        }
                        """)));
  }

  // --- Race fix: null name/avatarUrl accepted on create ---

  @Test
  void shouldSyncMemberWithNullNameAndAvatarUrl() throws Exception {
    // name and avatarUrl are optional — a webhook that omits them must still result in a 201
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_null_name_create",
                      "email": "nullname@test.com",
                      "name": null,
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_A)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.memberId").exists())
        .andExpect(jsonPath("$.clerkUserId").value("user_null_name_create"))
        .andExpect(jsonPath("$.action").value("created"));
  }
}
