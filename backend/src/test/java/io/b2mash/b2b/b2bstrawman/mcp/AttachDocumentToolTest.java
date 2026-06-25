package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.AttachDocumentConfirmResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.AttachDocumentInitResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceWriteTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 583A.3 — {@code attach_document} INITIATE/CONFIRM happy path, driven directly against the {@link
 * CorrespondenceWriteTools} bean with {@code RequestScopes} bound. Asserts the presigned URL on
 * INITIATE, the persisted {@code correspondence_id} + {@code source=EMAIL_INGEST} stamp on CONFIRM
 * (re-read from the repository to prove it flushed), CONFIRM idempotency, and the emitted {@code
 * mcp.write.document_attached} audit event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachDocumentToolTest {

  private static final String ORG_ID = "org_mcp_583_attach";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID matterId;
  private UUID clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 583 Attach Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Attach Matter"));
    clientId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Attach Client", "ac@test.com"));

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

  /** File a correspondence against the matter + client and return its id. */
  private UUID fileCorrespondence(String messageId) {
    var resp =
        asOwner(
            () ->
                (FileCorrespondenceToolResponse)
                    tools.fileCorrespondence(
                        matterId,
                        clientId,
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
  void initiateReturnsPresignedUrlAndDocumentId() {
    UUID correspondenceId = fileCorrespondence("<attach-init-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE",
                        correspondenceId,
                        "report.pdf",
                        "application/pdf",
                        1024L,
                        null));

    assertThat(init.documentId()).isNotNull();
    assertThat(init.presignedUrl()).isNotBlank().startsWith("http");
    assertThat(init.expiresInSeconds()).isPositive();
  }

  @Test
  void confirmStampsCorrespondenceAndSource() {
    UUID correspondenceId = fileCorrespondence("<attach-confirm-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE",
                        correspondenceId,
                        "filing.pdf",
                        "application/pdf",
                        2048L,
                        null));
    UUID documentId = init.documentId();

    var confirm =
        asOwner(
            () ->
                (AttachDocumentConfirmResponse)
                    tools.attachDocument(
                        "CONFIRM", correspondenceId, null, null, null, documentId));

    assertThat(confirm.documentId()).isEqualTo(documentId);
    assertThat(confirm.status()).isEqualTo("UPLOADED");
    assertThat(confirm.correspondenceId()).isEqualTo(correspondenceId);

    // Re-read from the repository to prove the stamp actually flushed (GOTCHA #2).
    Document persisted = inTenantTx(() -> documentRepository.findById(documentId).orElseThrow());
    assertThat(persisted.getStatus()).isEqualTo(Document.Status.UPLOADED);
    assertThat(persisted.getCorrespondenceId()).isEqualTo(correspondenceId);
    assertThat(persisted.getSource()).isEqualTo(Document.Source.EMAIL_INGEST);
  }

  @Test
  void confirmIsIdempotentPerDocumentId() {
    UUID correspondenceId = fileCorrespondence("<attach-idem-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE", correspondenceId, "again.pdf", "application/pdf", 512L, null));
    UUID documentId = init.documentId();

    asOwner(() -> tools.attachDocument("CONFIRM", correspondenceId, null, null, null, documentId));
    var second =
        asOwner(
            () ->
                (AttachDocumentConfirmResponse)
                    tools.attachDocument(
                        "CONFIRM", correspondenceId, null, null, null, documentId));

    assertThat(second.documentId()).isEqualTo(documentId);
    assertThat(second.status()).isEqualTo("UPLOADED");

    Document persisted = inTenantTx(() -> documentRepository.findById(documentId).orElseThrow());
    assertThat(persisted.getCorrespondenceId()).isEqualTo(correspondenceId);
    assertThat(persisted.getSource()).isEqualTo(Document.Source.EMAIL_INGEST);
  }

  @Test
  void confirmEmitsDocumentAttachedAudit() {
    UUID correspondenceId = fileCorrespondence("<attach-audit-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE", correspondenceId, "audited.pdf", "application/pdf", 64L, null));
    UUID documentId = init.documentId();

    asOwner(() -> tools.attachDocument("CONFIRM", correspondenceId, null, null, null, documentId));

    var events =
        readEvents("mcp.write.document_attached").stream()
            .filter(e -> documentId.equals(e.getEntityId()))
            .toList();
    assertThat(events).hasSize(1);
    var event = events.get(0);
    assertThat(event.getEntityType()).isEqualTo("document");
    assertThat(event.getDetails()).containsEntry("tool", "attach_document");

    @SuppressWarnings("unchecked")
    List<String> entityRefs = (List<String>) event.getDetails().get("entityRefs");
    assertThat(entityRefs).contains(documentId.toString(), correspondenceId.toString());
    // fileName is caller-controlled and can carry PII (POPIA), so it must NOT appear in the audit
    // params — the entityRefs identify the records instead.
    @SuppressWarnings("unchecked")
    var params = (java.util.Map<String, Object>) event.getDetails().get("params");
    if (params != null) {
      assertThat(params).doesNotContainKey("fileName");
    }
  }

  @Test
  void confirmRetryDoesNotEmitDuplicateAudit() {
    UUID correspondenceId = fileCorrespondence("<attach-audit-retry-1@mail.test>");

    var init =
        asOwner(
            () ->
                (AttachDocumentInitResponse)
                    tools.attachDocument(
                        "INITIATE", correspondenceId, "retry.pdf", "application/pdf", 32L, null));
    UUID documentId = init.documentId();

    // First confirm performs the real state change → exactly one audit event.
    asOwner(() -> tools.attachDocument("CONFIRM", correspondenceId, null, null, null, documentId));
    var afterFirst =
        readEvents("mcp.write.document_attached").stream()
            .filter(e -> documentId.equals(e.getEntityId()))
            .toList();
    assertThat(afterFirst).hasSize(1);

    // Second (retry) confirm is an idempotent no-op → success but NO additional audit event.
    var second =
        asOwner(
            () ->
                (AttachDocumentConfirmResponse)
                    tools.attachDocument(
                        "CONFIRM", correspondenceId, null, null, null, documentId));
    assertThat(second.documentId()).isEqualTo(documentId);
    assertThat(second.status()).isEqualTo("UPLOADED");

    var afterSecond =
        readEvents("mcp.write.document_attached").stream()
            .filter(e -> documentId.equals(e.getEntityId()))
            .toList();
    assertThat(afterSecond).hasSize(1);
  }
}
