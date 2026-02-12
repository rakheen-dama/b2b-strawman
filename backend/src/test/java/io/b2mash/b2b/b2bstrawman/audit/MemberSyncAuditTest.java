package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests verifying audit events produced by {@code MemberSyncService} operations. These
 * use internal endpoints with X-API-KEY authentication. Audit events should have actorType=WEBHOOK
 * and source=WEBHOOK.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberSyncAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ms_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MemberSync Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "MemberSync Audit Test Org").schemaName();
  }

  @Test
  void syncNewMemberProducesAuditEvent() throws Exception {
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
                          "clerkUserId": "user_ms_new",
                          "email": "ms_new@test.com",
                          "name": "New Member",
                          "avatarUrl": null,
                          "orgRole": "member"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    var memberId =
        UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.memberId"));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("member", memberId, null, "member.synced", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("member.synced");
              assertThat(event.getEntityType()).isEqualTo("member");
              assertThat(event.getEntityId()).isEqualTo(memberId);
              assertThat(event.getActorType()).isEqualTo("WEBHOOK");
              assertThat(event.getSource()).isEqualTo("WEBHOOK");
              assertThat(event.getDetails()).containsEntry("action", "added");
              assertThat(event.getDetails()).containsEntry("email", "ms_new@test.com");
            });
  }

  @Test
  void syncExistingMemberWithRoleChangeProducesAuditEvent() throws Exception {
    // Create a member first
    var createResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_ms_role",
                          "email": "ms_role@test.com",
                          "name": "Role Change Member",
                          "avatarUrl": null,
                          "orgRole": "member"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    var memberId =
        UUID.fromString(
            JsonPath.read(createResult.getResponse().getContentAsString(), "$.memberId"));

    // Update with a role change
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_ms_role",
                      "email": "ms_role@test.com",
                      "name": "Role Change Member",
                      "avatarUrl": null,
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "member", memberId, null, "member.role_changed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("member.role_changed");
              assertThat(event.getActorType()).isEqualTo("WEBHOOK");
              assertThat(event.getSource()).isEqualTo("WEBHOOK");

              @SuppressWarnings("unchecked")
              var roleChange = (Map<String, Object>) event.getDetails().get("org_role");
              assertThat(roleChange).containsEntry("from", "member");
              assertThat(roleChange).containsEntry("to", "admin");
            });
  }

  @Test
  void syncExistingMemberWithNoRoleChangeProducesNoRoleChangedEvent() throws Exception {
    // Create a member first
    var createResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_ms_norole",
                          "email": "ms_norole@test.com",
                          "name": "No Role Change",
                          "avatarUrl": null,
                          "orgRole": "member"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    var memberId =
        UUID.fromString(
            JsonPath.read(createResult.getResponse().getContentAsString(), "$.memberId"));

    // Update with same role (only name change)
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_ms_norole",
                      "email": "ms_norole@test.com",
                      "name": "Updated Name",
                      "avatarUrl": null,
                      "orgRole": "member"
                    }
                    """
                        .formatted(ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "member", memberId, null, "member.role_changed", null, null),
                      PageRequest.of(0, 10));

              // No role_changed event should be emitted
              assertThat(page.getTotalElements()).isZero();
            });
  }

  @Test
  void deleteMemberProducesAuditEvent() throws Exception {
    // Create a member first
    var createResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_ms_delete",
                          "email": "ms_delete@test.com",
                          "name": "Delete Me",
                          "avatarUrl": null,
                          "orgRole": "member"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    var memberId =
        UUID.fromString(
            JsonPath.read(createResult.getResponse().getContentAsString(), "$.memberId"));

    // Delete the member
    mockMvc
        .perform(
            delete("/internal/members/user_ms_delete")
                .header("X-API-KEY", API_KEY)
                .param("clerkOrgId", ORG_ID))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("member", memberId, null, "member.removed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("member.removed");
              assertThat(event.getEntityType()).isEqualTo("member");
              assertThat(event.getEntityId()).isEqualTo(memberId);
              assertThat(event.getActorType()).isEqualTo("WEBHOOK");
              assertThat(event.getSource()).isEqualTo("WEBHOOK");
              assertThat(event.getDetails()).containsEntry("clerk_user_id", "user_ms_delete");
            });
  }
}
