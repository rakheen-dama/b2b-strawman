package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberFilter;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustAccountingCapabilityTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_trust_cap_test";

  private final MockMvc mockMvc;
  private final TenantProvisioningService provisioningService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final OrgSettingsService orgSettingsService;
  private final OrgRoleRepository orgRoleRepository;
  private final MemberRepository memberRepository;
  private final MemberFilter memberFilter;
  private final TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID noTrustRoleId;

  @Autowired
  TrustAccountingCapabilityTest(
      MockMvc mockMvc,
      TenantProvisioningService provisioningService,
      OrgSettingsRepository orgSettingsRepository,
      OrgSettingsService orgSettingsService,
      OrgRoleRepository orgRoleRepository,
      MemberRepository memberRepository,
      MemberFilter memberFilter,
      TransactionTemplate transactionTemplate) {
    this.mockMvc = mockMvc;
    this.provisioningService = provisioningService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.orgSettingsService = orgSettingsService;
    this.orgRoleRepository = orgRoleRepository;
    this.memberRepository = memberRepository;
    this.memberFilter = memberFilter;
    this.transactionTemplate = transactionTemplate;
  }

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    // Provision tenant
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Cap Test Org", null).schemaName();

    // Sync owner member (has VIEW_TRUST via all capabilities)
    syncMember(ORG_ID, "user_trust_cap_owner", "trust_cap_owner@test.com", "Owner", "owner");

    // Enable trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Sync a member (will get default member role initially)
    syncMember(
        ORG_ID, "user_trust_cap_nocap", "trust_cap_nocap@test.com", "No Cap Member", "member");

    // Create a custom role with NO capabilities and reassign the member to it
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var role =
                          new OrgRole("No Trust", "no-trust", "Role without trust caps", false);
                      role.setCapabilities(Set.of());
                      orgRoleRepository.save(role);
                      noTrustRoleId = role.getId();
                    }));

    // Reassign the member to the custom role
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var member =
                          memberRepository.findByClerkUserId("user_trust_cap_nocap").orElseThrow();
                      var role = orgRoleRepository.findById(noTrustRoleId).orElseThrow();
                      member.setOrgRoleEntity(role);
                      memberRepository.save(member);
                    }));

    // Evict cached member so MemberFilter picks up the new role
    memberFilter.evictFromCache(tenantSchema, "user_trust_cap_nocap");
  }

  @Test
  void trustAccountingStatus_returns200_whenMemberHasViewTrustCapability() throws Exception {
    mockMvc
        .perform(get("/api/trust-accounting/status").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.module").value("trust_accounting"))
        .andExpect(jsonPath("$.status").value("active"));
  }

  @Test
  void trustAccountingStatus_returns403_whenMemberLacksViewTrustCapability() throws Exception {
    mockMvc
        .perform(get("/api/trust-accounting/status").with(noCapJwt()))
        .andExpect(status().isForbidden());
  }

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    mockMvc
        .perform(
            post("/internal/members/sync")
                .header("X-API-KEY", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clerkOrgId": "%s",
                      "clerkUserId": "%s",
                      "email": "%s",
                      "name": "%s",
                      "avatarUrl": null,
                      "orgRole": "%s"
                    }
                    """
                        .formatted(orgId, clerkUserId, email, name, orgRole)))
        .andExpect(status().isCreated());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_trust_cap_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(new SimpleGrantedAuthority("VIEW_TRUST"));
  }

  private JwtRequestPostProcessor noCapJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_trust_cap_nocap").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(Collections.emptyList());
  }
}
