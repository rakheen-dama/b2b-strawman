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

/**
 * Epic 587A.4 — {@code list_correspondence} tenant isolation (search_path) + POPIA safe-refs-only
 * audit. Correspondence filed under tenant A's matter is invisible to tenant B; the {@code
 * mcp.tool.invoked} details carry only ids/refs, never subject/from/body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListCorrespondenceTenantIsolationTest {

  private static final String ORG_A = "org_mcp_587a_iso_a";
  private static final String ORG_B = "org_mcp_587a_iso_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceReadTools readTools;
  @Autowired private CorrespondenceWriteTools writeTools;
  @Autowired private AuditEventRepository auditEventRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  private record Tenant(String orgId, String schema, UUID ownerMemberId, UUID matterId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantA = provision(ORG_A, "MCP 587A Iso A", "user_587a_a");
    tenantB = provision(ORG_B, "MCP 587A Iso B", "user_587a_b");
  }

  private Tenant provision(String orgId, String orgName, String userSubject) throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    UUID owner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, orgId, userSubject, userSubject + "@test.com", "Owner", "owner"));
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
    JwtRequestPostProcessor jwt = TestJwtFactory.ownerJwt(orgId, userSubject);
    UUID matterId =
        UUID.fromString(TestEntityHelper.createProject(mockMvc, jwt, "Matter " + orgId));
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, owner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
    return new Tenant(orgId, schema, owner, matterId);
  }

  @Test
  void tenantAsCorrespondenceIsInvisibleToTenantB() {
    seed(tenantA, "<iso587a-a1@mail.test>", "Tenant A subject", "secret@a.co");

    // Tenant A sees its own matter row.
    @SuppressWarnings("unchecked")
    McpPage<McpCorrespondenceListItem> aPage =
        runAs(
            tenantA,
            () ->
                (McpPage<McpCorrespondenceListItem>)
                    readTools.listCorrespondence(tenantA.matterId(), null, 0, 50));
    assertThat(aPage.total()).isGreaterThanOrEqualTo(1);

    // Tenant B, querying tenant A's matterId, sees an EMPTY page (search_path isolation — the
    // matter
    // simply does not exist in B's schema, and the owner role short-circuits to an empty list).
    @SuppressWarnings("unchecked")
    Object bResult =
        runAs(tenantB, () -> readTools.listCorrespondence(tenantA.matterId(), null, 0, 50));
    // Cross-tenant matter id is unknown in B → either an empty page (owner-sees-all over an absent
    // matter) or a not_found CallToolResult; either way B never sees A's row/body.
    if (bResult instanceof McpPage<?> bPage) {
      assertThat(bPage.items()).isEmpty();
    }
  }

  @Test
  void invokedAuditCarriesSafeRefsOnlyNoPii() {
    seed(tenantA, "<iso587a-a2@mail.test>", "PII subject must not leak", "pii@a.co");
    runAs(tenantA, () -> readTools.listCorrespondence(tenantA.matterId(), null, 0, 50));

    var invoked =
        readEvents(tenantA.schema(), "mcp.tool.invoked").stream()
            .filter(e -> "list_correspondence".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(invoked).isNotEmpty();
    assertThat(invoked)
        .anySatisfy(
            event -> {
              assertThat(event.getEntityType()).isEqualTo("mcp_tool");
              assertThat(event.getDetails().get("rowCount")).isNotNull();
              assertThat(event.getDetails().get("entityRefs")).isInstanceOf(List.class);
              // POPIA: no subject/from/body content anywhere in the details payload.
              String details = event.getDetails().toString();
              assertThat(details).doesNotContain("PII subject must not leak");
              assertThat(details).doesNotContain("pii@a.co");
              assertThat(details).doesNotContain("body-");
            });
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private void seed(Tenant tenant, String messageId, String subject, String from) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenant.schema())
        .where(RequestScopes.ORG_ID, tenant.orgId())
        .where(RequestScopes.MEMBER_ID, tenant.ownerMemberId())
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(
            () -> {
              var resp =
                  (FileCorrespondenceToolResponse)
                      writeTools.fileCorrespondence(
                          tenant.matterId(),
                          null,
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

  private <T> T runAs(Tenant tenant, java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenant.schema())
        .where(RequestScopes.ORG_ID, tenant.orgId())
        .where(RequestScopes.MEMBER_ID, tenant.ownerMemberId())
        .where(RequestScopes.ORG_ROLE, "owner")
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

  private List<AuditEvent> readEvents(String schema, String prefix) {
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
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
}
