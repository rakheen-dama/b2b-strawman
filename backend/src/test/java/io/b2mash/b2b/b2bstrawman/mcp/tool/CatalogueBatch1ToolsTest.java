package io.b2mash.b2b.b2bstrawman.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for the Epic 563A read-catalogue batch-1 tools ({@code list_matters}, {@code
 * get_matter}, {@code list_clients}, {@code get_client}, {@code search_documents}, {@code
 * get_document_url}).
 *
 * <p>Drives the streamable {@code /mcp} JSON-RPC endpoint over MockMvc (mirrors the 562C handshake
 * test): {@code initialize} (capture session id) → {@code notifications/initialized} → {@code
 * tools/call}. A successful tool result serialises to {@code content[0].text} (JSON) with {@code
 * isError:false}; a non-leaking error surfaces as {@code isError:true} carrying the sanitised
 * {@code McpError} JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueBatch1ToolsTest {

  private static final String ORG_ID = "org_mcp_batch1_test";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;

  // Matter the member IS assigned to.
  private String assignedMatterId;
  // Matter the member is NOT assigned to (owner-only).
  private String unassignedMatterId;
  private String clientId;
  private String orgDocumentId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Batch1 Test Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");

    assignedMatterId = TestEntityHelper.createProject(mockMvc, owner, "Assigned Matter");
    unassignedMatterId = TestEntityHelper.createProject(mockMvc, owner, "Owner-Only Matter");

    // Assign the member to ONE matter only — so list_matters shows them exactly one.
    mockMvc
        .perform(
            post("/api/projects/" + assignedMatterId + "/members")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());

    clientId = TestEntityHelper.createCustomer(mockMvc, owner, "Acme Corp", "acme@test.com");

    // Seed one ORG-scoped document via upload-init (immediately listable + presignable).
    MvcResult upload =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "brief.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    orgDocumentId =
        JsonPath.read(upload.getResponse().getContentAsString(), "$.documentId").toString();
    // Confirm the upload so the document is UPLOADED (required for a presigned download URL).
    mockMvc
        .perform(post("/api/documents/" + orgDocumentId + "/confirm").with(owner))
        .andExpect(status().isOk());
  }

  // ---- helpers ---------------------------------------------------------------

  private MvcResult mcpCall(String body, JwtRequestPostProcessor jwt, String sessionId)
      throws Exception {
    var builder =
        post("/mcp")
            .with(jwt)
            .header("Accept", ACCEPT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (sessionId != null) {
      builder = builder.header("Mcp-Session-Id", sessionId);
    }
    MvcResult result = mockMvc.perform(builder).andExpect(status().is2xxSuccessful()).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result =
          mockMvc.perform(asyncDispatch(result)).andExpect(status().is2xxSuccessful()).andReturn();
    }
    return result;
  }

  private JsonNode parseRpc(String body) {
    String json =
        body.lines()
            .filter(l -> l.startsWith("data:"))
            .map(l -> l.substring(5).trim())
            .findFirst()
            .orElse(body.trim());
    if (json.isBlank()) {
      throw new AssertionError("empty /mcp response body");
    }
    return objectMapper.readTree(json);
  }

  /** Opens a session for {@code jwt} and returns its Mcp-Session-Id. */
  private String openSession(JwtRequestPostProcessor jwt) throws Exception {
    MvcResult init = mcpCall(INITIALIZE_BODY, jwt, null);
    String sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    mcpCall(INITIALIZED_NOTIFICATION_BODY, jwt, sessionId);
    return sessionId;
  }

  /** Invokes a tool and returns the {@code /result} node of the JSON-RPC reply. */
  private JsonNode callTool(JwtRequestPostProcessor jwt, String sessionId, String name, String args)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":99,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
            .formatted(name, args);
    MvcResult res = mcpCall(body, jwt, sessionId);
    return parseRpc(res.getResponse().getContentAsString()).at("/result");
  }

  /** Parses the (single) text content block of a tool result into JSON. */
  private JsonNode resultPayload(JsonNode result) {
    String text = result.at("/content/0/text").asString();
    return objectMapper.readTree(text);
  }

  // ---- (1) list_matters — member sees only assigned; owner sees all ----------

  @Test
  void listMattersIsProjectAccessScoped() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");

    JsonNode ownerResult =
        callTool(owner, openSession(owner), "list_matters", "{\"page\":0,\"size\":50}");
    JsonNode ownerPayload = resultPayload(ownerResult);
    assertThat(ownerResult.at("/isError").asBoolean()).isFalse();
    assertThat(ownerPayload.at("/total").asLong()).isGreaterThanOrEqualTo(2);

    JsonNode memberResult =
        callTool(member, openSession(member), "list_matters", "{\"page\":0,\"size\":50}");
    JsonNode memberPayload = resultPayload(memberResult);
    assertThat(memberPayload.at("/total").asLong()).isEqualTo(1);
    assertThat(memberPayload.at("/items/0/id").asString()).isEqualTo(assignedMatterId);
  }

  // ---- (2) get_matter — 404 for non-member is non-leaking, isError:true ------

  @Test
  void getMatterReturnsNonLeakingErrorForNonMember() throws Exception {
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode result =
        callTool(
            member,
            openSession(member),
            "get_matter",
            "{\"id\":\"%s\"}".formatted(unassignedMatterId));

    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("not_found");
    // Non-leaking: no stack trace, no "forbidden", no id echo.
    String message = payload.at("/message").asString();
    assertThat(message).doesNotContainIgnoringCase("forbidden").doesNotContain(unassignedMatterId);
    assertThat(message).doesNotContainIgnoringCase("exception");

    // Same message whether the matter is inaccessible (above) or absent (below).
    JsonNode missing =
        callTool(
            member,
            openSession(member),
            "get_matter",
            "{\"id\":\"00000000-0000-0000-0000-000000000000\"}");
    assertThat(missing.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(missing).at("/message").asString()).isEqualTo(message);
  }

  // ---- (3) list_clients — org-wide -------------------------------------------

  @Test
  void listClientsIsOrgWide() throws Exception {
    // The member is not assigned to any matter for this client, yet sees it (org-wide).
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode result =
        callTool(member, openSession(member), "list_clients", "{\"page\":0,\"size\":50}");
    assertThat(result.at("/isError").asBoolean()).isFalse();
    JsonNode payload = resultPayload(result);
    boolean found = false;
    for (JsonNode item : payload.at("/items")) {
      if (clientId.equals(item.at("/id").asString())) {
        found = true;
      }
    }
    assertThat(found).as("member sees the org-wide client").isTrue();
  }

  // ---- (4) get_client — linked matters from the separate call ----------------

  @Test
  void getClientResolvesLinkedMatters() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String session = openSession(owner);

    // The CREATE_PROJECT lifecycle gate blocks linking a PROSPECT customer; move it to ONBOARDING
    // (a single transition, no checklist) which the gate permits, then link via the join table.
    mockMvc
        .perform(
            post("/api/customers/" + clientId + "/transition")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"ONBOARDING\"}"))
        .andExpect(status().isOk());
    String linkedMatter = TestEntityHelper.createProject(mockMvc, owner, "Linked-Client Matter");
    mockMvc
        .perform(post("/api/customers/" + clientId + "/projects/" + linkedMatter).with(owner))
        .andExpect(status().isCreated());

    JsonNode result = callTool(owner, session, "get_client", "{\"id\":\"%s\"}".formatted(clientId));
    assertThat(result.at("/isError").asBoolean()).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/id").asString()).isEqualTo(clientId);
    boolean linked = false;
    for (JsonNode m : payload.at("/linkedMatters")) {
      if (linkedMatter.equals(m.at("/id").asString())) {
        linked = true;
      }
    }
    assertThat(linked).as("linkedMatters resolved via listProjectsForCustomer").isTrue();
  }

  // ---- (5) search_documents — project vs org/customer scope ------------------

  @Test
  void searchDocumentsRespectsScope() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String session = openSession(owner);

    // org scope — returns the seeded ORG document.
    JsonNode orgResult =
        callTool(owner, session, "search_documents", "{\"scope\":\"ORG\",\"page\":0,\"size\":50}");
    assertThat(orgResult.at("/isError").asBoolean()).isFalse();
    JsonNode orgPayload = resultPayload(orgResult);
    boolean foundOrg = false;
    for (JsonNode d : orgPayload.at("/items")) {
      if (orgDocumentId.equals(d.at("/id").asString())) {
        foundOrg = true;
        assertThat(d.at("/scope").asString()).isEqualTo("ORG");
      }
    }
    assertThat(foundOrg).as("ORG-scoped search returns the seeded document").isTrue();

    // project scope — a non-member is denied with a non-leaking error.
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode denied =
        callTool(
            member,
            openSession(member),
            "search_documents",
            "{\"projectId\":\"%s\",\"page\":0,\"size\":50}".formatted(unassignedMatterId));
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(denied).at("/error").asString()).isEqualTo("not_found");
  }

  // ---- (6) get_document_url — presigned URL, no bytes ------------------------

  @Test
  void getDocumentUrlReturnsPresignedUrlNeverBytes() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "get_document_url",
            "{\"documentId\":\"%s\"}".formatted(orgDocumentId));
    assertThat(result.at("/isError").asBoolean()).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/url").asString()).isNotBlank();
    assertThat(payload.at("/expiresInSeconds").asLong()).isGreaterThan(0);
    // No byte payload fields.
    assertThat(payload.has("bytes")).isFalse();
    assertThat(payload.has("content")).isFalse();
    assertThat(payload.has("data")).isFalse();
  }

  // ---- (7) pagination cap — size clamps to 50 --------------------------------

  @Test
  void listToolsClampPageSizeTo50() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_matters", "{\"page\":0,\"size\":500}");
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/size").asInt()).isEqualTo(50);
  }

  // ---- audit: mcp.tool.invoked emitted ---------------------------------------

  @Test
  void toolInvocationEmitsAuditEvent() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    callTool(owner, openSession(owner), "list_matters", "{\"page\":0,\"size\":50}");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditEventRepository.findByFilter(
                      null, null, null, "mcp.tool.invoked", null, null, PageRequest.of(0, 50));
              assertThat(page.getContent())
                  .extracting(AuditEvent::getEventType)
                  .contains("mcp.tool.invoked");
            });
  }
}
