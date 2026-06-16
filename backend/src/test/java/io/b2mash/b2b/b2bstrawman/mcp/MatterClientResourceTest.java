package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for Epic 563B: the {@code get_matter_activity} tool and the {@code
 * kazi://matter/{id}} / {@code kazi://client/{id}} resources.
 *
 * <p>Drives the streamable {@code /mcp} JSON-RPC endpoint over MockMvc (mirrors the 562C handshake
 * test). A resource read returns {@code /result/contents[0].text} (the JSON projection); a tool
 * call returns {@code /result/content[0].text}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterClientResourceTest {

  private static final String ORG_ID = "org_mcp_resource_test";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";
  // kazi://matter/{id} and kazi://client/{id} are URI-TEMPLATED resources, so the MCP server
  // advertises them via resources/templates/list (not the fixed-URI resources/list).
  private static final String RESOURCE_TEMPLATES_LIST_BODY =
      """
      {"jsonrpc":"2.0","id":3,"method":"resources/templates/list","params":{}}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuditService auditService;

  private String tenantSchema;
  private UUID ownerUuid;
  private String assignedMatterId;
  private String unassignedMatterId;
  private String clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Resource Test Org", null);
    String ownerId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member");
    ownerUuid = UUID.fromString(ownerId);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");

    assignedMatterId = TestEntityHelper.createProject(mockMvc, owner, "Assigned Matter");
    unassignedMatterId = TestEntityHelper.createProject(mockMvc, owner, "Owner-Only Matter");
    mockMvc
        .perform(
            post("/api/projects/" + assignedMatterId + "/members")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberId)))
        .andExpect(status().isCreated());

    clientId = TestEntityHelper.createCustomer(mockMvc, owner, "Beta LLC", "beta@test.com");

    // Seed two activity rows (audit events with project_id) on the assigned matter.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerUuid)
        .run(
            () -> {
              UUID matterUuid = UUID.fromString(assignedMatterId);
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("title", "Seed Task", "project_id", matterUuid.toString())));
              auditService.log(
                  new AuditEventRecord(
                      "document.uploaded",
                      "document",
                      UUID.randomUUID(),
                      ownerUuid,
                      "USER",
                      "API",
                      null,
                      null,
                      Map.of("file_name", "seed.pdf", "project_id", matterUuid.toString())));
            });
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

  private String openSession(JwtRequestPostProcessor jwt) throws Exception {
    MvcResult init = mcpCall(INITIALIZE_BODY, jwt, null);
    String sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    mcpCall(INITIALIZED_NOTIFICATION_BODY, jwt, sessionId);
    return sessionId;
  }

  private JsonNode callTool(JwtRequestPostProcessor jwt, String sessionId, String name, String args)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":99,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
            .formatted(name, args);
    MvcResult res = mcpCall(body, jwt, sessionId);
    return parseRpc(res.getResponse().getContentAsString()).at("/result");
  }

  private JsonNode readResource(JwtRequestPostProcessor jwt, String sessionId, String uri)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":98,"method":"resources/read","params":{"uri":"%s"}}"""
            .formatted(uri);
    MvcResult res = mcpCall(body, jwt, sessionId);
    return parseRpc(res.getResponse().getContentAsString()).at("/result");
  }

  private JsonNode toolPayload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
  }

  private JsonNode resourcePayload(JsonNode result) {
    return objectMapper.readTree(result.at("/contents/0/text").asString());
  }

  // ---- (1) get_matter_activity — project-access + pagination -----------------

  @Test
  void getMatterActivityRespectsAccessAndPaginates() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode ok =
        callTool(
            owner,
            openSession(owner),
            "get_matter_activity",
            "{\"projectId\":\"%s\",\"page\":0,\"size\":500}".formatted(assignedMatterId));
    assertThat(ok.at("/isError").asBoolean()).isFalse();
    JsonNode payload = toolPayload(ok);
    assertThat(payload.at("/size").asInt()).isEqualTo(50); // clamped
    assertThat(payload.at("/total").asLong()).isGreaterThanOrEqualTo(2);

    // Non-member is denied with a non-leaking error.
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode denied =
        callTool(
            member,
            openSession(member),
            "get_matter_activity",
            "{\"projectId\":\"%s\",\"page\":0,\"size\":50}".formatted(unassignedMatterId));
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(toolPayload(denied).at("/error").asString()).isEqualTo("not_found");
  }

  // ---- (2) resources/templates/list — exactly the two 563B resources ---------

  @Test
  void resourceTemplatesListExposesMatterAndClientResources() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String session = openSession(owner);
    MvcResult rl = mcpCall(RESOURCE_TEMPLATES_LIST_BODY, owner, session);
    JsonNode templates =
        parseRpc(rl.getResponse().getContentAsString()).at("/result/resourceTemplates");
    assertThat(templates.isArray()).isTrue();
    boolean hasMatter = false;
    boolean hasClient = false;
    for (JsonNode r : templates) {
      String uri = r.at("/uriTemplate").asString();
      if ("kazi://matter/{id}".equals(uri)) {
        hasMatter = true;
      }
      if ("kazi://client/{id}".equals(uri)) {
        hasClient = true;
      }
    }
    assertThat(hasMatter).as("kazi://matter/{id} resource template registered").isTrue();
    assertThat(hasClient).as("kazi://client/{id} resource template registered").isTrue();
  }

  // ---- (3) kazi://matter/{id} read for a member ------------------------------

  @Test
  void matterResourceReadableByMember() throws Exception {
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode result =
        readResource(member, openSession(member), "kazi://matter/" + assignedMatterId);
    JsonNode payload = resourcePayload(result);
    assertThat(payload.at("/id").asString()).isEqualTo(assignedMatterId);
    assertThat(payload.at("/name").asString()).isEqualTo("Assigned Matter");
  }

  // ---- (4) kazi://matter/{id} 404 for a non-member (non-leaking) -------------

  @Test
  void matterResourceNonLeakingForNonMember() throws Exception {
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode result =
        readResource(member, openSession(member), "kazi://matter/" + unassignedMatterId);
    JsonNode payload = resourcePayload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("not_found");
    assertThat(payload.at("/message").asString())
        .doesNotContainIgnoringCase("forbidden")
        .doesNotContain(unassignedMatterId);
  }

  // ---- (5) kazi://client/{id} org-wide read ----------------------------------

  @Test
  void clientResourceOrgWideRead() throws Exception {
    // Even a member with no matter for this client can read it (org-wide).
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_member");
    JsonNode result = readResource(member, openSession(member), "kazi://client/" + clientId);
    JsonNode payload = resourcePayload(result);
    assertThat(payload.at("/id").asString()).isEqualTo(clientId);
    assertThat(payload.at("/name").asString()).isEqualTo("Beta LLC");
  }
}
