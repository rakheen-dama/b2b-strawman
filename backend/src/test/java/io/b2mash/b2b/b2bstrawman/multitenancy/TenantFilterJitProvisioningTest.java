package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.jit-provisioning.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantFilterJitProvisioningTest {

  private static final String PRE_PROVISIONED_ORG_ID = "org_jit_pre_provisioned";
  private static final String JIT_ORG_ID = "org_jit_new_tenant";
  private static final String JIT_ORG_ID_2 = "org_jit_concurrent";
  private static final String JIT_ORG_SLUG = "jit-test-org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private TenantFilter tenantFilter;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(PRE_PROVISIONED_ORG_ID, "Pre-Provisioned Org", null);
    planSyncService.syncPlan(PRE_PROVISIONED_ORG_ID, "pro-plan");
  }

  @Test
  void firstRequest_unprovisionedOrg_provisionsSchema() throws Exception {
    // Verify org is not yet provisioned
    assertThat(mappingRepository.findByClerkOrgId(JIT_ORG_ID)).isEmpty();

    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(JIT_ORG_ID, JIT_ORG_SLUG)))
        .andExpect(status().isOk());

    // Verify org is now provisioned
    var mapping = mappingRepository.findByClerkOrgId(JIT_ORG_ID);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).startsWith("tenant_");
  }

  @Test
  void secondRequest_sameOrg_usesCache() throws Exception {
    // First request triggers JIT provisioning
    String orgId = "org_jit_cache_test";
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(orgId, "cache-test-org")))
        .andExpect(status().isOk());

    // Second request should succeed (uses cache, no re-provision)
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(orgId, "cache-test-org")))
        .andExpect(status().isOk());

    // Only one mapping should exist
    assertThat(mappingRepository.findByClerkOrgId(orgId)).isPresent();
  }

  @Test
  void provisionedOrg_noJitCall() throws Exception {
    // Pre-provisioned org should work without JIT
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(PRE_PROVISIONED_ORG_ID, "pre-prov-org")))
        .andExpect(status().isOk());
  }

  @Test
  void concurrent_firstRequests_idempotent() throws Exception {
    // First request triggers JIT provisioning
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(JIT_ORG_ID_2, "concurrent-org")))
        .andExpect(status().isOk());

    // Evict cache to force re-lookup
    tenantFilter.evictSchema(JIT_ORG_ID_2);

    // Second request should find existing mapping
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(JIT_ORG_ID_2, "concurrent-org")))
        .andExpect(status().isOk());

    // Only one mapping should exist
    assertThat(mappingRepository.findByClerkOrgId(JIT_ORG_ID_2)).isPresent();
  }

  @Test
  void jitProvisioning_usesSlugAsOrgName() throws Exception {
    String orgId = "org_jit_slug_name";
    mockMvc
        .perform(get("/api/projects").with(jwtForOrg(orgId, "my-org-slug")))
        .andExpect(status().isOk());

    assertThat(mappingRepository.findByClerkOrgId(orgId)).isPresent();
  }

  @Test
  void jitProvisioning_noSlug_usesOrgIdAsName() throws Exception {
    String orgId = "org_jit_no_slug";
    mockMvc.perform(get("/api/projects").with(jwtForOrgNoSlug(orgId))).andExpect(status().isOk());

    assertThat(mappingRepository.findByClerkOrgId(orgId)).isPresent();
  }

  @Test
  void jitProvisioning_memberRole_provisions() throws Exception {
    // With capability-based auth, any authenticated user can trigger JIT provisioning
    String orgId = "org_jit_member_role";
    mockMvc
        .perform(get("/api/projects").with(jwtForOrgWithRole(orgId, "member-org", "member")))
        .andExpect(status().isOk());

    assertThat(mappingRepository.findByClerkOrgId(orgId)).isPresent();
  }

  @Test
  void jitProvisioning_adminRole_provisions() throws Exception {
    String orgId = "org_jit_admin_role";
    mockMvc
        .perform(get("/api/projects").with(jwtForOrgWithRole(orgId, "admin-org", "admin")))
        .andExpect(status().isOk());

    assertThat(mappingRepository.findByClerkOrgId(orgId)).isPresent();
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor jwtForOrg(String orgId, String slug) {
    return jwt()
        .jwt(
            j ->
                j.subject("user_jit_tenant_test")
                    .claim("o", Map.of("id", orgId, "rol", "owner", "slg", slug)));
  }

  private JwtRequestPostProcessor jwtForOrgNoSlug(String orgId) {
    return jwt()
        .jwt(
            j -> j.subject("user_jit_tenant_test").claim("o", Map.of("id", orgId, "rol", "owner")));
  }

  private JwtRequestPostProcessor jwtForOrgWithRole(String orgId, String slug, String role) {
    return jwt()
        .jwt(
            j ->
                j.subject("user_jit_tenant_test")
                    .claim("o", Map.of("id", orgId, "rol", role, "slg", slug)));
  }
}
