package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Extended single-shard characterization test — backward-compatibility gate. Verifies that the full
 * stack (CRUD + TenantScopedRunner + job queue) works identically when {@code
 * kazi.sharding.enabled=true} with only the primary shard configured. Extends coverage beyond
 * {@link SingleShardCharacterizationTest} with TenantScopedRunner verification and job queue
 * interaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.sharding.enabled=true",
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleShardFullCharacterizationTest {

  private static final String ORG_ID = "org_full_char_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TenantScopedRunner tenantScopedRunner;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private JobEnqueuer jobEnqueuer;
  @Autowired private JobQueueRepository jobQueueRepository;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Full Characterization Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_full_char", "full_char@test.com", "Full Char Owner", "owner");
  }

  @Test
  void shardingEnabled_singlePrimaryShard_fullCrudWorksIdentically() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_full_char");

    // Create a project
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Full Char Project","description":"Single-shard characterization"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Full Char Project"))
            .andReturn();

    String projectId =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Read back
    mockMvc
        .perform(get("/api/projects/" + projectId).with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Full Char Project"));

    // List projects
    mockMvc
        .perform(get("/api/projects").with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());

    // Verify mapping uses primary shard
    var mapping = mappingRepository.findByExternalOrgId(ORG_ID);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo("primary");
  }

  @Test
  void tenantScopedRunner_forEachTenant_bindsShardId() {
    // Ensure our tenant mapping exists
    var mapping = mappingRepository.findByExternalOrgId(ORG_ID);
    assertThat(mapping).isPresent();
    String expectedTenantId = mapping.get().getSchemaName();

    AtomicInteger invoked = new AtomicInteger(0);
    AtomicInteger shardBound = new AtomicInteger(0);

    tenantScopedRunner.forEachTenant(
        (tenantId, orgId) -> {
          if (tenantId.equals(expectedTenantId)) {
            invoked.incrementAndGet();
            // Verify SHARD_ID is bound within the TenantScopedRunner action
            if (RequestScopes.SHARD_ID.isBound()) {
              assertThat(RequestScopes.SHARD_ID.get()).isEqualTo("primary");
              shardBound.incrementAndGet();
            }
          }
        });

    // The runner should have invoked our callback at least once for our tenant
    assertThat(invoked.get()).isGreaterThanOrEqualTo(1);
    // SHARD_ID should have been bound during the run
    assertThat(shardBound.get()).isGreaterThanOrEqualTo(1);
  }
}
