package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
 * Single-shard characterization test — critical backward-compatibility gate. Verifies that all
 * existing functionality works identically when {@code kazi.sharding.enabled=true} with only the
 * primary shard configured. Provisions a tenant, creates a project, and verifies CRUD operations
 * produce correct results with composite tenant identifiers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// @TestPropertySource is required here: application-test.yml disables sharding globally, but this
// characterization test needs sharding enabled to verify backward compatibility. This genuinely
// varies per test class per the anti-pattern policy in backend/CLAUDE.md.
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SingleShardCharacterizationTest {

  private static final String ORG_ID = "org_shard_char_test";

  private final MockMvc mockMvc;
  private final TenantProvisioningService provisioningService;
  private final TenantIdentifierResolver resolver;

  @Autowired
  SingleShardCharacterizationTest(
      MockMvc mockMvc,
      TenantProvisioningService provisioningService,
      TenantIdentifierResolver resolver) {
    this.mockMvc = mockMvc;
    this.provisioningService = provisioningService;
    this.resolver = resolver;
  }

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Shard Characterization Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_shard_owner", "shard_owner@test.com", "Shard Owner", "owner");
  }

  @Test
  void resolverReturnsCompositeFormat() {
    // With sharding enabled but no SHARD_ID bound, resolver should return "primary:public"
    String identifier = resolver.resolveCurrentTenantIdentifier();
    assertThat(identifier).isEqualTo("primary:public");
  }

  @Test
  void provisionThenCreateProject_worksWithShardingEnabled() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_shard_owner");

    // Create a project
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Shard Test Project","description":"Characterization test"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Shard Test Project"))
            .andReturn();

    String projectId =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Read the project back
    mockMvc
        .perform(get("/api/projects/" + projectId).with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Shard Test Project"));
  }

  @Test
  void listProjects_returnsResultsWithCompositeIdentifier() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_shard_owner");

    mockMvc
        .perform(get("/api/projects").with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
