package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTaskView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Optional;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Characterisation of the eight {@link TaskService} portal-task call sites (Phase 89 633A, R7.3)
 * that delegate to {@link PortalTaskEventPublisher}: each one, driven through the real API on a
 * client-linked matter, still projects the task into {@code portal.portal_tasks} (via {@code
 * PortalEventHandler} after commit). The inline delete path and an unlinked matter are covered too.
 * Green before and after the extraction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskServicePortalEventTest {

  private static final String ORG_ID = "org_task_portal_events";
  private static final String OWNER = "user_task_portal_events_owner";
  private static final String OWNER_NAME = "Task Portal Events Owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private UUID linkedProjectId;
  private UUID unlinkedProjectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Portal Events Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "task_portal_events_owner@test.com", OWNER_NAME, "owner");
    linkedProjectId =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), "Linked Matter", "Client-linked"));
    unlinkedProjectId =
        UUID.fromString(
            TestEntityHelper.createProject(
                mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), "Unlinked Matter", "No client"));
    String customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, OWNER),
            "Task Portal Events Client",
            "task_portal_events_client@test.com");
    transitionCustomerToActive(customerId);
    mockMvc
        .perform(
            post("/api/clients/" + customerId + "/matters/" + linkedProjectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isCreated());
  }

  @Test
  void createTaskProjects() throws Exception {
    UUID taskId = createTask("Create site");

    var row = row(taskId).orElseThrow();
    assertThat(row.name()).isEqualTo("Create site");
    assertThat(row.status()).isEqualTo("OPEN");
    assertThat(row.sortOrder()).isZero();
  }

  @Test
  void updateTaskProjects() throws Exception {
    UUID taskId = createTask("Update site");

    perform(
        put("/api/tasks/" + taskId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"title": "Update site edited", "status": "IN_PROGRESS", "priority": "HIGH"}
                """));

    var row = row(taskId).orElseThrow();
    assertThat(row.name()).isEqualTo("Update site edited");
    assertThat(row.status()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void claimAndReleaseProject() throws Exception {
    UUID taskId = createTask("Claim site");

    perform(post("/api/tasks/" + taskId + "/claim"));
    assertThat(row(taskId).orElseThrow().assigneeName()).isEqualTo(OWNER_NAME);
    assertThat(row(taskId).orElseThrow().status()).isEqualTo("IN_PROGRESS");

    perform(post("/api/tasks/" + taskId + "/release"));
    assertThat(row(taskId).orElseThrow().assigneeName()).isNull();
  }

  @Test
  void completeTaskProjects() throws Exception {
    UUID taskId = createTask("Complete site");
    perform(post("/api/tasks/" + taskId + "/claim"));

    perform(patch("/api/tasks/" + taskId + "/complete"));

    assertThat(row(taskId).orElseThrow().status()).isEqualTo("DONE");
  }

  @Test
  void completingARecurringTaskProjectsTheNextInstance() throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/matters/" + linkedProjectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Recurring site", "recurrenceRule": "FREQ=WEEKLY;INTERVAL=1",
                         "dueDate": "2026-03-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    UUID taskId = UUID.fromString(TestEntityHelper.extractId(created));
    perform(post("/api/tasks/" + taskId + "/claim"));

    var completed =
        mockMvc
            .perform(
                patch("/api/tasks/" + taskId + "/complete")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn();
    UUID nextId =
        UUID.fromString(
            JsonPath.read(completed.getResponse().getContentAsString(), "$.nextInstance.id"));

    assertThat(row(taskId).orElseThrow().status()).isEqualTo("DONE");
    var next = row(nextId).orElseThrow();
    assertThat(next.name()).isEqualTo("Recurring site");
    assertThat(next.assigneeName()).isEqualTo(OWNER_NAME);
  }

  @Test
  void cancelAndReopenProject() throws Exception {
    UUID taskId = createTask("Cancel site");

    perform(patch("/api/tasks/" + taskId + "/cancel"));
    assertThat(row(taskId).orElseThrow().status()).isEqualTo("CANCELLED");

    perform(patch("/api/tasks/" + taskId + "/reopen"));
    assertThat(row(taskId).orElseThrow().status()).isEqualTo("OPEN");
  }

  @Test
  void deleteRemovesTheRow() throws Exception {
    UUID taskId = createTask("Delete site");
    assertThat(row(taskId)).isPresent();

    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNoContent());

    assertThat(row(taskId)).isEmpty();
  }

  @Test
  void unlinkedMatterTaskNeverProjects() throws Exception {
    UUID taskId =
        UUID.fromString(
            TestEntityHelper.createTask(
                mockMvc,
                TestJwtFactory.ownerJwt(ORG_ID, OWNER),
                unlinkedProjectId.toString(),
                "Unlinked site"));
    perform(post("/api/tasks/" + taskId + "/claim"));

    assertThat(readModelRepo.findTasksByProject(unlinkedProjectId, ORG_ID)).isEmpty();
  }

  // ── helpers ──

  private UUID createTask(String title) throws Exception {
    return UUID.fromString(
        TestEntityHelper.createTask(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), linkedProjectId.toString(), title));
  }

  private Optional<PortalTaskView> row(UUID taskId) {
    return readModelRepo.findTasksByProject(linkedProjectId, ORG_ID).stream()
        .filter(t -> t.id().equals(taskId))
        .findFirst();
  }

  private void perform(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc
        .perform(request.with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk());
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/clients/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.transitionToActive(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, OWNER));
  }
}
