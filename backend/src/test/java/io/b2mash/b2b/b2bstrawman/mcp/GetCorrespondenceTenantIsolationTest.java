package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
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
 * Epic 587B.4 — {@code get_correspondence} tenant isolation. Tenant A's correspondence id is {@code
 * not_found} for tenant B (search_path isolation), the body never crosses the boundary, and the
 * cross-tenant miss emits NO {@code mcp.access.denied} (a lookup miss is not a policy denial).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetCorrespondenceTenantIsolationTest {

  private static final String ORG_A = "org_mcp_587b_iso_a";
  private static final String ORG_B = "org_mcp_587b_iso_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceReadTools readTools;
  @Autowired private CorrespondenceWriteTools writeTools;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private Tenant tenantA;
  private Tenant tenantB;
  private UUID tenantACorrId;

  private record Tenant(String orgId, String schema, UUID ownerMemberId, UUID matterId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantA = provision(ORG_A, "MCP 587B Iso A", "user_587b_a");
    tenantB = provision(ORG_B, "MCP 587B Iso B", "user_587b_b");
    tenantACorrId = seed(tenantA, "<iso587b-a1@mail.test>", "Tenant A get subject", "secret@a.co");
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
  void tenantBCannotGetTenantAsCorrespondence() {
    long deniedBefore = countDenied(tenantB.schema());
    CallToolResult ctr =
        runAs(tenantB, () -> (CallToolResult) readTools.getCorrespondence(tenantACorrId));
    // Cross-tenant id → not_found, never the body.
    assertThat(errorCode(ctr)).isEqualTo("not_found");
    String text = ((TextContent) ctr.content().get(0)).text();
    assertThat(text).doesNotContain("body-");
    assertThat(text).doesNotContain("Tenant A get subject");
    // A cross-tenant miss is not a policy denial.
    assertThat(countDenied(tenantB.schema())).isEqualTo(deniedBefore);
  }

  @Test
  void tenantASeesItsOwnCorrespondence() {
    Object dto = runAs(tenantA, () -> readTools.getCorrespondence(tenantACorrId));
    // Tenant A resolves its own row to a DTO (not a CallToolResult error).
    assertThat(dto).isNotInstanceOf(CallToolResult.class);
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private UUID seed(Tenant tenant, String messageId, String subject, String from) {
    UUID[] holder = new UUID[1];
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
              holder[0] = resp.correspondenceId();
            });
    return holder[0];
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

  private long countDenied(String schema) {
    return readEvents(schema, "mcp.access.denied").stream()
        .filter(e -> "get_correspondence".equals(e.getDetails().get("tool")))
        .count();
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

  private String errorCode(CallToolResult ctr) {
    String text = ((TextContent) ctr.content().get(0)).text();
    return objectMapper.readTree(text).get("error").asString();
  }
}
