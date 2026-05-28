package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class JobQueueAdminControllerIntegrationTest {

  private static final String BASE_PATH = "/api/platform-admin/jobs";
  private static final String TENANT_1 = "tenant_admin_test_001";
  private static final String ORG_1 = "org_admin_test_1";

  @Autowired private MockMvc mockMvc;
  @Autowired private JobQueueRepository jobQueueRepository;

  @BeforeEach
  void setUp() {
    jobQueueRepository.deleteAllInBatch();
  }

  // --- GET /api/platform-admin/jobs ---

  @Test
  void listDeadLetteredJobs_returnsCorrectResults() throws Exception {
    seedJob("sync_drain", JobStatus.DEAD_LETTER);
    seedJob("sync_drain", JobStatus.DEAD_LETTER);
    seedJob("sync_drain", JobStatus.PENDING);

    mockMvc
        .perform(get(BASE_PATH).param("status", "DEAD_LETTER").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].status").value("DEAD_LETTER"))
        .andExpect(jsonPath("$.content[0].jobType").value("sync_drain"));
  }

  @Test
  void listJobs_nonAdmin_returns403() throws Exception {
    mockMvc
        .perform(get(BASE_PATH).param("status", "DEAD_LETTER").with(regularJwt()))
        .andExpect(status().isForbidden());
  }

  // --- POST /api/platform-admin/jobs/{id}/retry ---

  @Test
  void retryJob_resetsDeadLetterToPending() throws Exception {
    var job = seedJob("sync_drain", JobStatus.DEAD_LETTER);

    mockMvc
        .perform(post(BASE_PATH + "/" + job.getId() + "/retry").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(job.getId().toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.retryCount").value(0));
  }

  @Test
  void retryJob_nonDeadLetter_returns400() throws Exception {
    var job = seedJob("sync_drain", JobStatus.PENDING);

    mockMvc
        .perform(post(BASE_PATH + "/" + job.getId() + "/retry").with(adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid job status"))
        .andExpect(
            jsonPath("$.detail")
                .value("Only DEAD_LETTER jobs can be retried; current status is PENDING"));
  }

  // --- DELETE /api/platform-admin/jobs/{id} ---

  @Test
  void deleteJob_removesDeadLetteredJob() throws Exception {
    var job = seedJob("sync_drain", JobStatus.DEAD_LETTER);

    mockMvc
        .perform(delete(BASE_PATH + "/" + job.getId()).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteJob_nonDeadLetter_returns400() throws Exception {
    var job = seedJob("sync_drain", JobStatus.COMPLETED);

    mockMvc
        .perform(delete(BASE_PATH + "/" + job.getId()).with(adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid job status"))
        .andExpect(
            jsonPath("$.detail")
                .value("Only DEAD_LETTER jobs can be deleted; current status is COMPLETED"));
  }

  // --- GET /api/platform-admin/jobs/stats ---

  @Test
  void getStats_returnsCorrectCounts() throws Exception {
    seedJob("sync_drain", JobStatus.PENDING);
    seedJob("sync_drain", JobStatus.DEAD_LETTER);
    seedJob("poll_triggers", JobStatus.PENDING);
    seedJob("poll_triggers", JobStatus.COMPLETED);

    mockMvc
        .perform(get(BASE_PATH + "/stats").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.byStatus.PENDING").value(2))
        .andExpect(jsonPath("$.byStatus.DEAD_LETTER").value(1))
        .andExpect(jsonPath("$.byStatus.COMPLETED").value(1))
        .andExpect(jsonPath("$.byJobType.sync_drain.PENDING").value(1))
        .andExpect(jsonPath("$.byJobType.sync_drain.DEAD_LETTER").value(1))
        .andExpect(jsonPath("$.byJobType.poll_triggers.PENDING").value(1))
        .andExpect(jsonPath("$.byJobType.poll_triggers.COMPLETED").value(1));
  }

  // --- Helpers ---

  private JobQueue seedJob(String jobType, JobStatus status) {
    var job = new JobQueue(jobType, TENANT_1, ORG_1, "primary", null, 3);
    job.setStatus(status);
    if (status == JobStatus.DEAD_LETTER) {
      job.setRetryCount(3);
      job.setErrorMessage("Test error");
    }
    return jobQueueRepository.saveAndFlush(job);
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_platform_admin").claim("groups", List.of("platform-admins")));
  }

  private JwtRequestPostProcessor regularJwt() {
    return jwt().jwt(j -> j.subject("user_regular"));
  }
}
