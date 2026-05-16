package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiFirmProfileIntegrationTest {

  private static final String ORG_ID = "org_ai_profile_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiFirmProfileService profileService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Profile Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_aiprof_owner", "aiprof_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_aiprof_member", "aiprof_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void getProfile_createsDefaultWhenNoneExists() throws Exception {
    // Use a dedicated org so no other test can mutate it first
    String defaultOrg = "org_ai_profile_def";
    provisioningService.provisionTenant(defaultOrg, "AI Profile Default Org", null);
    TestMemberHelper.syncMember(
        mockMvc, defaultOrg, "user_aiprof_def_owner", "aiprof_def@test.com", "Def Owner", "owner");

    mockMvc
        .perform(
            get("/api/ai/profile")
                .with(TestJwtFactory.ownerJwt(defaultOrg, "user_aiprof_def_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.jurisdiction").value("ZA"))
        .andExpect(jsonPath("$.riskCalibration").value("CONSERVATIVE"))
        .andExpect(jsonPath("$.profileVersion").value(1))
        .andExpect(jsonPath("$.coldStartCompleted").value(false));
  }

  @Test
  void putProfile_createsNewProfileWithAllFields() throws Exception {
    // Use a fresh org to ensure no profile exists yet
    String freshOrg = "org_ai_profile_put";
    provisioningService.provisionTenant(freshOrg, "AI Profile Put Org", null);
    TestMemberHelper.syncMember(
        mockMvc, freshOrg, "user_aiprof_put_owner", "aiprof_put@test.com", "Put Owner", "owner");

    String requestBody =
        """
        {
          "practiceAreas": ["litigation", "estates", "collections"],
          "jurisdiction": "ZA-GP",
          "riskCalibration": "MODERATE",
          "houseStyleNotes": "Formal English, use Attorneys not Lawyers",
          "ficaRequirements": {"enhancedDueDiligence": ["trusts"]},
          "feeEstimationNotes": "Standard LSSA tariff + 15%",
          "preferredModel": "claude-sonnet-4-6",
          "monthlyBudgetCents": 500000,
          "coldStartCompleted": true
        }
        """;

    mockMvc
        .perform(
            put("/api/ai/profile")
                .with(TestJwtFactory.ownerJwt(freshOrg, "user_aiprof_put_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.practiceAreas[0]").value("litigation"))
        .andExpect(jsonPath("$.practiceAreas[1]").value("estates"))
        .andExpect(jsonPath("$.practiceAreas[2]").value("collections"))
        .andExpect(jsonPath("$.jurisdiction").value("ZA-GP"))
        .andExpect(jsonPath("$.riskCalibration").value("MODERATE"))
        .andExpect(jsonPath("$.houseStyleNotes").value("Formal English, use Attorneys not Lawyers"))
        .andExpect(jsonPath("$.feeEstimationNotes").value("Standard LSSA tariff + 15%"))
        .andExpect(jsonPath("$.preferredModel").value("claude-sonnet-4-6"))
        .andExpect(jsonPath("$.monthlyBudgetCents").value(500000))
        .andExpect(jsonPath("$.coldStartCompleted").value(true))
        .andExpect(jsonPath("$.profileVersion").value(2));
  }

  @Test
  void putProfile_updatesExistingAndIncrementsVersion() throws Exception {
    // First create a profile via GET (lazy-creates)
    String updateOrg = "org_ai_profile_upd";
    provisioningService.provisionTenant(updateOrg, "AI Profile Update Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        updateOrg,
        "user_aiprof_upd_owner",
        "aiprof_upd@test.com",
        "Update Owner",
        "owner");

    // GET creates default profile with version 1
    mockMvc
        .perform(
            get("/api/ai/profile")
                .with(TestJwtFactory.ownerJwt(updateOrg, "user_aiprof_upd_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileVersion").value(1));

    // PUT updates to version 2
    mockMvc
        .perform(
            put("/api/ai/profile")
                .with(TestJwtFactory.ownerJwt(updateOrg, "user_aiprof_upd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "practiceAreas": ["commercial"],
                      "jurisdiction": "ZA-WC"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileVersion").value(2))
        .andExpect(jsonPath("$.jurisdiction").value("ZA-WC"))
        .andExpect(jsonPath("$.practiceAreas[0]").value("commercial"));

    // PUT again updates to version 3
    mockMvc
        .perform(
            put("/api/ai/profile")
                .with(TestJwtFactory.ownerJwt(updateOrg, "user_aiprof_upd_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jurisdiction": "ZA-KZN"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileVersion").value(3))
        .andExpect(jsonPath("$.jurisdiction").value("ZA-KZN"));
  }

  @Test
  void assembleProfileBlock_producesCorrectXmlBlock() {
    // Use ScopedValue to run in tenant context
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_MANAGE"))
        .run(
            () -> {
              // Update profile with full data so cold_start is complete
              profileService.updateProfile(
                  new UpdateAiFirmProfileRequest(
                      java.util.List.of("litigation", "estates"),
                      "ZA-GP",
                      "CONSERVATIVE",
                      "Formal English",
                      java.util.Map.of("pepScreening", "required"),
                      "LSSA tariff + 15%",
                      "claude-sonnet-4-6",
                      500000L,
                      true));

              String block = profileService.assembleProfileBlock();
              assertThat(block).startsWith("<firm-profile version=\"");
              assertThat(block).contains("Practice areas: litigation, estates");
              assertThat(block).contains("Jurisdiction: ZA-GP");
              assertThat(block).contains("Risk calibration: CONSERVATIVE");
              assertThat(block).contains("House style: Formal English");
              assertThat(block).contains("Fee estimation: LSSA tariff + 15%");
              assertThat(block).endsWith("</firm-profile>");
            });
  }

  @Test
  void getProfile_requiresAiManageCapability() throws Exception {
    mockMvc
        .perform(
            get("/api/ai/profile").with(TestJwtFactory.memberJwt(ORG_ID, "user_aiprof_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void putProfile_requiresAiManageCapability() throws Exception {
    mockMvc
        .perform(
            put("/api/ai/profile")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_aiprof_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jurisdiction": "ZA-GP"}
                    """))
        .andExpect(status().isForbidden());
  }
}
