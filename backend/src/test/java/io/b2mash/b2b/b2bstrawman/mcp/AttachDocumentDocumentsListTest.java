package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.mcp.dto.AttachDocumentInitResponse;
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
 * 583A.4 — the attached document is a first-class {@link Document}, so it surfaces in the matter's
 * existing documents list; and a read-only MCP user (no {@code MCP_WRITE}) is rejected on {@code
 * attach_document} with a non-leaking {@code forbidden} + {@code mcp.access.denied} audit carrying
 * {@code deniedGate=MCP_WRITE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachDocumentDocumentsListTest {

  private static final String ORG_ID = "org_mcp_583_list";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private DocumentService documentService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 583 List Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    memberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "List Matter"));

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

  /** File a PROJECT-only correspondence (no customer) and return its id. */
  private UUID fileProjectCorrespondence(String messageId) {
    var resp =
        asOwner(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId,
                        null,
                        messageId,
                        "Subject",
                        "Body",
                        null,
                        "from@acme.co.za",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    return resp.correspondenceId();
  }

  @Test
  void confirmedAttachmentAppearsInMatterDocumentsList() {
    UUID correspondenceId = fileProjectCorrespondence("<list-attach-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE", correspondenceId, "listed.pdf", "application/pdf", 128L, null));
    UUID documentId = init.documentId();
    asOwner(() -> tools.attachDocument("CONFIRM", correspondenceId, null, null, null, documentId));

    var actor = new io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext(ownerMemberId, "owner");
    List<Document> docs = inTenantTx(() -> documentService.listDocuments(matterId, actor));
    assertThat(docs).extracting(Document::getId).contains(documentId);
    assertThat(docs)
        .filteredOn(d -> documentId.equals(d.getId()))
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.getCorrespondenceId()).isEqualTo(correspondenceId);
              assertThat(d.getSource()).isEqualTo(Document.Source.EMAIL_INGEST);
            });
  }

  @Test
  void readOnlyUserWithoutMcpWriteIsForbiddenAndAudited() {
    UUID correspondenceId = fileProjectCorrespondence("<list-gate-1@mail.test>");

    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberMemberId)
        .where(RequestScopes.ORG_ROLE, "member")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS"))
        .run(
            () ->
                holder[0] =
                    tools.attachDocument(
                        "INITIATE", correspondenceId, "denied.pdf", "application/pdf", 16L, null));

    assertThat(holder[0]).isInstanceOf(CallToolResult.class);
    var ctr = (CallToolResult) holder[0];
    assertThat(ctr.isError()).isTrue();
    assertThat(errorCode(ctr)).isEqualTo("forbidden");

    var denied =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "attach_document".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(denied).isNotEmpty();
    assertThat(denied.get(0).getDetails()).containsEntry("deniedGate", "MCP_WRITE");
  }

  private String errorCode(CallToolResult ctr) {
    String text = ((TextContent) ctr.content().get(0)).text();
    return objectMapper.readTree(text).get("error").asString();
  }
}
