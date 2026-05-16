package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiExecutionRepositoryTest {

  private static final String ORG_ID = "org_ai_exec_test";

  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "AI Execution Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void persist_roundTrip() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var memberId = UUID.randomUUID();
              var execution =
                  new AiExecution(
                      "fica-verification",
                      "CUSTOMER",
                      UUID.randomUUID(),
                      memberId,
                      "claude-sonnet-4-6",
                      2);

              var response =
                  new AiCompletionResponse(
                      "{\"verified\": true}",
                      "claude-sonnet-4-6",
                      1500,
                      800,
                      1200,
                      0,
                      "end_turn",
                      2345L);
              execution.markCompleted(response, 4200L);
              execution.setInputSummary("FICA verification for customer Acme Ltd");

              var saved = executionRepository.save(execution);
              assertThat(saved.getId()).isNotNull();

              var loaded = executionRepository.findById(saved.getId()).orElseThrow();
              assertThat(loaded.getSkillId()).isEqualTo("fica-verification");
              assertThat(loaded.getEntityType()).isEqualTo("CUSTOMER");
              assertThat(loaded.getStatus()).isEqualTo("COMPLETED");
              assertThat(loaded.getInputTokens()).isEqualTo(1500);
              assertThat(loaded.getOutputTokens()).isEqualTo(800);
              assertThat(loaded.getCacheReadInputTokens()).isEqualTo(1200);
              assertThat(loaded.getCostCents()).isEqualTo(4200L);
              assertThat(loaded.getDurationMs()).isEqualTo(2345L);
              assertThat(loaded.getFirmProfileVersion()).isEqualTo(2);
              assertThat(loaded.getInputSummary())
                  .isEqualTo("FICA verification for customer Acme Ltd");
              assertThat(loaded.getOutputContent()).isEqualTo("{\"verified\": true}");
            });
  }

  @Test
  void sumCostCentsForCurrentMonth_aggregatesCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var memberId = UUID.randomUUID();
              Instant monthStart =
                  Instant.now().truncatedTo(ChronoUnit.DAYS).minus(15, ChronoUnit.DAYS);

              // Create two executions with known costs
              var exec1 =
                  new AiExecution(
                      "fica-verification",
                      "CUSTOMER",
                      UUID.randomUUID(),
                      memberId,
                      "claude-sonnet-4-6",
                      1);
              exec1.markCompleted(
                  new AiCompletionResponse(
                      "{}", "claude-sonnet-4-6", 100, 50, 0, 0, "end_turn", 100L),
                  3000L);
              executionRepository.save(exec1);

              var exec2 =
                  new AiExecution(
                      "matter-intake",
                      "PROJECT",
                      UUID.randomUUID(),
                      memberId,
                      "claude-sonnet-4-6",
                      1);
              exec2.markCompleted(
                  new AiCompletionResponse(
                      "{}", "claude-sonnet-4-6", 200, 100, 0, 0, "end_turn", 200L),
                  5000L);
              executionRepository.save(exec2);

              long totalCost = executionRepository.sumCostCentsForCurrentMonth(monthStart);
              assertThat(totalCost).isGreaterThanOrEqualTo(8000L); // At least our two

              int count = executionRepository.countForCurrentMonth(monthStart);
              assertThat(count).isGreaterThanOrEqualTo(2);
            });
  }
}
