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

/** Integration tests verifying audit events produced by {@code TimeEntryService} operations. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntryServiceAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_te_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TimeEntry Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "TimeEntry Audit Test Org").schemaName();

    syncMember(ORG_ID, "user_te_owner", "te_owner@test.com", "TE Owner", "owner");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TE Audit Project", "description": "for time entry tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var projectId = UUID.fromString(extractIdFromLocation(projectResult));

    // Create a task for time entry tests
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "TE Audit Task", "priority": "MEDIUM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    taskId = UUID.fromString(extractIdFromLocation(taskResult));
  }

  @Test
  void createTimeEntryProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tasks/" + taskId + "/time-entries")
                    .with(ownerJwt())
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
                    .with(ownerJwt())
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
                .with(ownerJwt())
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
                    .with(ownerJwt())
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
        .perform(delete("/api/time-entries/" + entryId).with(ownerJwt()))
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
                    .with(ownerJwt())
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
                .with(ownerJwt())
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
              assertThat(event.getDetails()).isNull();
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
        .jwt(j -> j.subject("user_te_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
