package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
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
 * Integration tests for shard-aware provisioning. Runs with sharding enabled so that ShardRegistry
 * is available. Uses the single embedded Postgres as the "primary" shard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShardAwareProvisioningTest {

  private static final String API_KEY = "test-api-key";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private MockMvc mockMvc;

  @Test
  void provisionWithDefaultShard_setsShardIdToPrimary() {
    var result =
        provisioningService.provisionTenant("org_shard_default", "Default Shard Org", null);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();

    var mapping = mappingRepository.findByClerkOrgId("org_shard_default");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo("primary");
  }

  @Test
  void provisionWithExplicitPrimaryShard_setsShardIdToPrimary() {
    var result =
        provisioningService.provisionTenant(
            "org_shard_explicit", "Explicit Shard Org", null, null, "primary");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();

    var mapping = mappingRepository.findByClerkOrgId("org_shard_explicit");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo("primary");
  }

  @Test
  void provisionWithInvalidShard_throwsInvalidStateException() {
    assertThatThrownBy(
            () ->
                provisioningService.provisionTenant(
                    "org_shard_invalid", "Invalid Shard Org", null, null, "nonexistent_shard"))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not active or does not exist");
  }

  @Test
  void provisioningApiAcceptsShardIdInRequestBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/orgs/provision")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clerkOrgId": "org_api_shard", "orgName": "API Shard Org", "shardId": "primary"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.schemaName").exists())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    var mapping = mappingRepository.findByClerkOrgId("org_api_shard");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getShardId()).isEqualTo("primary");
  }
}
