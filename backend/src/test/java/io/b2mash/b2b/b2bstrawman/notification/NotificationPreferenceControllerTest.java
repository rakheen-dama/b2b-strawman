package io.b2mash.b2b.b2bstrawman.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
class NotificationPreferenceControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_notif_pref_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Notif Pref Controller Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_npc_owner", "npc_owner@test.com", "NPC Owner", "owner");
    memberIdMember =
        syncMember(ORG_ID, "user_npc_member", "npc_member@test.com", "NPC Member", "member");
  }

  // --- GET defaults ---

  @Test
  @Order(1)
  void getPreferencesReturnsAllTypesWithDefaults() throws Exception {
    mockMvc
        .perform(get("/api/notifications/preferences").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences", hasSize(32)))
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false))
        .andExpect(jsonPath("$.preferences[1].notificationType").value("TASK_CLAIMED"))
        .andExpect(jsonPath("$.preferences[2].notificationType").value("TASK_CANCELLED"))
        .andExpect(jsonPath("$.preferences[3].notificationType").value("TASK_UPDATED"))
        .andExpect(jsonPath("$.preferences[4].notificationType").value("COMMENT_ADDED"))
        .andExpect(jsonPath("$.preferences[5].notificationType").value("DOCUMENT_SHARED"))
        .andExpect(jsonPath("$.preferences[6].notificationType").value("MEMBER_INVITED"));
  }

  // --- PUT single type and verify ---

  @Test
  @Order(2)
  void putUpdatesSingleTypeAndGetReflectsIt() throws Exception {
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "TASK_ASSIGNED", "inAppEnabled": false, "emailEnabled": false }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences", hasSize(32)))
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(false))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false));

    // Verify via GET
    mockMvc
        .perform(get("/api/notifications/preferences").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(false))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false));
  }

  // --- PUT multiple types ---

  @Test
  @Order(3)
  void putUpdatesMultipleTypes() throws Exception {
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "COMMENT_ADDED", "inAppEnabled": false, "emailEnabled": true },
                        { "notificationType": "DOCUMENT_SHARED", "inAppEnabled": false, "emailEnabled": true }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[4].notificationType").value("COMMENT_ADDED"))
        .andExpect(jsonPath("$.preferences[4].inAppEnabled").value(false))
        .andExpect(jsonPath("$.preferences[4].emailEnabled").value(true))
        .andExpect(jsonPath("$.preferences[5].notificationType").value("DOCUMENT_SHARED"))
        .andExpect(jsonPath("$.preferences[5].inAppEnabled").value(false))
        .andExpect(jsonPath("$.preferences[5].emailEnabled").value(true));
  }

  // --- Defaults preserved for types not in PUT ---

  @Test
  @Order(4)
  void defaultsPreservedForTypesNotInPutRequest() throws Exception {
    // TASK_CLAIMED was never updated — should still be default
    mockMvc
        .perform(get("/api/notifications/preferences").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[1].notificationType").value("TASK_CLAIMED"))
        .andExpect(jsonPath("$.preferences[1].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[1].emailEnabled").value(false));
  }

  // --- Updating back to default values still persists ---

  @Test
  @Order(5)
  void updatingBackToDefaultValuesStillPersistsRow() throws Exception {
    // Set TASK_ASSIGNED back to defaults
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "TASK_ASSIGNED", "inAppEnabled": true, "emailEnabled": false }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false));
  }

  // --- emailEnabled can be toggled independently ---

  @Test
  @Order(6)
  void emailEnabledToggledIndependentlyOfInAppEnabled() throws Exception {
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "MEMBER_INVITED", "inAppEnabled": true, "emailEnabled": true }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[6].notificationType").value("MEMBER_INVITED"))
        .andExpect(jsonPath("$.preferences[6].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[6].emailEnabled").value(true));
  }

  // --- Idempotent update (second PUT overwrites first) ---

  @Test
  @Order(7)
  void updatingSameTypeTwiceIsIdempotent() throws Exception {
    // First update
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "TASK_UPDATED", "inAppEnabled": false, "emailEnabled": true }
                      ]
                    }
                    """))
        .andExpect(status().isOk());

    // Second update (overwrite)
    mockMvc
        .perform(
            put("/api/notifications/preferences")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferences": [
                        { "notificationType": "TASK_UPDATED", "inAppEnabled": true, "emailEnabled": false }
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[3].notificationType").value("TASK_UPDATED"))
        .andExpect(jsonPath("$.preferences[3].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[3].emailEnabled").value(false));

    // Verify via GET
    mockMvc
        .perform(get("/api/notifications/preferences").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences[3].notificationType").value("TASK_UPDATED"))
        .andExpect(jsonPath("$.preferences[3].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[3].emailEnabled").value(false));
  }

  // --- Self-scoping isolation: member sees own defaults, not owner's changes ---

  @Test
  @Order(8)
  void memberSeesOwnPreferencesNotOwnerChanges() throws Exception {
    // Member should see all defaults (owner's changes should not affect member)
    mockMvc
        .perform(get("/api/notifications/preferences").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences", hasSize(32)))
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false))
        .andExpect(jsonPath("$.preferences[4].notificationType").value("COMMENT_ADDED"))
        .andExpect(jsonPath("$.preferences[4].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[4].emailEnabled").value(false));
  }

  // --- Helpers ---

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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_npc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_npc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
