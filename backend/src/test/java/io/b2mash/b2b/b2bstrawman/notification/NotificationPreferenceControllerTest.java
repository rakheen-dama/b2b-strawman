package io.b2mash.b2b.b2bstrawman.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class NotificationPreferenceControllerTest {
  private static final String ORG_ID = "org_notif_pref_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String tenantSchema;
  private String memberIdOwner;
  private String memberIdMember;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Notif Pref Controller Test Org", null)
            .schemaName();

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_npc_owner", "npc_owner@test.com", "NPC Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_npc_member", "npc_member@test.com", "NPC Member", "member");
  }

  // --- GET defaults ---

  @Test
  @Order(1)
  void getPreferencesReturnsAllTypesWithDefaults() throws Exception {
    mockMvc
        .perform(
            get("/api/notifications/preferences")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences", hasSize(54)))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
        .andExpect(jsonPath("$.preferences", hasSize(54)))
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(false))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false));

    // Verify via GET
    mockMvc
        .perform(
            get("/api/notifications/preferences")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
        .perform(
            get("/api/notifications/preferences")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner"))
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
        .perform(
            get("/api/notifications/preferences")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_npc_owner")))
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
        .perform(
            get("/api/notifications/preferences")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_npc_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferences", hasSize(54)))
        .andExpect(jsonPath("$.preferences[0].notificationType").value("TASK_ASSIGNED"))
        .andExpect(jsonPath("$.preferences[0].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[0].emailEnabled").value(false))
        .andExpect(jsonPath("$.preferences[4].notificationType").value("COMMENT_ADDED"))
        .andExpect(jsonPath("$.preferences[4].inAppEnabled").value(true))
        .andExpect(jsonPath("$.preferences[4].emailEnabled").value(false));
  }
}
