package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests verifying that MemberFilter resolves roles exclusively from the DB, never from
 * JWT claims.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberFilterDbRoleTest {

  private static final String ORG_ID = "org_db_role_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private MemberFilter memberFilter;

  private String schemaName;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DB Role Test Org", null);
    schemaName =
        mappingRepository
            .findByClerkOrgId(ORG_ID)
            .map(OrgSchemaMapping::getSchemaName)
            .orElseThrow();

    // Create a member with DB role "admin" via sync endpoint
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_db_admin", "db_admin@test.com", "DB Admin", "admin");
    // Create a member with DB role "member" via sync endpoint
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_db_member", "db_member@test.com", "DB Member", "member");
  }

  @Test
  void shouldResolveAdminRoleFromDbRegardlessOfJwtClaim() throws Exception {
    // JWT says "member" but DB has "admin" — DB should win
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_db_admin")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))))
        .andExpect(status().isOk());

    // Verify the member in DB still has "admin" role
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_db_admin");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("admin");
            });
  }

  @Test
  void shouldNotElevateMemberToAdminEvenIfJwtSaysAdmin() throws Exception {
    // JWT says "admin" but DB has "member" — DB should win, member stays "member"
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_db_member")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "admin")))))
        .andExpect(status().isOk());

    // Verify the member's DB role is still "member"
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_db_member");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }

  @Test
  void shouldLazyCreateWithDefaultMemberRoleNotJwtRole() throws Exception {
    // JWT says "admin" but user doesn't exist — should get default "member" role (no invitation)
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_lazy_db_role")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "admin")))))
        .andExpect(status().isOk());

    // Verify lazy-created member has "member" role (default), not "admin" from JWT
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_lazy_db_role");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("member");
            });
  }

  @Test
  void shouldReturnCachedDbRoleOnSubsequentRequests() throws Exception {
    // Evict cache first to start fresh
    memberFilter.evictFromCache(schemaName, "user_db_admin");

    // First request — loads from DB and caches
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_db_admin")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))))
        .andExpect(status().isOk());

    // Second request — should hit cache, still resolve DB role
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_db_admin")
                                    .claim("o", Map.of("id", ORG_ID, "rol", "member")))))
        .andExpect(status().isOk());

    // Verify member role unchanged
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var member = memberRepository.findByClerkUserId("user_db_admin");
              assertThat(member).isPresent();
              assertThat(member.get().getOrgRole()).isEqualTo("admin");
            });
  }
}
