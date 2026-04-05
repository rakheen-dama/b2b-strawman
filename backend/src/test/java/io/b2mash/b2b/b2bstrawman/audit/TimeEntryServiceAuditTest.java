package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** Integration tests verifying audit events produced by {@code TimeEntryService} operations. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntryServiceAuditTest {
  private static final String ORG_ID = "org_te_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TimeEntry Audit Test Org", null);
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "TimeEntry Audit Test Org", null).schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_te_owner", "te_owner@test.com", "TE Owner", "owner");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TE Audit Project", "description": "for time entry tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(TestEntityHelper.extractIdFromLocation(projectResult));

    // Create a task for time entry tests
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "TE Audit Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    taskId = UUID.fromString(TestEntityHelper.extractIdFromLocation(taskResult));
  }

  @Test
  void createTimeEntryProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"date": "2025-01-15", "durationMinutes": 120, "billable": true}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var entryId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "time_entry", entryId, null, "time_entry.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("time_entry.created");
              assertThat(event.getEntityType()).isEqualTo("time_entry");
              assertThat(event.getEntityId()).isEqualTo(entryId);
              assertThat(event.getDetails()).containsEntry("task_id", taskId.toString());
              assertThat(event.getDetails()).containsEntry("duration_minutes", 120);
              assertThat(event.getDetails()).containsEntry("billable", true);
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void updateTimeEntryCapturesFieldDeltas() throws Exception {
    // Create a time entry
    var createResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"date": "2025-02-10", "durationMinutes": 60, "billable": false}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var entryId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    // Update duration and billable
    mockMvc
        .perform(
            put("/api/time-entries/" + entryId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"durationMinutes": 90, "billable": true}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "time_entry", entryId, null, "time_entry.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("time_entry.updated");

              @SuppressWarnings("unchecked")
              var durationChange = (Map<String, Object>) event.getDetails().get("duration_minutes");
              assertThat(durationChange).containsEntry("from", 60);
              assertThat(durationChange).containsEntry("to", 90);

              @SuppressWarnings("unchecked")
              var billableChange = (Map<String, Object>) event.getDetails().get("billable");
              assertThat(billableChange).containsEntry("from", false);
              assertThat(billableChange).containsEntry("to", true);
            });
  }

  @Test
  void deleteTimeEntryProducesAuditEvent() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"date": "2025-03-01", "durationMinutes": 30}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var entryId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            delete("/api/time-entries/" + entryId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner")))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "time_entry", entryId, null, "time_entry.deleted", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("time_entry.deleted");
              assertThat(event.getDetails()).containsEntry("task_id", taskId.toString());
            });
  }

  @Test
  void updateTimeEntryWithNoChangesProducesEventWithNullDetails() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"date": "2025-04-01", "durationMinutes": 45, "billable": true}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var entryId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    // Update with same values
    mockMvc
        .perform(
            put("/api/time-entries/" + entryId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_te_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"durationMinutes": 45, "billable": true}
                    """))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "time_entry", entryId, null, "time_entry.updated", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              // Details now always includes project_id even when no fields changed
              assertThat(event.getDetails()).isNotNull();
              assertThat(event.getDetails()).containsKey("project_id");
              assertThat(event.getDetails()).containsKey("actor_name");
              assertThat(event.getDetails()).hasSize(2);
            });
  }
}
