package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FicaVerificationSkillTest {

  private static final String ORG_ID = "org_fica_skill_test";
  private final AtomicInteger counter = new AtomicInteger(0);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSkillExecutionService executionService;
  @Autowired private StorageService storageService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ChecklistInstanceRepository checklistInstanceRepository;
  @Autowired private ChecklistInstanceItemRepository checklistInstanceItemRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "FICA Skill Test Org", null);
    String ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_fica_owner", "fica_owner@test.com", "FICA Owner", "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void ficaSkill_producesCompletedExecution_withCorrectSkillId() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithDocumentAndChecklist();
          var context =
              new SkillContext(customerId, "CUSTOMER", "FICA verification test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution).isNotNull();
          assertThat(execution.getStatus()).isEqualTo("COMPLETED");
          assertThat(execution.getSkillId()).isEqualTo("fica-verification");
          assertThat(execution.getEntityType()).isEqualTo("CUSTOMER");
        });
  }

  @Test
  void ficaSkill_outputIsParseable_asFicaVerificationOutput() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithDocumentAndChecklist();
          var context =
              new SkillContext(customerId, "CUSTOMER", "FICA output parse test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getOutputContent()).isNotNull();
          FicaVerificationOutput output = parseOutput(execution.getOutputContent());
          assertThat(output.overallAssessment()).isEqualTo("INCOMPLETE");
          assertThat(output.riskLevel()).isEqualTo("MEDIUM");
          assertThat(output.checklistReview()).hasSize(3);
          assertThat(output.recommendedActions()).hasSize(2);
        });
  }

  @Test
  void ficaSkill_markItemsCompleteAction_createsExecutionGate() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithDocumentAndChecklist();
          var context =
              new SkillContext(customerId, "CUSTOMER", "FICA gate creation test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          // The canned response has one MARK_ITEMS_COMPLETE action -> one gate
          assertThat(gates).hasSize(1);
          assertThat(gates.getFirst().getGateType()).isEqualTo("MARK_KYC_COMPLETE");
          assertThat(gates.getFirst().getStatus()).isEqualTo("PENDING");
          assertThat(gates.getFirst().getAiReasoning()).contains("FICA section 21");
        });
  }

  @Test
  void ficaSkill_requestAdditionalDocument_doesNotCreateGate() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithDocumentAndChecklist();
          var context =
              new SkillContext(customerId, "CUSTOMER", "FICA no extra gate test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          List<AiExecutionGate> gates = result.gates();

          // Only MARK_ITEMS_COMPLETE produces gates, not REQUEST_ADDITIONAL_DOCUMENT
          // The canned response has both types, but only 1 gate should be created
          assertThat(gates).hasSize(1);
          assertThat(gates.stream().noneMatch(g -> "REQUEST_DOCUMENT".equals(g.getGateType())))
              .isTrue();
        });
  }

  @Test
  void ficaSkill_failsGracefully_whenCustomerHasNoDocuments() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithChecklistOnly();

          var context = new SkillContext(customerId, "CUSTOMER", "FICA no docs test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("no uploaded documents");
        });
  }

  @Test
  void ficaSkill_preflightCheck_rejectsCustomerWithoutActiveChecklist() {
    runInTenant(
        () -> {
          UUID customerId = createCustomerWithDocumentOnly();

          var context =
              new SkillContext(customerId, "CUSTOMER", "FICA no checklist test", Map.of());

          SkillExecutionResult result =
              executionService.executeSkill("fica-verification", context, ownerMemberId, List.of());
          AiExecution execution = result.execution();

          assertThat(execution.getStatus()).isEqualTo("FAILED");
          assertThat(execution.getErrorMessage()).contains("no active compliance checklist");
        });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_EXECUTE", "AI_REVIEW", "AI_MANAGE"))
        .run(() -> transactionTemplate.executeWithoutResult(status -> action.run()));
  }

  private UUID createCustomerWithDocumentAndChecklist() {
    int idx = counter.incrementAndGet();
    var customer =
        TestCustomerFactory.createActiveCustomer(
            "FICA Test Client " + idx, "fica-client-" + idx + "@test.com", ownerMemberId);
    customer = customerRepository.saveAndFlush(customer);
    UUID customerId = customer.getId();

    // Upload a test document
    var doc =
        new Document(
            Document.Scope.CUSTOMER,
            null,
            customerId,
            "id-document.pdf",
            "application/pdf",
            1024L,
            ownerMemberId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/customer/" + customerId + "/id-document.pdf");
    doc.confirmUpload();
    documentRepository.saveAndFlush(doc);

    // Put bytes into InMemoryStorageService
    storageService.upload(
        doc.getS3Key(),
        ("Mock PDF content with enough text to pass threshold - this document represents "
                + "a South African ID for FICA verification purposes. Additional text to exceed "
                + "the 100 character threshold for scanned PDF detection.")
            .getBytes(StandardCharsets.UTF_8),
        "application/pdf");

    // Create template, template items, instance, and instance items
    var template =
        new ChecklistTemplate(
            "FICA Template " + idx,
            "Test FICA checklist",
            "fica-test-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8),
            "ANY",
            "CUSTOM",
            false);
    template = checklistTemplateRepository.saveAndFlush(template);

    var templateItem1 = new ChecklistTemplateItem(template.getId(), "SA ID Document", 1, true);
    templateItem1.setRequiresDocument(true);
    templateItem1.setRequiredDocumentLabel("ID Document");
    templateItem1 = checklistTemplateItemRepository.saveAndFlush(templateItem1);

    var templateItem2 = new ChecklistTemplateItem(template.getId(), "Proof of Address", 2, true);
    templateItem2.setRequiresDocument(true);
    templateItem2.setRequiredDocumentLabel("Address Proof");
    templateItem2 = checklistTemplateItemRepository.saveAndFlush(templateItem2);

    var instance = new ChecklistInstance(template.getId(), customerId, Instant.now());
    instance = checklistInstanceRepository.saveAndFlush(instance);

    var item1 =
        new ChecklistInstanceItem(
            instance.getId(),
            templateItem1.getId(),
            "South African ID Document",
            "Valid SA ID document",
            1,
            true,
            true,
            "ID Document");
    checklistInstanceItemRepository.saveAndFlush(item1);

    var item2 =
        new ChecklistInstanceItem(
            instance.getId(),
            templateItem2.getId(),
            "Proof of Residential Address",
            "Utility bill or bank statement",
            2,
            true,
            true,
            "Address Proof");
    checklistInstanceItemRepository.saveAndFlush(item2);

    return customerId;
  }

  private UUID createCustomerWithChecklistOnly() {
    int idx = counter.incrementAndGet();
    var customer =
        TestCustomerFactory.createActiveCustomer(
            "FICA NoDocs Client " + idx, "fica-nodocs-" + idx + "@test.com", ownerMemberId);
    customer = customerRepository.saveAndFlush(customer);
    UUID customerId = customer.getId();

    // Create template and checklist instance with PENDING items but NO documents
    var template =
        new ChecklistTemplate(
            "FICA NoDocs Template " + idx,
            "NoDocs test",
            "fica-nodocs-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8),
            "ANY",
            "CUSTOM",
            false);
    template = checklistTemplateRepository.saveAndFlush(template);

    var templateItem = new ChecklistTemplateItem(template.getId(), "SA ID", 1, true);
    templateItem.setRequiresDocument(true);
    templateItem = checklistTemplateItemRepository.saveAndFlush(templateItem);

    var instance = new ChecklistInstance(template.getId(), customerId, Instant.now());
    instance = checklistInstanceRepository.saveAndFlush(instance);

    var item =
        new ChecklistInstanceItem(
            instance.getId(),
            templateItem.getId(),
            "SA ID Document",
            "Valid SA ID",
            1,
            true,
            true,
            "ID Document");
    checklistInstanceItemRepository.saveAndFlush(item);

    return customerId;
  }

  private UUID createCustomerWithDocumentOnly() {
    int idx = counter.incrementAndGet();
    var customer =
        TestCustomerFactory.createActiveCustomer(
            "FICA NoChecklist Client " + idx,
            "fica-nochecklist-" + idx + "@test.com",
            ownerMemberId);
    customer = customerRepository.saveAndFlush(customer);
    UUID customerId = customer.getId();

    // Upload a document but do NOT create a checklist
    var doc =
        new Document(
            Document.Scope.CUSTOMER,
            null,
            customerId,
            "id-document.pdf",
            "application/pdf",
            1024L,
            ownerMemberId,
            Document.Visibility.INTERNAL);
    doc.assignS3Key("org/" + ORG_ID + "/customer/" + customerId + "/id-document.pdf");
    doc.confirmUpload();
    documentRepository.saveAndFlush(doc);

    storageService.upload(
        doc.getS3Key(),
        "Mock PDF content for no-checklist test".getBytes(StandardCharsets.UTF_8),
        "application/pdf");

    return customerId;
  }

  private FicaVerificationOutput parseOutput(String json) {
    try {
      return objectMapper.readValue(json, FicaVerificationOutput.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse FICA output: " + e.getMessage(), e);
    }
  }
}
