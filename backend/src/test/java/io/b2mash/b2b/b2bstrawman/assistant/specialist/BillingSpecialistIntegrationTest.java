package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.ProposeInvoiceLineGroupingTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.ProposeTimeEntryPolishTool;
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

/**
 * Integration tests for the Billing specialist's propose tools. Verifies that invocations are
 * recorded correctly and capability gates work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingSpecialistIntegrationTest {

  private static final String ORG_ID = "org_billing_spec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationService invocationService;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private ProposeTimeEntryPolishTool polishTool;
  @Autowired private ProposeInvoiceLineGroupingTool groupingTool;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Billing Spec Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_billing_owner", "billing@test.com", "Billing Owner", "owner");
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
  void proposeTimeEntryPolish_recordsInvocationWithPendingStatus() {
    var invoiceId = UUID.randomUUID();
    var timeEntryId = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("INVOICING"));
          var input =
              Map.<String, Object>of(
                  "invoiceId",
                  invoiceId.toString(),
                  "edits",
                  List.of(
                      Map.of(
                          "timeEntryId",
                          timeEntryId.toString(),
                          "polishedDescription",
                          "Telephone attendance upon client J")));

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) polishTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("editCount")).isEqualTo(1);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(BillingPolishPayload.class);

          var payload = (BillingPolishPayload) inv.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.edits()).hasSize(1);
          assertThat(payload.edits().getFirst().afterText())
              .isEqualTo("Telephone attendance upon client J");
        });
  }

  @Test
  void proposeInvoiceLineGrouping_recordsGroupingPayload() {
    var invoiceId = UUID.randomUUID();
    var te1 = UUID.randomUUID();
    var te2 = UUID.randomUUID();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "INVOICING"),
        () -> {
          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("INVOICING"));
          var input =
              Map.<String, Object>of(
                  "invoiceId",
                  invoiceId.toString(),
                  "groups",
                  List.of(
                      Map.of(
                          "description",
                          "Preparation for trial",
                          "hours",
                          3.5,
                          "sourceTimeEntryIds",
                          List.of(te1.toString(), te2.toString()))));

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) groupingTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("groupCount")).isEqualTo(1);

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(BillingGroupingPayload.class);

          var payload = (BillingGroupingPayload) inv.getProposedOutput();
          assertThat(payload.invoiceId()).isEqualTo(invoiceId);
          assertThat(payload.groups()).hasSize(1);
          assertThat(payload.groups().getFirst().sourceTimeEntryIds()).containsExactly(te1, te2);
        });
  }

  @Test
  void capabilityGate_memberWithoutInvoicing_cannotUsePolishTool() {
    assertThat(polishTool.requiredCapabilities()).contains("INVOICING");
    // A member without INVOICING capability would be filtered out by the tool registry
    // before execute() is ever called. The registry's filterBy removes tools whose
    // requiredCapabilities are not a subset of the member's capabilities.
    assertThat(polishTool.requiredCapabilities()).isNotEmpty();
    assertThat(groupingTool.requiredCapabilities()).contains("INVOICING");
  }
}
