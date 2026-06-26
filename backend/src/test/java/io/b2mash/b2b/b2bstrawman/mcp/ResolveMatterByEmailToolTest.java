package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpMatterDto;
import io.b2mash.b2b.b2bstrawman.mcp.dto.ResolveMatterResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.ClientTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
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
 * Epic 584A — {@code resolve_matter_by_email} read tool on {@link ClientTools}. Asserts the read
 * shape (match / no-match / multi-matter), the {@code mcp.tool.invoked} read-audit family (never
 * {@code mcp.write.*}), and that the tool is gated on {@code MCP_ACCESS} (not {@code MCP_WRITE}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResolveMatterByEmailToolTest {

  private static final String ORG_ID = "org_mcp_584_resolve";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ClientTools tools;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;

  // A customer with exactly one linked matter.
  private static final String SINGLE_EMAIL = "single@acme.co.za";
  private UUID singleCustomerId;
  private UUID singleMatterId;

  // A customer with two linked matters.
  private static final String MULTI_EMAIL = "multi@beta.co.za";
  private UUID multiCustomerId;
  private UUID multiMatterAId;
  private UUID multiMatterBId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 584 Resolve Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");

    // Single-matter customer. Linking a project requires a non-PROSPECT customer (the lifecycle
    // guard blocks CREATE_PROJECT on PROSPECT), so transition each customer to ACTIVE first.
    singleCustomerId =
        UUID.fromString(
            TestEntityHelper.createCustomer(mockMvc, owner, "Acme Single", SINGLE_EMAIL));
    transitionToActive(owner, singleCustomerId);
    singleMatterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Acme Matter"));
    linkProject(owner, singleCustomerId, singleMatterId);

    // Multi-matter customer.
    multiCustomerId =
        UUID.fromString(TestEntityHelper.createCustomer(mockMvc, owner, "Beta Multi", MULTI_EMAIL));
    transitionToActive(owner, multiCustomerId);
    multiMatterAId =
        UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Beta Matter A"));
    multiMatterBId =
        UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Beta Matter B"));
    linkProject(owner, multiCustomerId, multiMatterAId);
    linkProject(owner, multiCustomerId, multiMatterBId);

    enableMcp();
  }

  // Transition PROSPECT -> ONBOARDING -> ACTIVE so the customer can have matters linked.
  // TestChecklistHelper.transitionToActive preserves the customer's email (findByEmail still
  // works).
  private void transitionToActive(JwtRequestPostProcessor jwt, UUID customerId) throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/customers/{id}/transition", customerId)
                .with(jwt)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    TestChecklistHelper.transitionToActive(mockMvc, customerId.toString(), jwt);
  }

  // Link a project to a customer via the join-table API (POST, no body). Must succeed (201).
  private void linkProject(JwtRequestPostProcessor jwt, UUID customerId, UUID projectId)
      throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/customers/{id}/projects/{projectId}", customerId, projectId)
                .with(jwt))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
  }

  // MCP must be enabled (popia-egress consent) for any tool to run past effectiveState().
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

  // Bind RequestScopes (incl. CAPABILITIES) and run the tool body.
  private <T> T runWithCapabilities(
      Set<String> capabilities, java.util.concurrent.Callable<T> body) {
    @SuppressWarnings("unchecked")
    T[] holder = (T[]) new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, capabilities)
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

  // Read-only callers hold MCP_ACCESS but NOT MCP_WRITE — proves the tool is a read tool.
  private <T> T asReadOnly(java.util.concurrent.Callable<T> body) {
    return runWithCapabilities(Set.of("MCP_ACCESS"), body);
  }

  // Read audit events by eventType prefix, in the tenant schema.
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

  @Test
  void matchReturnsCustomerAndMatters() {
    var result = asReadOnly(() -> tools.resolveMatterByEmail(SINGLE_EMAIL, null, null));

    assertThat(result).isInstanceOf(ResolveMatterResponse.class);
    var response = (ResolveMatterResponse) result;
    assertThat(response.customer()).isNotNull();
    assertThat(response.customer().id()).isEqualTo(singleCustomerId);
    assertThat(response.matters()).extracting(McpMatterDto::id).contains(singleMatterId);
  }

  @Test
  void noMatchReturnsNullCustomerAndEmptyMatters() {
    var result = asReadOnly(() -> tools.resolveMatterByEmail("nobody@nowhere.example", null, null));

    assertThat(result).isInstanceOf(ResolveMatterResponse.class);
    var response = (ResolveMatterResponse) result;
    assertThat(response.customer()).isNull();
    assertThat(response.matters()).isEmpty();
  }

  @Test
  void multiMatterCustomerReturnsAllMatters() {
    var result =
        asReadOnly(() -> tools.resolveMatterByEmail(MULTI_EMAIL, "Re: hearing", "REF-001"));

    assertThat(result).isInstanceOf(ResolveMatterResponse.class);
    var response = (ResolveMatterResponse) result;
    assertThat(response.customer()).isNotNull();
    assertThat(response.customer().id()).isEqualTo(multiCustomerId);
    assertThat(response.matters())
        .extracting(McpMatterDto::id)
        .contains(multiMatterAId, multiMatterBId);
  }

  @Test
  void emitsReadAuditFamilyNeverWriteFamily() {
    asReadOnly(() -> tools.resolveMatterByEmail(SINGLE_EMAIL, null, null));

    var invoked =
        readEvents("mcp.tool.invoked").stream()
            .filter(e -> "resolve_matter_by_email".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(invoked).isNotEmpty();
    assertThat(invoked)
        .anySatisfy(
            event -> {
              assertThat(event.getEntityType()).isEqualTo("mcp_tool");
              assertThat(event.getDetails().get("rowCount")).isNotNull();
              assertThat(event.getDetails().get("entityRefs")).isInstanceOf(List.class);
            });

    // A read tool must NEVER emit any write-family audit event.
    var writeEvents =
        readEvents("mcp.write.").stream()
            .filter(e -> "resolve_matter_by_email".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(writeEvents).isEmpty();
  }

  @Test
  void requiresMcpAccessNotMcpWrite() {
    // A caller holding MCP_ACCESS (no MCP_WRITE) succeeds — proves this is a read tool.
    var allowed = asReadOnly(() -> tools.resolveMatterByEmail(SINGLE_EMAIL, null, null));
    assertThat(allowed).isInstanceOf(ResolveMatterResponse.class);

    // A caller WITHOUT MCP_ACCESS is forbidden and triggers the mcp.access.denied audit.
    var denied =
        runWithCapabilities(
            Set.of("MCP_WRITE"), // holds an unrelated capability, but NOT MCP_ACCESS
            () -> tools.resolveMatterByEmail(SINGLE_EMAIL, null, null));
    assertThat(denied).isInstanceOf(CallToolResult.class);
    var ctr = (CallToolResult) denied;
    assertThat(ctr.isError()).isTrue();
    assertThat(errorCode(ctr)).isEqualTo("forbidden");

    var deniedEvents =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "resolve_matter_by_email".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(deniedEvents).isNotEmpty();
    assertThat(deniedEvents.get(0).getDetails()).containsEntry("deniedGate", "MCP_ACCESS");
  }

  private String errorCode(CallToolResult ctr) {
    String text = ((TextContent) ctr.content().get(0)).text();
    return objectMapper.readTree(text).get("error").asString();
  }
}
