package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpCorrespondenceListItem;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpPage;
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
 * Epic 587A.3 — {@code list_correspondence} behaviour: McpPage shape, size clamp, pagination, the
 * exactly-one-of-id validation, the two distinct {@code not_found} paths (matter-path view-access
 * refusal vs customer-path lookup miss) and the denial-emission asymmetry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListCorrespondenceToolTest {

  private static final String ORG_ID = "org_mcp_587a_list";

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
  private UUID matterIdEmpty;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 587A List Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    // A plain org:member who is NOT a project member of `matterId` → cannot view it.
    nonMemberMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Matter With Corr"));
    matterIdEmpty = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Empty Matter"));
    customerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Acme Co", "acme@client.test"));

    enableMcp();

    // Seed 3 matter-scoped rows + 1 customer-scoped row via the write tool (owner scopes).
    seed(matterId, null, "<m1@mail.test>", "Matter subject 1", "a@b.co");
    seed(matterId, null, "<m2@mail.test>", "Matter subject 2", "a@b.co");
    seed(matterId, null, "<m3@mail.test>", "Matter subject 3", "a@b.co");
    seed(null, customerId, "<c1@mail.test>", "Customer subject 1", "x@y.co");
  }

  @Test
  void matterPathReturnsRowsAsMcpPage() {
    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> page =
        asOwner(
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    tools.listCorrespondence(matterId, null, 0, 50));
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.items()).hasSize(3);
    assertThat(page.truncated()).isFalse();
    // Newest-first: the most recently filed row leads (received/filed DESC).
    assertThat(page.items().get(0).subject()).isEqualTo("Matter subject 3");
    assertThat(page.items()).allSatisfy(i -> assertThat(i.direction()).isEqualTo("INBOUND"));
  }

  @Test
  void sizeIsClampedToFifty() {
    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> page =
        asOwner(
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    tools.listCorrespondence(matterId, null, 0, 999));
    assertThat(page.size()).isEqualTo(50);
  }

  @Test
  void paginationHonoursPageIndexAndHasNext() {
    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> page0 =
        asOwner(
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    tools.listCorrespondence(matterId, null, 0, 2));
    assertThat(page0.items()).hasSize(2);
    assertThat(page0.page()).isEqualTo(0);
    assertThat(page0.total()).isEqualTo(3);
    assertThat(page0.truncated()).isTrue();

    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> page1 =
        asOwner(
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    tools.listCorrespondence(matterId, null, 1, 2));
    assertThat(page1.items()).hasSize(1);
    assertThat(page1.page()).isEqualTo(1);
    assertThat(page1.truncated()).isFalse();
  }

  @Test
  void neitherIdIsInvalidRequest() {
    CallToolResult ctr =
        asOwner(() -> (CallToolResult) tools.listCorrespondence(null, null, 0, 50));
    assertThat(errorCode(ctr)).isEqualTo("invalid_request");
  }

  @Test
  void bothIdsIsInvalidRequest() {
    CallToolResult ctr =
        asOwner(() -> (CallToolResult) tools.listCorrespondence(matterId, customerId, 0, 50));
    assertThat(errorCode(ctr)).isEqualTo("invalid_request");
  }

  @Test
  void matterPathViewAccessDenialIsNotFoundPlusDeniedAudit() {
    // A plain org:member who is not a member of `matterId` cannot view it → obscurity-404 → denial.
    CallToolResult ctr =
        asMember(() -> (CallToolResult) tools.listCorrespondence(matterId, null, 0, 50));
    assertThat(errorCode(ctr)).isEqualTo("not_found");

    var denied =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "list_correspondence".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(denied).isNotEmpty();
    assertThat(denied)
        .anySatisfy(e -> assertThat(e.getDetails().get("deniedGate")).isEqualTo("project-access"));
  }

  @Test
  void customerPathListsKnownAndUnknownIdIsNotFoundWithoutDenial() {
    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> page =
        asOwner(
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    tools.listCorrespondence(null, customerId, 0, 50));
    assertThat(page.total()).isEqualTo(1);

    UUID unknown = UUID.randomUUID();
    long deniedBefore =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "list_correspondence".equals(e.getDetails().get("tool")))
            .count();
    CallToolResult ctr =
        asOwner(() -> (CallToolResult) tools.listCorrespondence(null, unknown, 0, 50));
    assertThat(errorCode(ctr)).isEqualTo("not_found");
    long deniedAfter =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "list_correspondence".equals(e.getDetails().get("tool")))
            .count();
    // A customer lookup miss must NOT emit a policy denial.
    assertThat(deniedAfter).isEqualTo(deniedBefore);
  }

  @Test
  void mcpDisabledReturnsNotEnabled() {
    revokeMcp();
    try {
      CallToolResult ctr =
          asOwner(() -> (CallToolResult) tools.listCorrespondence(matterId, null, 0, 50));
      assertThat(errorCode(ctr)).isEqualTo("not_enabled");
    } finally {
      enableMcp();
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private void seed(UUID matter, UUID customer, String messageId, String subject, String from) {
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
            });
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
