package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.correspondence.CorrespondenceRepository;
import io.b2mash.b2b.b2bstrawman.correspondence.Direction;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceWriteTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 582B.3 — {@code file_correspondence} happy-path + idempotency, driven directly against the {@link
 * CorrespondenceWriteTools} bean with {@code RequestScopes} bound. Asserts the persisted {@code
 * Correspondence} row and the emitted {@code mcp.write.*} audit event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorrespondenceWriteToolsTest {

  private static final String ORG_ID = "org_mcp_582_write";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private CorrespondenceRepository correspondenceRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID matterId;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 582 Write Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Write Matter"));
    clientId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Write Client", "wc@test.com"));

    enableMcp();
  }

  private void enableMcp() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
  }

  private <T> T asOwner(java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(
            () -> {
              try {
                holder[0] = body.call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    return holder[0];
  }

  private List<AuditEvent> readEvents(String prefix) {
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    transactionTemplate.execute(
                        tx ->
                            auditEventRepository
                                .findByFilter(
                                    null, null, null, prefix, null, null, PageRequest.of(0, 200))
                                .getContent()));
    return holder[0];
  }

  private <T> T inTenantTx(java.util.function.Supplier<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> holder[0] = transactionTemplate.execute(tx -> body.get()));
    return holder[0];
  }

  @Test
  void fileCreatesRowAndEmitsFiledAudit() {
    String messageId = "<file-create-1@mail.test>";
    var response =
        asOwner(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId,
                        clientId,
                        messageId,
                        "Hello",
                        "Body text",
                        null,
                        "jane@acme.co.za",
                        List.of("attorney@firm.co.za"),
                        null,
                        null,
                        null,
                        null,
                        null));

    assertThat(response.idempotent()).isFalse();
    assertThat(response.correspondenceId()).isNotNull();

    var row = inTenantTx(() -> correspondenceRepository.findByMessageId(messageId).orElseThrow());
    assertThat(row.getId()).isEqualTo(response.correspondenceId());
    assertThat(row.getDirection()).isEqualTo(Direction.INBOUND);
    assertThat(row.getProjectId()).isEqualTo(matterId);
    assertThat(row.getCustomerId()).isEqualTo(clientId);
    assertThat(row.getFiledByMemberId()).isEqualTo(ownerMemberId);

    var filed =
        readEvents("mcp.write.correspondence_filed").stream()
            .filter(e -> response.correspondenceId().equals(e.getEntityId()))
            .toList();
    assertThat(filed).hasSize(1);
    assertThat(filed.get(0).getEntityType()).isEqualTo("correspondence");
    assertThat(filed.get(0).getDetails()).containsEntry("tool", "file_correspondence");
  }

  @Test
  void refileSameMessageIdReturnsIdempotentAndEmitsRefiledAudit() {
    String messageId = "<refile-1@mail.test>";

    var first =
        asOwner(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId, null, messageId, "Subj", null, null, "a@b.co", null, null, null,
                        null, null, null));
    assertThat(first.idempotent()).isFalse();

    var second =
        asOwner(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId, null, messageId, "Subj", null, null, "a@b.co", null, null, null,
                        null, null, null));
    assertThat(second.idempotent()).isTrue();
    assertThat(second.correspondenceId()).isEqualTo(first.correspondenceId());

    boolean present =
        inTenantTx(() -> correspondenceRepository.findByMessageId(messageId).isPresent());
    assertThat(present).isTrue();

    var refiled =
        readEvents("mcp.write.correspondence_refiled").stream()
            .filter(e -> second.correspondenceId().equals(e.getEntityId()))
            .toList();
    assertThat(refiled).hasSize(1);
  }

  @Test
  void bothNullLinkageReturnsInvalidRequest() {
    var result =
        asOwner(
            () ->
                tools.fileCorrespondence(
                    null,
                    null,
                    "<both-null-1@mail.test>",
                    "Subj",
                    null,
                    null,
                    "a@b.co",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));

    assertThat(result).isInstanceOf(CallToolResult.class);
    var ctr = (CallToolResult) result;
    assertThat(ctr.isError()).isTrue();
    assertThat(errorCode(ctr)).isEqualTo("invalid_request");
  }

  @Test
  void notEnabledTenantReturnsNotEnabled() throws Exception {
    String otherOrg = "org_mcp_582_disabled";
    provisioningService.provisionTenant(otherOrg, "MCP 582 Disabled Org", null);
    UUID otherMember =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, otherOrg, "user_owner2", "o2@test.com", "Owner2", "owner"));
    String otherSchema =
        orgSchemaMappingRepository.findByClerkOrgId(otherOrg).orElseThrow().getSchemaName();

    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, otherSchema)
        .where(RequestScopes.ORG_ID, otherOrg)
        .where(RequestScopes.MEMBER_ID, otherMember)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(
            () ->
                holder[0] =
                    tools.fileCorrespondence(
                        null,
                        null,
                        "<disabled-1@mail.test>",
                        "Subj",
                        null,
                        null,
                        "a@b.co",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

    assertThat(holder[0]).isInstanceOf(CallToolResult.class);
    var ctr = (CallToolResult) holder[0];
    assertThat(ctr.isError()).isTrue();
    assertThat(errorCode(ctr)).isEqualTo("not_enabled");
  }

  private String errorCode(CallToolResult ctr) {
    String text = ((TextContent) ctr.content().get(0)).text();
    return objectMapper.readTree(text).get("error").asString();
  }
}
