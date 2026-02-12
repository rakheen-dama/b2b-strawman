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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests verifying audit events produced by {@code ProjectService} operations. Each test
 * invokes an API endpoint via MockMvc, then queries audit events to verify correctness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectServiceAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proj_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName = provisioningService.provisionTenant(ORG_ID, "Project Audit Test Org").schemaName();

    syncMember(ORG_ID, "user_pa_owner", "pa_owner@test.com", "PA Owner", "owner");
    syncMember(ORG_ID, "user_pa_admin", "pa_admin@test.com", "PA Admin", "admin");
  }

  @Test
  void createProjectProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Create Test", "description": "test desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(result));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project", projectId, null, "project.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("project.created");
              assertThat(event.getEntityType()).isEqualTo("project");
              assertThat(event.getEntityId()).isEqualTo(projectId);
              assertThat(event.getDetails()).containsEntry("name", "Audit Create Test");
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void updateProjectCapturesFieldDeltas() throws Exception {
    // Create a project
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Original Name", "description": "Original Desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(createResult));

    // Update the project
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Updated Name", "description": "Updated Desc"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project", projectId, null, "project.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("project.updated");
              assertThat(event.getEntityId()).isEqualTo(projectId);

              @SuppressWarnings("unchecked")
              var nameChange = (Map<String, Object>) event.getDetails().get("name");
              assertThat(nameChange).containsEntry("from", "Original Name");
              assertThat(nameChange).containsEntry("to", "Updated Name");

              @SuppressWarnings("unchecked")
              var descChange = (Map<String, Object>) event.getDetails().get("description");
              assertThat(descChange).containsEntry("from", "Original Desc");
              assertThat(descChange).containsEntry("to", "Updated Desc");
            });
  }

  @Test
  void updateProjectWithNoChangesProducesEventWithNullDetails() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Same Name", "description": "Same Desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(createResult));

    // Update with same values
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Same Name", "description": "Same Desc"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project", projectId, null, "project.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // No fields changed, so details should be null
              assertThat(event.getDetails()).isNull();
            });
  }

  @Test
  void deleteProjectProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Delete Me", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(createResult));

    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project", projectId, null, "project.deleted", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("project.deleted");
              assertThat(event.getEntityId()).isEqualTo(projectId);
              assertThat(event.getDetails()).containsEntry("name", "Delete Me");
            });
  }

  @Test
  void updateProjectWithPartialChangeOnlyCapturesChangedField() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Keep This Name", "description": "Change This"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(createResult));

    // Only change description, keep name the same
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Keep This Name", "description": "New Description"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "project", projectId, null, "project.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // Only description changed
              assertThat(event.getDetails()).doesNotContainKey("name");
              assertThat(event.getDetails()).containsKey("description");
            });
  }

  // --- Rollback semantics tests (Task 51.5) ---

  @Test
  void failedCreateProducesNoAuditEvent() throws Exception {
    // Attempt create with blank name -- should fail validation (400)
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "", "description": "should fail"}
                    """))
        .andExpect(status().isBadRequest());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // Query for any project.created events with "should fail" -- there should be none
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("project", null, null, "project.created", null, null),
                      PageRequest.of(0, 100));

              // None of the events should have details containing "should fail"
              assertThat(page.getContent())
                  .noneMatch(
                      e ->
                          e.getDetails() != null
                              && "should fail".equals(e.getDetails().get("name")));
            });
  }

  @Test
  void failedUpdateOfNonexistentProjectProducesNoAuditEvent() throws Exception {
    var fakeId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/projects/" + fakeId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Ghost Project", "description": "nope"}
                    """))
        .andExpect(status().isNotFound());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("project", fakeId, null, "project.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isZero();
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
        .jwt(j -> j.subject("user_pa_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
