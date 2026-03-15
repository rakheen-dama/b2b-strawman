package io.b2mash.b2b.b2bstrawman.automation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutomationTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_auto_tmpl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AutomationTemplateSeeder automationTemplateSeeder;
  @Autowired private AutomationRuleRepository ruleRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;

  private String memberIdOwner;
  private String tenantSchema;
  private UUID customRoleMemberId;
  private UUID noCapMemberId;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Automation Template Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner = syncMember("user_tmpl_owner", "tmpl_owner@test.com", "Tmpl Owner", "owner");
    syncMember("user_tmpl_member", "tmpl_member@test.com", "Tmpl Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    customRoleMemberId =
        UUID.fromString(
            syncMember(
                "user_tmpl_315b_custom", "tmpl_custom@test.com", "Tmpl Custom User", "member"));
    noCapMemberId =
        UUID.fromString(
            syncMember("user_tmpl_315b_nocap", "tmpl_nocap@test.com", "Tmpl NoCap User", "member"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.fromString(memberIdOwner))
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var withCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Automation Tmpl Manager",
                          "Can manage automations",
                          Set.of("AUTOMATIONS")));
              var customMember = memberRepository.findById(customRoleMemberId).orElseThrow();
              customMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withCapRole.id()).orElseThrow());
              memberRepository.save(customMember);

              var withoutCapRole =
                  orgRoleService.createRole(
                      new io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest(
                          "Team Lead Tmpl", "Can manage teams", Set.of("TEAM_OVERSIGHT")));
              var noCapMember = memberRepository.findById(noCapMemberId).orElseThrow();
              noCapMember.setOrgRoleEntity(
                  orgRoleRepository.findById(withoutCapRole.id()).orElseThrow());
              memberRepository.save(noCapMember);
            });
  }

  @Test
  void listTemplates_returns9() throws Exception {
    mockMvc
        .perform(get("/api/automation-templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(9)))
        .andExpect(jsonPath("$[0].slug").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].category").exists())
        .andExpect(jsonPath("$[0].triggerType").exists())
        .andExpect(jsonPath("$[0].actionCount").exists());
  }

  @Test
  void activateTemplate_createsRuleAndActions() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/automation-templates/task-completion-chain/activate").with(ownerJwt()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Task Completion Chain")))
            .andExpect(jsonPath("$.actions", hasSize(1)))
            .andExpect(jsonPath("$.actions[0].actionType", is("CREATE_TASK")))
            .andReturn();

    String ruleId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    assertTrue(ruleId != null && !ruleId.isEmpty());
  }

  @Test
  void activateTemplate_hasSourceTemplateAndSlug() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-templates/budget-alert-escalation/activate").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source", is("TEMPLATE")))
        .andExpect(jsonPath("$.templateSlug", is("budget-alert-escalation")));
  }

  @Test
  void activateTemplate_ruleIsEnabled() throws Exception {
    mockMvc
        .perform(post("/api/automation-templates/new-project-welcome/activate").with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.enabled", is(true)));
  }

  @Test
  void activateTemplate_sameTwice_createsTwoRules() throws Exception {
    mockMvc
        .perform(
            post("/api/automation-templates/document-review-notification/activate")
                .with(ownerJwt()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/automation-templates/document-review-notification/activate")
                .with(ownerJwt()))
        .andExpect(status().isCreated());

    // Both should exist — verify via listing rules
    var result =
        mockMvc
            .perform(get("/api/automation-rules").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String content = result.getResponse().getContentAsString();
    List<Map<String, Object>> rules =
        JsonPath.read(content, "$[?(@.templateSlug == 'document-review-notification')]");
    assertTrue(
        rules.size() >= 2, "Expected at least 2 rules with slug document-review-notification");
  }

  @Test
  void listTemplates_memberGets403() throws Exception {
    mockMvc
        .perform(get("/api/automation-templates").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void seederIdempotency_runTwice_noDuplicates() {
    // The seeder already ran during provisioning. Count rules seeded with enabled=false
    long countBefore =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () ->
                    ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(r -> r.getSource() == RuleSource.TEMPLATE && !r.isEnabled())
                        .count());

    // Run seeder again
    automationTemplateSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    long countAfter =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () ->
                    ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(r -> r.getSource() == RuleSource.TEMPLATE && !r.isEnabled())
                        .count());

    assertEquals(
        countBefore, countAfter, "Seeder should be idempotent — no new rules on second run");
  }

  @Test
  void seeder_createsRulesWithEnabledFalse() {
    // Check that the seeder-created rules (from provisioning) are disabled
    List<AutomationRule> seededRules =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .call(
                () ->
                    ruleRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(
                            r ->
                                r.getSource() == RuleSource.TEMPLATE && r.getTemplateSlug() != null)
                        .toList());

    // At least some should be disabled (seeder-created ones)
    boolean hasDisabled = seededRules.stream().anyMatch(r -> !r.isEnabled());
    assertTrue(hasDisabled, "Seeder should create disabled template rules");
  }

  // --- Capability Tests (added in Epic 315B) ---

  @Test
  void customRoleWithCapability_accessesTemplateEndpoint_returns200() throws Exception {
    mockMvc
        .perform(get("/api/automation-templates").with(customRoleJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void customRoleWithoutCapability_accessesTemplateEndpoint_returns403() throws Exception {
    mockMvc
        .perform(get("/api/automation-templates").with(noCapabilityJwt()))
        .andExpect(status().isForbidden());
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s",
                          "name": "%s", "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tmpl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tmpl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor customRoleJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tmpl_315b_custom")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor noCapabilityJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_tmpl_315b_nocap")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }
}
