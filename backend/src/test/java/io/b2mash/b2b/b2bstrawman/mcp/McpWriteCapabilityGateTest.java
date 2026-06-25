package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.mcp.dto.FileCorrespondenceToolResponse;
import io.b2mash.b2b.b2bstrawman.mcp.tool.CorrespondenceWriteTools;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 582B.4 — the MCP write-capability gate. Establishes the cross-cutting invariant reused by 583A /
 * 585B: a member holding {@code MCP_ACCESS} but NOT {@code MCP_WRITE} is rejected on {@code
 * file_correspondence} (non-leaking {@code forbidden} + {@code mcp.access.denied} carrying {@code
 * deniedGate=MCP_WRITE}); a member holding {@code MCP_WRITE} succeeds; and {@code MCP_WRITE} is
 * auto-granted to owner/admin but NOT to a custom role that only declares {@code MCP_ACCESS}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpWriteCapabilityGateTest {

  private static final String ORG_ID = "org_mcp_582_gate";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CorrespondenceWriteTools tools;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private ObjectMapper objectMapper;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID memberMemberId;
  private UUID matterId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 582 Gate Org", null);
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
    matterId = UUID.fromString(TestEntityHelper.createProject(mockMvc, owner, "Gate Matter"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
  }

  @Test
  void readOnlyUserWithoutMcpWriteIsForbiddenAndAudited() {
    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];
    // A read-only MCP user: has MCP_ACCESS, lacks MCP_WRITE.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberMemberId)
        .where(RequestScopes.ORG_ROLE, "member")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS"))
        .run(
            () ->
                holder[0] =
                    tools.fileCorrespondence(
                        matterId,
                        null,
                        "<gate-denied-1@mail.test>",
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
    assertThat(errorCode(ctr)).isEqualTo("forbidden");

    var denied =
        readEvents("mcp.access.denied").stream()
            .filter(e -> "file_correspondence".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(denied).isNotEmpty();
    assertThat(denied.get(0).getDetails()).containsEntry("deniedGate", "MCP_WRITE");
  }

  @Test
  void userWithMcpWriteSucceeds() {
    @SuppressWarnings("unchecked")
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberMemberId)
        .where(RequestScopes.ORG_ROLE, "member")
        .where(RequestScopes.CAPABILITIES, Set.of("MCP_ACCESS", "MCP_WRITE"))
        .run(
            () ->
                holder[0] =
                    tools.fileCorrespondence(
                        matterId,
                        null,
                        "<gate-allowed-1@mail.test>",
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

    assertThat(holder[0]).isInstanceOf(FileCorrespondenceToolResponse.class);
    var response = (FileCorrespondenceToolResponse) holder[0];
    assertThat(response.idempotent()).isFalse();
    assertThat(response.correspondenceId()).isNotNull();
  }

  @Test
  void mcpWriteAutoGrantedToOwnerAndAdminButNotCustomRole() throws Exception {
    // Owner and admin auto-grant via resolveCapabilities (no seed edit).
    Set<String> ownerCaps = resolveCaps(ownerMemberId);
    assertThat(ownerCaps).contains("MCP_WRITE", "MCP_ACCESS");

    UUID adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_admin", "admin@test.com", "Admin", "admin"));
    assertThat(resolveCaps(adminMemberId)).contains("MCP_WRITE");

    // A custom role declaring only MCP_ACCESS does NOT receive MCP_WRITE.
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String roleId = createCustomRole(owner, "MCP Read Only", Set.of("MCP_ACCESS"));
    assignRole(owner, memberMemberId.toString(), roleId);

    Set<String> customCaps = resolveCaps(memberMemberId);
    assertThat(customCaps).contains("MCP_ACCESS");
    assertThat(customCaps).doesNotContain("MCP_WRITE");
  }

  private Set<String> resolveCaps(UUID memberId) {
    @SuppressWarnings("unchecked")
    Set<String>[] holder = new Set[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                holder[0] =
                    transactionTemplate.execute(
                        tx -> orgRoleService.resolveCapabilities(memberId)));
    return holder[0];
  }

  private String createCustomRole(JwtRequestPostProcessor jwt, String name, Set<String> caps)
      throws Exception {
    var body =
        """
        {"name":"%s","description":"Test role","capabilities":[%s]}"""
            .formatted(
                name,
                caps.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + "," + b).orElse(""));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(JwtRequestPostProcessor jwt, String memberId, String roleId)
      throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
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
