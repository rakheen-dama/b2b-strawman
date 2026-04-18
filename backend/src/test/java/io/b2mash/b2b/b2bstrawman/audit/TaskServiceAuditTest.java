package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
 * Integration tests verifying audit events produced by {@code TaskService} operations. Each test
 * invokes an API endpoint via MockMvc, then queries audit events to verify correctness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskServiceAuditTest {
  private static final String ORG_ID = "org_task_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;
  private UUID projectId;
  private String ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Audit Test Org", null);
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Task Audit Test Org", null).schemaName();

    ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ta_owner", "ta_owner@test.com", "TA Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ta_member", "ta_member@test.com", "TA Member", "member");

    // Create a project for task tests
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Task Audit Project", "description": "for task tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(result));
  }

  @Test
  void createTaskProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Audit Task", "priority": "HIGH"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(result));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.created");
              assertThat(event.getEntityType()).isEqualTo("task");
              assertThat(event.getEntityId()).isEqualTo(taskId);
              assertThat(event.getDetails()).containsEntry("title", "Audit Task");
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void updateTaskCapturesFieldDeltas() throws Exception {
    // Create a task
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Original Title", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Update the task
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Updated Title", "description": "new desc", "priority": "HIGH", "status": "OPEN"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.updated");

              @SuppressWarnings("unchecked")
              var titleChange = (Map<String, Object>) event.getDetails().get("title");
              assertThat(titleChange).containsEntry("from", "Original Title");
              assertThat(titleChange).containsEntry("to", "Updated Title");

              @SuppressWarnings("unchecked")
              var priorityChange = (Map<String, Object>) event.getDetails().get("priority");
              assertThat(priorityChange).containsEntry("from", "LOW");
              assertThat(priorityChange).containsEntry("to", "HIGH");
            });
  }

  @Test
  void updateTaskWithNoChangesProducesEventWithNullDetails() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Same Title", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Update with same values
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Same Title", "priority": "MEDIUM", "status": "OPEN"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // Details now always includes project_id and current title (GAP-L-05)
              // so the activity feed can render the task identity on no-op / non-title updates.
              assertThat(event.getDetails()).isNotNull();
              assertThat(event.getDetails()).containsKey("project_id");
              assertThat(event.getDetails()).containsKey("actor_name");
              assertThat(event.getDetails()).containsEntry("title", "Same Title");
              assertThat(event.getDetails()).hasSize(3);
            });
  }

  @Test
  void deleteTaskProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Delete Me Task", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    mockMvc
        .perform(
            delete("/api/tasks/" + taskId).with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner")))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.deleted", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.deleted");
              assertThat(event.getDetails()).containsEntry("title", "Delete Me Task");
              assertThat(event.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  @Test
  void claimTaskProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Claim Me Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner")))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.claimed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.claimed");
              assertThat(event.getDetails()).containsEntry("assignee_id", ownerMemberId);
            });
  }

  @Test
  void releaseTaskProducesAuditEvent() throws Exception {
    // Create and claim a task first
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Release Me Task", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner")))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.released", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("task.released");
              assertThat(event.getDetails()).containsEntry("previous_assignee_id", ownerMemberId);
            });
  }

  @Test
  void updateTaskPartialChangeOnlyCapturesChangedFields() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Keep Title", "priority": "LOW"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(createResult));

    // Only change priority, keep title the same
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ta_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Keep Title", "priority": "URGENT", "status": "OPEN"}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter("task", taskId, null, "task.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // GAP-L-05: title is always included as a plain string (current value) so the
              // activity feed renders the task identity, even when title itself didn't change.
              // Delta-tracked fields (priority here) keep their {from,to} shape.
              assertThat(event.getDetails()).containsEntry("title", "Keep Title");
              assertThat(event.getDetails()).containsKey("priority");
            });
  }
}
