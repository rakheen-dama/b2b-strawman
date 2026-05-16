package io.b2mash.b2b.b2bstrawman.integration.ai.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.UpdateAiFirmProfileRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiCostServiceTest {

  private static final String ORG_ID = "org_ai_cost_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiCostService costService;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private AiFirmProfileService firmProfileService;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Cost Service Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_cost_svc_owner", "cost_svc_owner@test.com", "Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void calculateCostCents_computesCorrectZarFromUsdPricing() {
    // Given: 2000 input tokens, 800 output tokens, 1500 cache read, 0 cache creation
    // Model: claude-sonnet-4-6
    // Input cost: (2000 * 3.00 + 1500 * 0.30 + 0 * 3.75) / 1_000_000 = 0.006450 USD
    // Output cost: (800 * 15.00) / 1_000_000 = 0.012000 USD
    // Total USD: 0.018450
    // ZAR cents: 0.018450 * 18.50 * 100 = 34.1325 -> rounded = 34
    var response =
        new AiCompletionResponse(
            "output", "claude-sonnet-4-6", 2000, 800, 1500, 0, "end_turn", 5000L);

    long costCents = costService.calculateCostCents(response);

    assertThat(costCents).isEqualTo(34L);
  }

  @Test
  void checkBudget_passesWhenUnderBudget() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_MANAGE"))
        .run(
            () -> {
              // Set a generous budget
              firmProfileService.updateProfile(
                  new UpdateAiFirmProfileRequest(
                      List.of("litigation"),
                      "ZA",
                      "CONSERVATIVE",
                      null,
                      null,
                      null,
                      "claude-sonnet-4-6",
                      500000L, // R5000 budget
                      true));

              var profile = firmProfileService.getOrCreateProfile();

              // Should not throw — no executions recorded yet for this tenant
              costService.checkBudget(profile);
            });
  }

  @Test
  void checkBudget_throwsWhenBudgetExhausted() {
    // Use a separate org to avoid interference with other tests
    String budgetOrg = "org_ai_cost_budget_test";
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_MANAGE"))
        .run(
            () -> {
              // Set a very low budget (100 cents = R1)
              firmProfileService.updateProfile(
                  new UpdateAiFirmProfileRequest(
                      List.of("litigation"),
                      "ZA",
                      "CONSERVATIVE",
                      null,
                      null,
                      null,
                      "claude-sonnet-4-6",
                      100L, // R1 budget
                      true));

              // Create executions that exceed the budget
              var execution =
                  new AiExecution(
                      "fica-verification",
                      "customer",
                      UUID.randomUUID(),
                      ownerMemberId,
                      "claude-sonnet-4-6",
                      1);
              execution.markCompleted(
                  new AiCompletionResponse(
                      "output", "claude-sonnet-4-6", 10000, 5000, 0, 0, "end_turn", 3000L),
                  150L); // 150 cents > 100 budget
              executionRepository.save(execution);

              var profile = firmProfileService.getOrCreateProfile();

              assertThatThrownBy(() -> costService.checkBudget(profile))
                  .isInstanceOf(InvalidStateException.class)
                  .hasMessageContaining("AI budget exhausted");
            });
  }
}
