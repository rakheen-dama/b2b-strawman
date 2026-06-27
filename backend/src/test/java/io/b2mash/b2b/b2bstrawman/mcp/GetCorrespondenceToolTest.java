package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpCorrespondenceDto;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceReadTools;
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
 * Epic 587B.4 — {@code get_correspondence} body read-back, the three identical-message {@code
 * not_found} sub-cases, and the denial-emission asymmetry (emitted ONLY on found-but-refused, NOT
 * on absent/cross-tenant), plus POPIA safe-refs-only audit (entityRef=id only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetCorrespondenceToolTest {

  private static final String ORG_ID = "org_mcp_587b_get";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceReadTools tools;
  @Autowired private CorrespondenceWriteTools writeTools;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID nonMemberMemberId;
  private UUID matterId;
  private UUID customerId;
  private UUID matterCorrId;
  private UUID customerCorrId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 587B Get Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    nonMemberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Matter For Get"));
    customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Client Co", "client@get.test"));
    enableMcp();
    matterCorrId =
        seed(matterId, null, "<get-m1@mail.test>", "Matter body subject", "from@matter.co");
    customerCorrId =
        seed(null, customerId, "<get-c1@mail.test>", "Customer body subject", "from@cust.co");
  }

  @Test
  void returnsBodyAndHeadersForViewableMatterCorrespondence() {
    McpCorrespondenceDto dto =
        asOwner(() -> (McpCorrespondenceDto) tools.getCorrespondence(matterCorrId));
    assertThat(dto.id()).isEqualTo(matterCorrId);
    assertThat(dto.bodyText()).isEqualTo("body-Matter body subject");
    assertThat(dto.subject()).isEqualTo("Matter body subject");
    assertThat(dto.fromAddress()).isEqualTo("from@matter.co");
    assertThat(dto.messageId()).isEqualTo("<get-m1@mail.test>");
    assertThat(dto.direction()).isEqualTo("INBOUND");
    assertThat(dto.projectId()).isEqualTo(matterId);
  }

  @Test
  void fabricatedIdIsNotFoundWithoutDenial() {
    UUID fake = UUID.randomUUID();
    long deniedBefore = countDenied();
    CallToolResult ctr = asOwner(() -> (CallToolResult) tools.getCorrespondence(fake));
    assertThat(errorCode(ctr)).isEqualTo("not_found");
    assertThat(countDenied()).isEqualTo(deniedBefore);
  }

  @Test
  void customerOnlyCorrespondenceResolvesOnExistence() {
    McpCorrespondenceDto dto =
        asOwner(() -> (McpCorrespondenceDto) tools.getCorrespondence(customerCorrId));
    assertThat(dto.id()).isEqualTo(customerCorrId);
    assertThat(dto.customerId()).isEqualTo(customerId);
    assertThat(dto.projectId()).isNull();
    assertThat(dto.bodyText()).isEqualTo("body-Customer body subject");
  }

  @Test
  void nonMemberOnMatterCorrespondenceIsNotFoundPlusDeniedAudit() {
    long deniedBefore = countDenied();
    CallToolResult ctr = asMember(() -> (CallToolResult) tools.getCorrespondence(matterCorrId));
    assertThat(errorCode(ctr)).isEqualTo("not_found");
    // Found-but-refused → the ONLY path that emits mcp.access.denied.
    assertThat(countDenied()).isGreaterThan(deniedBefore);
    var denied =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "get_correspondence".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(denied)
        .anySatisfy(e -> assertThat(e.getDetails().get("deniedGate")).isEqualTo("project-access"));
  }

  @Test
  void mcpDisabledReturnsNotEnabled() {
    revokeMcp();
    try {
      CallToolResult ctr = asOwner(() -> (CallToolResult) tools.getCorrespondence(matterCorrId));
      assertThat(errorCode(ctr)).isEqualTo("not_enabled");
    } finally {
      enableMcp();
    }
  }

  @Test
  void invokedAuditCarriesEntityRefOnlyNoPii() {
    asOwner(() -> tools.getCorrespondence(matterCorrId));
    var invoked =
        readEvents("mcp.tool.invoked").stream()
            .filter(e -> "get_correspondence".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(invoked).isNotEmpty();
    assertThat(invoked)
        .anySatisfy(
            event -> {
              assertThat(event.getEntityType()).isEqualTo("mcp_tool");
              assertThat(event.getDetails().get("rowCount")).isNotNull();
              String details = event.getDetails().toString();
              assertThat(details).doesNotContain("Matter body subject");
              assertThat(details).doesNotContain("from@matter.co");
              assertThat(details).doesNotContain("body-");
            });
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private UUID seed(UUID matter, UUID customer, String messageId, String subject, String from) {
    UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(
            () -> {
              var resp =
                  (FileCorrespondenceToolResponse)
                      writeTools.fileCorrespondence(
                          matter,
                          customer,
                          messageId,
                          subject,
                          "body-" + subject,
                          null,
                          from,
                          null,
                          null,
                          null,
                          null,
                          null,
                          null);
              assertThat(resp.idempotent()).isFalse();
              holder[0] = resp.correspondenceId();
            });
    return holder[0];
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

  private void revokeMcp() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> enablementService.revoke()));
  }

  private <T> T asOwner(java.util.concurrent.Callable<T> body) {
    return runAs(ownerMemberId, "owner", body);
  }

  private <T> T asMember(java.util.concurrent.Callable<T> body) {
    return runAs(nonMemberMemberId, "member", body);
  }

  private <T> T runAs(UUID memberId, String role, java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, role)
        .where(RequestScopes.CAPABILITIES, Set.of())
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

  private long countDenied() {
    return readEvents("mcp.access.denied").stream()
        .filter(e -> "get_correspondence".equals(e.getDetails().get("tool")))
        .count();
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

  private String errorCode(CallToolResult ctr) {
    String text = ((TextContent) ctr.content().get(0)).text();
    return objectMapper.readTree(text).get("error").asString();
  }
}
