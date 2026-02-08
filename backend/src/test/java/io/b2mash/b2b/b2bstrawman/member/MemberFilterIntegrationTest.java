package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantContext;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberFilterIntegrationTest {

  private static final String ORG_ID = "org_member_filter_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;

  private String schemaName;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "MemberFilter Test Org");
    schemaName =
        mappingRepository
            .findByClerkOrgId(ORG_ID)
            .map(OrgSchemaMapping::getSchemaName)
            .orElseThrow();
  }

  @Test
  void shouldSetMemberContextForExistingMember() throws Exception {
    // Pre-create member via sync service
    memberSyncService.syncMember(
        ORG_ID, "user_existing_filter", "existing@test.com", "Existing User", null, "admin");

    // Make API request — MemberFilter resolves existing member
    mockMvc.perform(get("/api/projects").with(existingMemberJwt())).andExpect(status().isOk());
  }

  @Test
  void shouldLazyCreateMemberForUnknownUser() throws Exception {
    // Make API request with user not in members table
    mockMvc.perform(get("/api/projects").with(unknownUserJwt())).andExpect(status().isOk());

    // Verify member was lazy-created in tenant schema
    try {
      TenantContext.setTenantId(schemaName);
      var member = memberRepository.findByClerkUserId("user_lazy_create");
      assertThat(member).isPresent();
      assertThat(member.get().getOrgRole()).isEqualTo("member");
      assertThat(member.get().getEmail()).isEqualTo("user_lazy_create@placeholder.internal");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void shouldNotFilterInternalEndpoints() throws Exception {
    // Internal endpoint bypasses MemberFilter entirely
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "user_internal_skip",
                      "email": "internal@test.com",
                      "name": "Internal Test",
                      "avatarUrl": null,
                      "orgRole": "admin"
                    }
                    """
                        .formatted(ORG_ID)))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldCacheMemberAndNotDuplicateOnSecondRequest() throws Exception {
    // First request — lazy-creates and caches
    mockMvc.perform(get("/api/projects").with(cacheTestJwt())).andExpect(status().isOk());

    // Second request — hits cache
    mockMvc.perform(get("/api/projects").with(cacheTestJwt())).andExpect(status().isOk());

    // Verify exactly one member record exists (not duplicated)
    try {
      TenantContext.setTenantId(schemaName);
      var member = memberRepository.findByClerkUserId("user_cache_test");
      assertThat(member).isPresent();
    } finally {
      TenantContext.clear();
    }
  }

  // --- JWT helpers ---

  private JwtRequestPostProcessor existingMemberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_existing_filter").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor unknownUserJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lazy_create").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor cacheTestJwt() {
    return jwt()
        .jwt(j -> j.subject("user_cache_test").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
