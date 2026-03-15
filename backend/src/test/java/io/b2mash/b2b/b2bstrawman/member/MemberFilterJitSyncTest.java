package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
class MemberFilterJitSyncTest {

  private static final String ORG_ID = "org_member_jit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;

  private String schemaName;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Member JIT Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        mappingRepository
            .findByClerkOrgId(ORG_ID)
            .map(OrgSchemaMapping::getSchemaName)
            .orElseThrow();
  }

  @Test
  void jitEnabled_newMember_usesJwtEmailAndName() throws Exception {
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailAndName("user_jit_email", ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_jit_email");
              assertThat(member).isPresent();
              assertThat(member.get().getEmail()).isEqualTo("user_jit_email@example.com");
              assertThat(member.get().getName()).isEqualTo("Test User");
            });
  }

  @Test
  void jitEnabled_noEmailClaim_usesPlaceholder() throws Exception {
    mockMvc
        .perform(get("/api/projects").with(jwtWithoutEmail("user_jit_no_email", ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_jit_no_email");
              assertThat(member).isPresent();
              assertThat(member.get().getEmail())
                  .isEqualTo("user_jit_no_email@placeholder.internal");
            });
  }

  @Test
  void existingMember_noReCreation() throws Exception {
    // First request creates the member
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailAndName("user_jit_existing", ORG_ID)))
        .andExpect(status().isOk());

    // Second request should not duplicate
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailAndName("user_jit_existing", ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_jit_existing");
              assertThat(member).isPresent();
            });
  }

  @Test
  void jitEnabled_noNameClaim_nameIsNull() throws Exception {
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailOnly("user_jit_no_name", ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_jit_no_name");
              assertThat(member).isPresent();
              assertThat(member.get().getEmail()).isEqualTo("user_jit_no_name@example.com");
              assertThat(member.get().getName()).isNull();
            });
  }

  @Test
  void secondRequest_sameUser_usesExisting() throws Exception {
    // First request creates the member
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailAndName("user_jit_second_req", ORG_ID)))
        .andExpect(status().isOk());

    // Second request for same user should reuse existing member
    mockMvc
        .perform(get("/api/projects").with(jwtWithEmailAndName("user_jit_second_req", ORG_ID)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_jit_second_req");
              assertThat(member).isPresent();
            });
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor jwtWithEmailAndName(String userId, String orgId) {
    return jwt()
        .jwt(
            j ->
                j.subject(userId)
                    .claim("o", Map.of("id", orgId, "rol", "member"))
                    .claim("email", userId + "@example.com")
                    .claim("name", "Test User"));
  }

  private JwtRequestPostProcessor jwtWithoutEmail(String userId, String orgId) {
    return jwt()
        .jwt(
            j ->
                j.subject(userId)
                    .claim("o", Map.of("id", orgId, "rol", "member"))
                    .claim("name", "Test User"));
  }

  private JwtRequestPostProcessor jwtWithEmailOnly(String userId, String orgId) {
    return jwt()
        .jwt(
            j ->
                j.subject(userId)
                    .claim("o", Map.of("id", orgId, "rol", "member"))
                    .claim("email", userId + "@example.com"));
  }
}
