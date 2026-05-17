package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class AiSkillControllerTest {

  private static final String ORG_ID = "org_ai_skill_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Skill Controller Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_skill_ctrl_owner",
        "skill_ctrl_owner@test.com",
        "Skill Ctrl Owner",
        "owner");
    // Also sync a regular member with no AI capabilities
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_skill_ctrl_member",
        "skill_ctrl_member@test.com",
        "Skill Ctrl Member",
        "member");
  }

  @Test
  void ficaVerification_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/skills/fica-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": \"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ficaVerification_withUnprovisionedOrg_returnsForbidden() throws Exception {
    // A user from an org that hasn't been provisioned cannot access the endpoint
    mockMvc
        .perform(
            post("/api/ai/skills/fica-verification")
                .with(TestJwtFactory.ownerJwt("org_not_provisioned", "user_unknown"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": \"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void ficaVerification_withOwner_returnsOk_withFailedExecution_whenCustomerNotFound()
      throws Exception {
    // The fica-verification skill is registered; passing a non-existent customer ID
    // results in a FAILED execution (ResourceNotFoundException caught by the service)
    mockMvc
        .perform(
            post("/api/ai/skills/fica-verification")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_skill_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": \"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void unknownSkill_withOwner_returns404_whenSkillNotRegistered() throws Exception {
    // No AiSkill bean is registered with skillId "non-existent-skill" in this test context
    mockMvc
        .perform(
            post("/api/ai/skills/non-existent-skill")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_skill_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerId\": \""
                        + UUID.randomUUID()
                        + "\", \"description\": \"Testing unknown skill\"}"))
        .andExpect(status().isNotFound());
  }
}
