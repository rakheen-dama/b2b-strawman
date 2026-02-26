package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
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
class UnsubscribeControllerIntegrationTest {

  private static final String ORG_ID = "org_unsub_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private UnsubscribeService unsubscribeService;
  @Autowired private NotificationPreferenceRepository notificationPreferenceRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void provisionTenantAndMember() throws Exception {
    tenantSchema = provisioningService.provisionTenant(ORG_ID, "Unsub Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync a member via internal API
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
                          "clerkUserId": "user_unsub_owner",
                          "email": "unsub_owner@test.com",
                          "name": "Unsub Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    String memberIdStr = JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
    memberId = UUID.fromString(memberIdStr);
  }

  @Test
  void valid_token_sets_emailEnabled_false() throws Exception {
    String token = unsubscribeService.generateToken(memberId, "COMMENT_ADDED", tenantSchema);

    mockMvc.perform(get("/api/email/unsubscribe").param("token", token)).andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var pref =
                  notificationPreferenceRepository
                      .findByMemberIdAndNotificationType(memberId, "COMMENT_ADDED")
                      .orElseThrow();
              assertThat(pref.isEmailEnabled()).isFalse();
            });
  }

  @Test
  void returns_html_confirmation() throws Exception {
    String token = unsubscribeService.generateToken(memberId, "TASK_ASSIGNED", tenantSchema);

    var result =
        mockMvc
            .perform(get("/api/email/unsubscribe").param("token", token))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentType()).contains("text/html");
    assertThat(result.getResponse().getContentAsString()).contains("unsubscribed");
    assertThat(result.getResponse().getContentAsString()).contains("TASK_ASSIGNED");
  }

  @Test
  void invalid_token_returns_400() throws Exception {
    mockMvc
        .perform(get("/api/email/unsubscribe").param("token", "garbled-invalid-token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void idempotent() throws Exception {
    String token = unsubscribeService.generateToken(memberId, "PROJECT_UPDATED", tenantSchema);

    // First call
    mockMvc.perform(get("/api/email/unsubscribe").param("token", token)).andExpect(status().isOk());

    // Second call — still 200, preference remains false
    mockMvc.perform(get("/api/email/unsubscribe").param("token", token)).andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var pref =
                  notificationPreferenceRepository
                      .findByMemberIdAndNotificationType(memberId, "PROJECT_UPDATED")
                      .orElseThrow();
              assertThat(pref.isEmailEnabled()).isFalse();
            });
  }

  @Test
  void no_auth_required() throws Exception {
    String token = unsubscribeService.generateToken(memberId, "INVOICE_CREATED", tenantSchema);

    // Call without any JWT or API key — should still succeed
    mockMvc.perform(get("/api/email/unsubscribe").param("token", token)).andExpect(status().isOk());
  }
}
