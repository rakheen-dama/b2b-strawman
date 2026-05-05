package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.write.PostInboxSummaryTool;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
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
 * Integration tests for PostInboxSummary DIRECT mode: auto-apply + deduplication within the same
 * hour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InboxSpecialistDirectModeIntegrationTest {

  private static final String ORG_ID = "org_inbox_direct_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private PostInboxSummaryTool postInboxSummaryTool;

  private String tenantSchema;
  private UUID memberId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Inbox Direct Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_inbox_direct_owner", "direct@test.com", "Direct Owner", "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a real project so the comment applier can find it
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inbox_direct_owner");
    var projectIdStr = TestEntityHelper.createProject(mockMvc, jwt, "Inbox Direct Project");
    projectId = UUID.fromString(projectIdStr);
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
  void directMode_postsCommentAndRecordsAutoApplied() {
    var sourceId = UUID.randomUUID();
    runWithCaps(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var ctx =
              new TenantToolContext(tenantSchema, memberId, "owner", Set.of("AI_ASSISTANT_USE"));
          var input =
              Map.<String, Object>of(
                  "matterId",
                  projectId.toString(),
                  "summaryMarkdown",
                  "## Direct Summary\n\nClient uploaded documents.",
                  "lookbackFrom",
                  Instant.now().minusSeconds(86400).toString(),
                  "lookbackTo",
                  Instant.now().toString(),
                  "sources",
                  List.of(Map.of("entityType", "comment", "entityId", sourceId.toString())),
                  "mode",
                  "DIRECT");

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) postInboxSummaryTool.execute(input, ctx);

          assertThat(result).containsKey("invocationId");
          assertThat(result.get("status")).isEqualTo("AUTO_APPLIED");

          var invId = UUID.fromString((String) result.get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.AUTO_APPLIED);
        });
  }

  @Test
  void directMode_dedupe_rejectsSecondCallWithinSameHour() {
    // Use a unique project for this test to avoid interference with directMode_postsComment test
    UUID dedupeProjectId;
    try {
      var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inbox_direct_owner");
      var idStr = TestEntityHelper.createProject(mockMvc, jwt, "Inbox Dedupe Project");
      dedupeProjectId = UUID.fromString(idStr);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create dedupe project", e);
    }

    var sourceId = UUID.randomUUID();
    final UUID finalDedupeProjectId = dedupeProjectId;
    runWithCaps(
        Set.of("AI_ASSISTANT_USE"),
        () -> {
          var ctx =
              new TenantToolContext(tenantSchema, memberId, "owner", Set.of("AI_ASSISTANT_USE"));

          // First call should succeed
          var input1 =
              Map.<String, Object>of(
                  "matterId",
                  finalDedupeProjectId.toString(),
                  "summaryMarkdown",
                  "## First Summary\n\nFirst posting.",
                  "lookbackFrom",
                  Instant.now().minusSeconds(86400).toString(),
                  "lookbackTo",
                  Instant.now().toString(),
                  "sources",
                  List.of(Map.of("entityType", "comment", "entityId", sourceId.toString())),
                  "mode",
                  "DIRECT");

          @SuppressWarnings("unchecked")
          var result1 = (Map<String, Object>) postInboxSummaryTool.execute(input1, ctx);
          assertThat(result1.get("status")).isEqualTo("AUTO_APPLIED");

          // Second call within the same hour should be rejected (dedupe)
          var input2 =
              Map.<String, Object>of(
                  "matterId",
                  finalDedupeProjectId.toString(),
                  "summaryMarkdown",
                  "## Second Summary\n\nDuplicate attempt.",
                  "lookbackFrom",
                  Instant.now().minusSeconds(86400).toString(),
                  "lookbackTo",
                  Instant.now().toString(),
                  "sources",
                  List.of(Map.of("entityType", "comment", "entityId", sourceId.toString())),
                  "mode",
                  "DIRECT");

          @SuppressWarnings("unchecked")
          var result2 = (Map<String, Object>) postInboxSummaryTool.execute(input2, ctx);
          assertThat(result2).containsKey("error");
          assertThat((String) result2.get("error")).containsIgnoringCase("duplicate");
          assertThat(result2).containsKey("existingInvocationId");
        });
  }
}
