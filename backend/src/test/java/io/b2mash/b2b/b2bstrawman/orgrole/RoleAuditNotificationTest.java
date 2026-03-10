package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAuditNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_role_audit_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID regularMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Role Audit Notif Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ownerMemberId =
        UUID.fromString(syncMember("user_ran_owner", "ran_owner@test.com", "RAN Owner", "owner"));
    regularMemberId =
        UUID.fromString(
            syncMember("user_ran_member", "ran_member@test.com", "RAN Member", "member"));
  }

  // --- Test 1: createRole logs audit event ---

  @Test
  void createRole_logsAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Create",
                          "description": "test",
                          "capabilities": ["INVOICING", "CUSTOMER_MANAGEMENT"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("org_role", roleId, null, "role.created", null, null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("role.created");
              assertThat(event.getEntityType()).isEqualTo("org_role");
              assertThat(event.getEntityId()).isEqualTo(roleId);
              assertThat(event.getDetails()).containsEntry("name", "Audit Test Role Create");
              assertThat(event.getDetails()).containsKey("slug");
              assertThat(event.getDetails()).containsKey("capabilities");
            });
  }

  // --- Test 2: updateRole logs audit event with capability diff ---

  @Test
  void updateRole_logsAuditEvent_withChangedCaps() throws Exception {
    // Create role with initial capabilities
    var createResult =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Update",
                          "description": "test",
                          "capabilities": ["INVOICING"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    // Update capabilities: remove INVOICING, add PROJECT_MANAGEMENT
    mockMvc
        .perform(
            put("/api/org-roles/" + roleId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "capabilities": ["PROJECT_MANAGEMENT"]
                    }
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("org_role", roleId, null, "role.updated", null, null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("role.updated");
              assertThat(event.getDetails()).containsKey("addedCapabilities");
              assertThat(event.getDetails()).containsKey("removedCapabilities");

              @SuppressWarnings("unchecked")
              var added = (List<String>) event.getDetails().get("addedCapabilities");
              @SuppressWarnings("unchecked")
              var removed = (List<String>) event.getDetails().get("removedCapabilities");
              assertThat(added).contains("PROJECT_MANAGEMENT");
              assertThat(removed).contains("INVOICING");
            });
  }

  // --- Test 3: deleteRole logs audit event ---

  @Test
  void deleteRole_logsAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Delete",
                          "description": "to be deleted",
                          "capabilities": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(delete("/api/org-roles/" + roleId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("org_role", roleId, null, "role.deleted", null, null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("role.deleted");
              assertThat(event.getDetails()).containsEntry("name", "Audit Test Role Delete");
              assertThat(event.getDetails()).containsKey("slug");
            });
  }

  // --- Test 4: assignRole logs member.role_changed audit event ---

  @Test
  void assignRole_logsAuditEvent() throws Exception {
    var roleResult =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Assign",
                          "description": "for assign test",
                          "capabilities": ["PROJECT_MANAGEMENT"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId =
        UUID.fromString(JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id"));

    Instant before = Instant.now();

    mockMvc
        .perform(
            put("/api/members/" + regularMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orgRoleId": "%s",
                      "capabilityOverrides": []
                    }
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "member", regularMemberId, null, "member.role_changed", before, null),
                      PageRequest.of(0, 10));
              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("member.role_changed");
              assertThat(event.getEntityType()).isEqualTo("member");
              assertThat(event.getEntityId()).isEqualTo(regularMemberId);
              assertThat(event.getDetails()).containsEntry("newRole", "Audit Test Role Assign");
              assertThat(event.getDetails()).containsEntry("memberId", regularMemberId.toString());
            });
  }

  // --- Test 5: assignRole notifies the target member ---

  @Test
  void assignRole_notifiesMember() throws Exception {
    var roleResult =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Notify Assign",
                          "description": "for notify test",
                          "capabilities": ["CUSTOMER_MANAGEMENT"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId =
        UUID.fromString(JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id"));
    Instant before = Instant.now();

    mockMvc
        .perform(
            put("/api/members/" + regularMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orgRoleId": "%s",
                      "capabilityOverrides": []
                    }
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              boolean notified =
                  notificationRepository.existsByTypeAndRecipientMemberIdAndCreatedAtAfter(
                      "ROLE_PERMISSIONS_CHANGED", regularMemberId, before);
              assertThat(notified)
                  .as(
                      "Member should receive ROLE_PERMISSIONS_CHANGED notification after role assignment")
                  .isTrue();
            });
  }

  // --- Test 6: updateRole with cap change notifies affected members ---

  @Test
  void updateRole_withCapChange_notifiesAffectedMembers() throws Exception {
    // Create role
    var roleResult =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Audit Test Role Cap Notify",
                          "description": "notify on cap change",
                          "capabilities": ["FINANCIAL_VISIBILITY"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    UUID roleId =
        UUID.fromString(JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id"));

    // Assign role to regularMember
    mockMvc
        .perform(
            put("/api/members/" + regularMemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "orgRoleId": "%s",
                      "capabilityOverrides": []
                    }
                    """
                        .formatted(roleId)))
        .andExpect(status().isOk());

    Instant before = Instant.now();

    // Update capabilities
    mockMvc
        .perform(
            put("/api/org-roles/" + roleId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "capabilities": ["AUTOMATIONS"]
                    }
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              boolean notified =
                  notificationRepository.existsByTypeAndRecipientMemberIdAndCreatedAtAfter(
                      "ROLE_PERMISSIONS_CHANGED", regularMemberId, before);
              assertThat(notified)
                  .as(
                      "Member should receive ROLE_PERMISSIONS_CHANGED notification after role capability update")
                  .isTrue();
            });
  }

  // --- Helpers ---

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ran_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
