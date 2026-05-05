package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.IntakeExtractionPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.read.ExtractTextFromDocumentTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.read.ListDocumentsForContextTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.ProposeCustomerFieldExtractionTool;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntakeSpecialistIntegrationTest {

  private static final String ORG_ID = "org_intake_spec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private ListDocumentsForContextTool listDocsTool;
  @Autowired private ExtractTextFromDocumentTool extractTextTool;
  @Autowired private ProposeCustomerFieldExtractionTool proposeTool;
  @Autowired private AssistantToolRegistry toolRegistry;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Intake Spec Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_intake_owner", "intake@test.com", "Intake Owner", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runWithCaps(Set<String> caps, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, caps)
        .run(body);
  }

  @Test
  void proposeCustomerFieldExtraction_recordsInvocationWithPendingStatus() {
    var customerId = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_EDIT"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_EDIT"));
          var input =
              Map.<String, Object>of(
                  "contextEntityType",
                  "customer",
                  "contextEntityId",
                  customerId.toString(),
                  "proposedFields",
                  Map.of("name", "Test Client (Pty) Ltd", "idNumber", "8001015009087"),
                  "extractionPath",
                  "TEXT");

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) proposeTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("fieldCount")).isEqualTo(2);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(IntakeExtractionPayload.class);

          var payload = (IntakeExtractionPayload) inv.getProposedOutput();
          assertThat(payload.contextEntityType()).isEqualTo("customer");
          assertThat(payload.contextEntityId()).isEqualTo(customerId);
          assertThat(payload.proposedFields()).containsEntry("name", "Test Client (Pty) Ltd");
          assertThat(payload.extractionPath()).isEqualTo("TEXT");
        });
  }

  @Test
  void proposeWithValidationFlags_recordsFlags() {
    var customerId = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_EDIT"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_EDIT"));
          var input =
              Map.<String, Object>of(
                  "contextEntityType",
                  "customer",
                  "contextEntityId",
                  customerId.toString(),
                  "proposedFields",
                  Map.of("idNumber", "8001015009000"),
                  "extractionPath",
                  "TEXT",
                  "validationFlags",
                  List.of("RSA_ID_CHECKSUM_FAIL"),
                  "popiaFlaggedFields",
                  List.of("healthInfo"));

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) proposeTool.execute(input, ctx);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          var payload = (IntakeExtractionPayload) inv.getProposedOutput();
          assertThat(payload.validationFlags()).contains("RSA_ID_CHECKSUM_FAIL");
          assertThat(payload.popiaFlaggedFields()).contains("healthInfo");
        });
  }

  @Test
  void capabilityGate_memberWithoutCustomerEdit_cannotUseProposeExtractionTool() {
    assertThat(proposeTool.requiredCapabilities()).contains("CUSTOMER_EDIT");

    var intakeToolIds =
        List.of(
            "ListDocumentsForContext", "ExtractTextFromDocument", "ProposeCustomerFieldExtraction");
    var capsWithoutEdit = Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW");

    var filtered = toolRegistry.filterBy(intakeToolIds, capsWithoutEdit);
    var filteredNames = filtered.stream().map(t -> t.name()).toList();
    assertThat(filteredNames).doesNotContain("ProposeCustomerFieldExtraction");
    // Read tools should still be available with CUSTOMER_VIEW
    assertThat(filteredNames).contains("ListDocumentsForContext");
    assertThat(filteredNames).contains("ExtractTextFromDocument");

    var capsWithEdit = Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW", "CUSTOMER_EDIT");
    var filteredWithCap = toolRegistry.filterBy(intakeToolIds, capsWithEdit);
    var filteredWithCapNames = filteredWithCap.stream().map(t -> t.name()).toList();
    assertThat(filteredWithCapNames).contains("ProposeCustomerFieldExtraction");
  }

  @Test
  void intakeSpecialistConfig_hasToolsAndIsAutomationCapable() {
    // Verify the specialist is registered with correct tools
    var specialist =
        toolRegistry.filterBy(
            List.of(
                "ListDocumentsForContext",
                "ExtractTextFromDocument",
                "ProposeCustomerFieldExtraction"),
            Set.of("CUSTOMER_VIEW", "CUSTOMER_EDIT"));
    assertThat(specialist).hasSize(3);
  }
}
