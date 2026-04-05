package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class TaskClaimIntegrationTest {
  private static final String ORG_ID = "org_claim_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;
  private String memberIdOwner;
  private String memberIdMember;
  private String memberIdMember2;

  @BeforeAll
  void provisionTenantAndProject() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Claim Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_claim_owner", "claim_owner@test.com", "Claim Owner", "owner");
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_claim_member",
            "claim_member@test.com",
            "Claim Member",
            "member");
    memberIdMember2 =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_claim_member2",
            "claim_member2@test.com",
            "Claim Member2",
            "member");

    // Create a project (owner is auto-assigned as lead)
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Claim Test Project", "description": "For claim tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Add both members to the project
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s"}
                    """
                        .formatted(memberIdMember2)))
        .andExpect(status().isCreated());
  }

  // --- Claim tests ---

  @Test
  void shouldClaimUnassignedTask() throws Exception {
    var taskId = createTask("Claim Me Task");

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.assigneeId").value(memberIdMember));
  }

  @Test
  void shouldReturn400WhenClaimingAlreadyClaimedTask() throws Exception {
    var taskId = createTask("Already Claimed Task");

    // First claim succeeds
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Second claim by another member fails (task is IN_PROGRESS, not OPEN)
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member2")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenClaimingDoneTask() throws Exception {
    var taskId = createTask("Done Task");

    // First transition to IN_PROGRESS (OPEN -> IN_PROGRESS is valid)
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Done Task",
                      "priority": "MEDIUM",
                      "status": "IN_PROGRESS"
                    }
                    """))
        .andExpect(status().isOk());

    // Then transition to DONE (IN_PROGRESS -> DONE is valid)
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Done Task",
                      "priority": "MEDIUM",
                      "status": "DONE"
                    }
                    """))
        .andExpect(status().isOk());

    // Trying to claim a DONE task should fail
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404WhenClaimingTaskNotInProject() throws Exception {
    // Use a non-existent task ID
    mockMvc
        .perform(
            post("/api/tasks/00000000-0000-0000-0000-000000000000/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isNotFound());
  }

  // --- Release tests ---

  @Test
  void shouldReleaseByAssignee() throws Exception {
    var taskId = createTask("Release By Assignee Task");

    // Claim the task
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Release by the assignee
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.assigneeId").isEmpty());
  }

  @Test
  void shouldReturn403WhenReleasedByNonAssigneeNonLead() throws Exception {
    var taskId = createTask("Non-Assignee Release Task");

    // Claim the task by member
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Try to release by member2 (non-assignee, non-lead) — should fail
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member2")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReleaseByLead() throws Exception {
    var taskId = createTask("Lead Release Task");

    // Claim the task by member
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Release by the owner (who is project lead) — should succeed
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.assigneeId").isEmpty());
  }

  @Test
  void shouldAllowReclaimAfterRelease() throws Exception {
    var taskId = createTask("Reclaim After Release Task");

    // First claim succeeds (version goes from 0 to 1)
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Release so we can test again
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
        .andExpect(status().isOk());

    // Claim again — verifies version tracking works across claim/release cycle
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/claim")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member2")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.assigneeId").value(memberIdMember2));
  }

  @Test
  void shouldReturn400WhenReleasingUnclaimedTask() throws Exception {
    var taskId = createTask("Unclaimed Release Task");

    // Task is OPEN with no assignee — release should fail with state guard
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/release")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Task is not currently claimed"));
  }

  @Test
  void shouldReturn409OrStateErrorOnConcurrentClaim() throws Exception {
    var taskId = createTask("Concurrent Claim Task");

    var barrier = new CyclicBarrier(2);
    var executor = Executors.newFixedThreadPool(2);

    Future<Integer> future1 =
        executor.submit(
            () -> {
              barrier.await();
              return mockMvc
                  .perform(
                      post("/api/tasks/" + taskId + "/claim")
                          .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member")))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });

    Future<Integer> future2 =
        executor.submit(
            () -> {
              barrier.await();
              return mockMvc
                  .perform(
                      post("/api/tasks/" + taskId + "/claim")
                          .with(TestJwtFactory.memberJwt(ORG_ID, "user_claim_member2")))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            });

    int status1 = future1.get();
    int status2 = future2.get();
    executor.shutdown();

    // Exactly one should succeed (200); the other gets 400 (state guard) or 409 (optimistic lock)
    var statuses = List.of(status1, status2);
    assertThat(statuses).containsOnlyOnce(200);
    assertThat(statuses.stream().filter(s -> s != 200).findFirst().orElseThrow()).isIn(400, 409);
  }

  // --- Helpers ---

  private String createTask(String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_claim_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return TestEntityHelper.extractIdFromLocation(result);
  }
}
