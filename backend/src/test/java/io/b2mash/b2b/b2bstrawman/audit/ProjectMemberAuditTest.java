package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests verifying audit events produced by {@code ProjectMemberService} operations:
 * addMember, removeMember, and transferLead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectMemberAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_pm_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;
  private String ownerMemberId;
  private String memberMemberId;
  private String member2MemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "PM Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName = provisioningService.provisionTenant(ORG_ID, "PM Audit Test Org").schemaName();

    ownerMemberId =
        syncMember(ORG_ID, "user_pma_owner", "pma_owner@test.com", "PMA Owner", "owner");
    memberMemberId =
        syncMember(ORG_ID, "user_pma_member", "pma_member@test.com", "PMA Member", "member");
    member2MemberId =
        syncMember(ORG_ID, "user_pma_member2", "pma_member2@test.com", "PMA Member2", "member");
  }

  @Test
  void addMemberProducesAuditEvent() throws Exception {
    // Create a project (owner becomes lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Add Member Audit", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    // Add a member to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberMemberId)))
        .andExpect(status().isCreated());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project_member", null, null, "project_member.added", null, null),
                      PageRequest.of(0, 100));

              // Find the event for this specific project
              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e ->
                              e.getDetails() != null
                                  && projectId.equals(e.getDetails().get("project_id")))
                      .toList();

              assertThat(matchingEvents).hasSize(1);
              var event = matchingEvents.getFirst();
              assertThat(event.getEventType()).isEqualTo("project_member.added");
              assertThat(event.getEntityType()).isEqualTo("project_member");
              assertThat(event.getDetails()).containsEntry("project_id", projectId);
              assertThat(event.getDetails()).containsEntry("member_id", memberMemberId);
              assertThat(event.getDetails()).containsEntry("role", "member");
            });
  }

  @Test
  void removeMemberProducesAuditEvent() throws Exception {
    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Remove Member Audit", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    // Add then remove a member
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberMemberId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/projects/" + projectId + "/members/" + memberMemberId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project_member", null, null, "project_member.removed", null, null),
                      PageRequest.of(0, 100));

              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e ->
                              e.getDetails() != null
                                  && projectId.equals(e.getDetails().get("project_id"))
                                  && memberMemberId.equals(e.getDetails().get("member_id")))
                      .toList();

              assertThat(matchingEvents).hasSize(1);
              var event = matchingEvents.getFirst();
              assertThat(event.getEventType()).isEqualTo("project_member.removed");
              assertThat(event.getEntityType()).isEqualTo("project_member");
            });
  }

  @Test
  void transferLeadProducesTwoRoleChangedEvents() throws Exception {
    // Create a project (owner becomes lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Transfer Lead Audit", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    // Add member2 to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(member2MemberId)))
        .andExpect(status().isCreated());

    // Transfer lead from owner to member2
    // The owner is currently the lead (project creator)
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/members/" + member2MemberId + "/role")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"role": "lead"}
                    """))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project_member", null, null, "project_member.role_changed", null, null),
                      PageRequest.of(0, 100));

              // Filter to this specific project
              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e ->
                              e.getDetails() != null
                                  && projectId.equals(e.getDetails().get("project_id")))
                      .toList();

              assertThat(matchingEvents).hasSize(2);

              // One event for the old lead (demoted: lead -> member)
              var demotedEvent =
                  matchingEvents.stream()
                      .filter(e -> ownerMemberId.equals(e.getDetails().get("member_id")))
                      .findFirst();
              assertThat(demotedEvent).isPresent();
              @SuppressWarnings("unchecked")
              var demotedRole = (Map<String, Object>) demotedEvent.get().getDetails().get("role");
              assertThat(demotedRole).containsEntry("from", "lead");
              assertThat(demotedRole).containsEntry("to", "member");

              // One event for the new lead (promoted: member -> lead)
              var promotedEvent =
                  matchingEvents.stream()
                      .filter(e -> member2MemberId.equals(e.getDetails().get("member_id")))
                      .findFirst();
              assertThat(promotedEvent).isPresent();
              @SuppressWarnings("unchecked")
              var promotedRole = (Map<String, Object>) promotedEvent.get().getDetails().get("role");
              assertThat(promotedRole).containsEntry("from", "member");
              assertThat(promotedRole).containsEntry("to", "lead");
            });
  }

  @Test
  void addMemberAuditEventHasCorrectActorInfo() throws Exception {
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Actor Info Audit", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = extractIdFromLocation(projectResult);

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(member2MemberId)))
        .andExpect(status().isCreated());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project_member", null, null, "project_member.added", null, null),
                      PageRequest.of(0, 100));

              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e ->
                              e.getDetails() != null
                                  && projectId.equals(e.getDetails().get("project_id"))
                                  && member2MemberId.equals(e.getDetails().get("member_id")))
                      .toList();

              assertThat(matchingEvents).hasSize(1);
              var event = matchingEvents.getFirst();
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
              assertThat(event.getActorId()).isNotNull();
            });
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

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
        .jwt(j -> j.subject("user_pma_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
