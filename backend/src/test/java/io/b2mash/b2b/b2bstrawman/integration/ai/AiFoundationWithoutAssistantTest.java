package io.b2mash.b2b.b2bstrawman.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventGroup;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventTypeRegistry;
import io.b2mash.b2b.b2bstrawman.audit.AuditSeverity;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replacement-path test for Epic 601 (Phase 86 slice 7, ADR-333): the in-app chat assistant and the
 * Phase 70 specialists are gone; the AI foundation stays. On legal-za tenants: (a) the Settings →
 * AI model list still comes from the re-homed {@code
 * integration.ai.provider.LlmChatProviderRegistry} for a BYOAK (anthropic) tenant; (b) a
 * contract-review skill run creates an {@code AiExecution} and a PENDING {@code AiExecutionGate};
 * (c) {@code GET /api/ai/gates} lists it and approval executes via {@code GateActionExecutor}; (d)
 * {@code AiCostService} reports the execution; the assistant routes, classes, tables, specialist
 * automation rows and capability are absent. (e) {@code McpReadOnlyRegistryTest} + {@code
 * McpHandshakeIntegrationTest} (kazi_ping) run unchanged in the same suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiFoundationWithoutAssistantTest {

  private static final String ORG_SKILLS = "org_ai_found_no_assistant_skills";
  private static final String ORG_BYOAK = "org_ai_found_no_assistant_byoak";
  private static final String OWNER_SKILLS = "user_afna_skills_owner";
  private static final String OWNER_BYOAK = "user_afna_byoak_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private DataSource dataSource;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private StorageService storageService;
  @Autowired private AiExecutionRepository executionRepository;
  @Autowired private LlmChatProviderRegistry llmChatProviderRegistry;
  @Autowired private AuditEventTypeRegistry auditEventTypeRegistry;

  private String skillsSchema;
  private UUID skillsOwnerId;
  private UUID projectId;
  private UUID documentId;

  @BeforeAll
  void setup() throws Exception {
    skillsSchema =
        provisioningService
            .provisionTenant(ORG_SKILLS, "AI Foundation Without Assistant (skills)", "legal-za")
            .schemaName();
    skillsOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_SKILLS, OWNER_SKILLS, "afna_skills@test.com", "AFNA Owner", "owner"));
    runInSkillsTenant(this::createContractFixture);

    provisioningService.provisionTenant(
        ORG_BYOAK, "AI Foundation Without Assistant (BYOAK)", "legal-za");
    TestMemberHelper.syncMember(
        mockMvc, ORG_BYOAK, OWNER_BYOAK, "afna_byoak@test.com", "AFNA BYOAK Owner", "owner");
    var byoak = TestJwtFactory.ownerJwt(ORG_BYOAK, OWNER_BYOAK);
    mockMvc
        .perform(
            put("/api/integrations/AI")
                .with(byoak)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"providerSlug":"anthropic","configJson":"{\\"model\\":\\"claude-sonnet-4-6\\"}"}
                    """))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/integrations/AI/set-key")
                .with(byoak)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"sk-test-key-afna-12345\"}"))
        .andExpect(status().isNoContent());
  }

  // (a) model list from the re-homed provider registry
  @Test
  void modelListComesFromReHomedProviderRegistry() throws Exception {
    assertThat(LlmChatProviderRegistry.class.getPackageName())
        .isEqualTo("io.b2mash.b2b.b2bstrawman.integration.ai.provider");
    assertThat(llmChatProviderRegistry.get("anthropic").availableModels()).isNotEmpty();

    mockMvc
        .perform(
            get("/api/settings/integrations/ai/models")
                .with(TestJwtFactory.ownerJwt(ORG_BYOAK, OWNER_BYOAK)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.models", hasSize(3)))
        .andExpect(jsonPath("$.models[0].id").value("claude-sonnet-4-6"))
        .andExpect(jsonPath("$.models[0].recommended").value(true));

    // A tenant with no BYOAK provider (noop) gets an empty list, not an error.
    mockMvc
        .perform(
            get("/api/settings/integrations/ai/models")
                .with(TestJwtFactory.ownerJwt(ORG_SKILLS, OWNER_SKILLS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.models", hasSize(0)));
  }

  // (b) + (c) skill execution → PENDING gate → listed → approval runs GateActionExecutor
  @Test
  void contractReviewCreatesPendingGate_listedAndApprovedViaGateActionExecutor() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_SKILLS, OWNER_SKILLS);
    var body = runContractReview();
    UUID executionId = UUID.fromString(JsonPath.read(body, "$.executionId"));
    String gateId = JsonPath.read(body, "$.gates[0].id");

    runInSkillsTenant(
        () -> {
          var execution = executionRepository.findById(executionId).orElseThrow();
          assertThat(execution.getSkillId()).isEqualTo("contract-review");
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
        });

    mockMvc
        .perform(get("/api/ai/gates").param("status", "PENDING").param("size", "100").with(jwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem(gateId)))
        .andExpect(jsonPath("$.content[*].executionId", hasItem(executionId.toString())));

    mockMvc
        .perform(
            post("/api/ai/gates/" + gateId + "/approve")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"Approved in AiFoundationWithoutAssistantTest\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // GateActionExecutor's CREATE_REVIEW_REPORT effect: an AI-generated report document.
    runInSkillsTenant(
        () ->
            assertThat(documentRepository.findProjectScopedByProjectId(projectId))
                .filteredOn(d -> Document.Source.AI_GENERATED.equals(d.getSource()))
                .anySatisfy(d -> assertThat(d.getFileName()).contains("Contract Review Report")));
  }

  // (d) AiCostService reports the execution (baseline + delta; count-bleed safe)
  @Test
  void costSummaryReportsTheExecution() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_SKILLS, OWNER_SKILLS);
    String before =
        mockMvc
            .perform(get("/api/ai/cost-summary").with(jwt))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID executionId = UUID.fromString(JsonPath.read(runContractReview(), "$.executionId"));
    long[] costCents = new long[1];
    runInSkillsTenant(
        () ->
            costCents[0] = executionRepository.findById(executionId).orElseThrow().getCostCents());
    assertThat(costCents[0]).isPositive();

    String after =
        mockMvc
            .perform(get("/api/ai/cost-summary").with(jwt))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int countDelta =
        JsonPath.<Integer>read(after, "$.invocationCount")
            - JsonPath.<Integer>read(before, "$.invocationCount");
    long spentDelta =
        ((Number) JsonPath.read(after, "$.currentMonthSpentCents")).longValue()
            - ((Number) JsonPath.read(before, "$.currentMonthSpentCents")).longValue();
    assertThat(countDelta).isEqualTo(1);
    assertThat(spentDelta).isEqualTo(costCents[0]);
  }

  @Test
  void assistantRoutesClassesAndConstantsAreGone() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_SKILLS, OWNER_SKILLS);
    // Were AssistantController / SpecialistController / AiSpecialistInvocationController.
    for (String path : List.of("/api/assistant/specialists", "/api/assistant/invocations")) {
      mockMvc.perform(get(path).with(jwt)).andExpect(status().isNotFound());
    }

    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.integration.ai.provider")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.assistant")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.assistant."));
    assertThatThrownBy(
            () -> Class.forName("io.b2mash.b2b.b2bstrawman.assistant.AssistantController"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(
            () ->
                Class.forName(
                    "io.b2mash.b2b.b2bstrawman.automation.executor.InvokeAiSpecialistActionExecutor"))
        .isInstanceOf(ClassNotFoundException.class);

    assertThat(Arrays.stream(ActionType.values()).map(Enum::name))
        .doesNotContain("INVOKE_AI_SPECIALIST");
    assertThat(Capability.ALL_NAMES)
        .doesNotContain("AI_ASSISTANT_USE")
        .contains("AI_MANAGE", "AI_EXECUTE", "AI_REVIEW", "MCP_ACCESS");

    // D2: ai.specialist.failed (still emitted by the skill pipeline) keeps its classification.
    var failed = auditEventTypeRegistry.resolve("ai.specialist.failed");
    assertThat(failed.eventType()).isEqualTo("ai.specialist.failed");
    assertThat(failed.severity()).isEqualTo(AuditSeverity.WARNING);
    assertThat(failed.group()).isEqualTo(AuditEventGroup.STANDARD);
  }

  @Test
  void assistantSchemaAndSpecialistRowsAreDropped() {
    var jdbc = new JdbcTemplate(dataSource);
    for (String table : List.of("ai_specialist_invocations", "ai_llm_calls")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT to_regclass(?)::text", String.class, skillsSchema + "." + table))
          .as(table)
          .isNull();
    }
    // V120 also added automation_rules.last_run_at — kept (SCHEDULED trigger).
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = ?"
                    + " AND table_name = 'automation_rules' AND column_name = 'last_run_at'",
                Integer.class,
                skillsSchema))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".automation_actions WHERE action_type = 'INVOKE_AI_SPECIALIST'"
                    .formatted(skillsSchema),
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".pack_install WHERE pack_id LIKE 'ai-specialist-%%'"
                    .formatted(skillsSchema),
                Integer.class))
        .isZero();
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private String runContractReview() throws Exception {
    return mockMvc
        .perform(
            post("/api/ai/skills/contract-review")
                .with(TestJwtFactory.ownerJwt(ORG_SKILLS, OWNER_SKILLS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"documentId":"%s","matterId":"%s"}
                    """
                        .formatted(documentId, projectId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.gates[0].gateType").value("CREATE_REVIEW_REPORT"))
        .andExpect(jsonPath("$.gates[0].status").value("PENDING"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void createContractFixture() {
    var project = new Project("AFNA Matter", "Contract review matter", skillsOwnerId);
    project.setWorkType("commercial");
    projectId = projectRepository.saveAndFlush(project).getId();

    var doc =
        new Document(
            Document.Scope.PROJECT,
            projectId,
            null,
            "afna-contract.json",
            "application/json",
            1024L,
            skillsOwnerId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_SKILLS + "/documents/afna-contract.json");
    doc.confirmUpload();
    doc = documentRepository.saveAndFlush(doc);
    documentId = doc.getId();
    storageService.upload(
        doc.getS3Key(),
        ("""
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"SERVICE LEVEL AGREEMENT"}]},
          {"type":"paragraph","content":[{"type":"text","text":"Clause 12.3 - Limitation of Liability."}]}
        ]}""")
            .getBytes(StandardCharsets.UTF_8),
        "application/json");
  }

  private void runInSkillsTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, skillsSchema)
        .where(RequestScopes.ORG_ID, ORG_SKILLS)
        .where(RequestScopes.MEMBER_ID, skillsOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }
}
