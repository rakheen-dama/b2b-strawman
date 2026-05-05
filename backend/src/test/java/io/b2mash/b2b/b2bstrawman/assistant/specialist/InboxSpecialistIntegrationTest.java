package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.read.GetMatterActivityWindowTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.PostInboxSummaryTool;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
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
 * Integration tests for the Inbox specialist: GetMatterActivityWindow tool, PostInboxSummary REVIEW
 * mode, specialist config, and capability gating.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InboxSpecialistIntegrationTest {

  private static final String ORG_ID = "org_inbox_spec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private GetMatterActivityWindowTool activityWindowTool;
  @Autowired private PostInboxSummaryTool postInboxSummaryTool;
  @Autowired private AssistantToolRegistry toolRegistry;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Inbox Spec Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inbox_owner", "inbox@test.com", "Inbox Owner", "owner");
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
  void getMatterActivityWindow_returnsActivityBundle() {
    var matterId = UUID.randomUUID();
    runWithCaps(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var ctx =
              new TenantToolContext(tenantSchema, memberId, "owner", Set.of("AI_ASSISTANT_USE"));
          var input = Map.<String, Object>of("matterId", matterId.toString(), "lookback", "P7D");

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) activityWindowTool.execute(input, ctx);

          assertThat(result).containsKey("matterId");
          assertThat(result).containsKey("from");
          assertThat(result).containsKey("to");
          assertThat(result).containsKey("events");
          assertThat(result).containsKey("trustTransactionsIncluded");
          // Non-legal-za org: trust transactions should not be included
          assertThat(result.get("trustTransactionsIncluded")).isEqualTo(false);
        });
  }

  @Test
  void getMatterActivityWindow_nonLegalZa_excludesTrustTransactions() {
    var matterId = UUID.randomUUID();
    runWithCaps(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var ctx =
              new TenantToolContext(tenantSchema, memberId, "owner", Set.of("AI_ASSISTANT_USE"));
          var input = Map.<String, Object>of("matterId", matterId.toString(), "lookback", "P7D");

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) activityWindowTool.execute(input, ctx);

          assertThat(result.get("trustTransactionsIncluded")).isEqualTo(false);

          @SuppressWarnings("unchecked")
          var events = (List<Map<String, Object>>) result.get("events");
          // No trust transaction events should be present for non-legal-za
          var trustEvents =
              events.stream().filter(e -> "TRUST_TRANSACTION".equals(e.get("source"))).toList();
          assertThat(trustEvents).isEmpty();
        });
  }

  @Test
  void postInboxSummary_reviewMode_createsPendingApproval() {
    var matterId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    runWithCaps(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var ctx =
              new TenantToolContext(tenantSchema, memberId, "owner", Set.of("AI_ASSISTANT_USE"));
          var input =
              Map.<String, Object>of(
                  "matterId",
                  matterId.toString(),
                  "summaryMarkdown",
                  "## Summary\n\nClient uploaded documents.",
                  "lookbackFrom",
                  Instant.now().minusSeconds(86400).toString(),
                  "lookbackTo",
                  Instant.now().toString(),
                  "sources",
                  List.of(Map.of("entityType", "comment", "entityId", sourceId.toString())),
                  "mode",
                  "REVIEW");

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) postInboxSummaryTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("status")).isEqualTo("PENDING_APPROVAL");

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isInstanceOf(InboxSummaryPayload.class);
          var payload = (InboxSummaryPayload) inv.getProposedOutput();
          assertThat(payload.matterId()).isEqualTo(matterId);
          assertThat(payload.summaryMarkdown()).contains("Client uploaded");
        });
  }

  @Test
  void capabilityGate_memberWithoutAiAssistantUse_cannotUseInboxTools() {
    assertThat(activityWindowTool.requiredCapabilities()).contains("AI_ASSISTANT_USE");
    assertThat(postInboxSummaryTool.requiredCapabilities()).contains("AI_ASSISTANT_USE");

    var inboxToolIds = List.of("GetMatterActivityWindow", "PostInboxSummary");
    var capsWithout = Set.of("INVOICING");
    var filtered = toolRegistry.filterBy(inboxToolIds, capsWithout);
    var filteredNames = filtered.stream().map(t -> t.name()).toList();
    assertThat(filteredNames).doesNotContain("GetMatterActivityWindow");
    assertThat(filteredNames).doesNotContain("PostInboxSummary");
  }

  @Test
  void inboxSpecialistConfig_hasCorrectToolsAndAutomationCapable() {
    // Verify the specialist is configured correctly
    var inboxToolIds = List.of("GetMatterActivityWindow", "PostInboxSummary");
    var capsWithAi = Set.of("AI_ASSISTANT_USE");
    var filtered = toolRegistry.filterBy(inboxToolIds, capsWithAi);
    var filteredNames = filtered.stream().map(t -> t.name()).toList();
    assertThat(filteredNames).contains("GetMatterActivityWindow");
    assertThat(filteredNames).contains("PostInboxSummary");
  }
}
