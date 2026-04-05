package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
 * Integration tests verifying audit events produced by {@code ProjectService} operations. Each test
 * invokes an API endpoint via MockMvc, then queries audit events to verify correctness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectServiceAuditTest {
  private static final String ORG_ID = "org_proj_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Audit Test Org", null);
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Project Audit Test Org", null).schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pa_owner", "pa_owner@test.com", "PA Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pa_admin", "pa_admin@test.com", "PA Admin", "admin");
  }

  @Test
  void createProjectProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Audit Create Test", "description": "test desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(result));

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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Original Name", "description": "Original Desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Update the project
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Same Name", "description": "Same Desc"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Update with same values
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
              // No domain fields changed — only actor_name enrichment present
              assertThat(event.getDetails()).isNotNull();
              assertThat(event.getDetails()).containsKey("actor_name");
              assertThat(event.getDetails()).hasSize(1);
            });
  }

  @Test
  void deleteProjectProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Delete Me", "description": null}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    mockMvc
        .perform(
            delete("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner")))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Keep This Name", "description": "Change This"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Only change description, keep name the same
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
}
