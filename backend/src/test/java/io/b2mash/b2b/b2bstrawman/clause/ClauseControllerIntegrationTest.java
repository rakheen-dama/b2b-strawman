package io.b2mash.b2b.b2bstrawman.clause;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.TestDocumentBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClauseControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_clause_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String createdClauseId;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Clause Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_clause_owner", "clause_owner@test.com", "Clause Owner", "owner");
    syncMember(ORG_ID, "user_clause_admin", "clause_admin@test.com", "Clause Admin", "admin");
    syncMember(ORG_ID, "user_clause_member", "clause_member@test.com", "Clause Member", "member");
  }

  @Test
  @Order(1)
  void get_listClauses_returns200() throws Exception {
    mockMvc
        .perform(get("/api/clauses").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @Order(2)
  void post_createClause_returns201_asAdmin() throws Exception {
    Map<String, Object> clauseBody = TestDocumentBuilder.doc().paragraph("Body").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "Ctrl Test Clause");
    requestBody.put("description", "A test clause");
    requestBody.put("body", clauseBody);
    requestBody.put("category", "general");

    var result =
        mockMvc
            .perform(
                post("/api/clauses")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.title").value("Ctrl Test Clause"))
            .andExpect(jsonPath("$.slug").value("ctrl-test-clause"))
            .andExpect(jsonPath("$.source").value("CUSTOM"))
            .andExpect(jsonPath("$.body.type").value("doc"))
            .andReturn();
    createdClauseId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(3)
  void post_createClause_withMemberRole_returns403() throws Exception {
    Map<String, Object> clauseBody = TestDocumentBuilder.doc().paragraph("Body").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "Member Clause");
    requestBody.put("description", null);
    requestBody.put("body", clauseBody);
    requestBody.put("category", "general");

    mockMvc
        .perform(
            post("/api/clauses")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void post_createClause_withInvalidBody_returns400() throws Exception {
    // Send null body to trigger @NotNull validation
    var invalidBody = new LinkedHashMap<String, Object>();
    invalidBody.put("title", "");
    invalidBody.put("description", null);
    invalidBody.put("body", null);
    invalidBody.put("category", "");

    mockMvc
        .perform(
            post("/api/clauses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidBody)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void get_getClause_returns200() throws Exception {
    mockMvc
        .perform(get("/api/clauses/" + createdClauseId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdClauseId))
        .andExpect(jsonPath("$.title").value("Ctrl Test Clause"));
  }

  @Test
  @Order(6)
  void put_updateClause_returns200() throws Exception {
    Map<String, Object> updatedBody = TestDocumentBuilder.doc().paragraph("Updated Body").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "Updated Clause");
    requestBody.put("description", "Updated");
    requestBody.put("body", updatedBody);
    requestBody.put("category", "legal");

    mockMvc
        .perform(
            put("/api/clauses/" + createdClauseId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Clause"))
        .andExpect(jsonPath("$.category").value("legal"))
        .andExpect(jsonPath("$.body.type").value("doc"));
  }

  @Test
  @Order(7)
  void post_deactivateClause_returns200() throws Exception {
    mockMvc
        .perform(post("/api/clauses/" + createdClauseId + "/deactivate").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @Order(8)
  void post_cloneClause_returns201() throws Exception {
    mockMvc
        .perform(post("/api/clauses/" + createdClauseId + "/clone").with(adminJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("CLONED"))
        .andExpect(jsonPath("$.sourceClauseId").value(createdClauseId));
  }

  @Test
  @Order(9)
  void get_listCategories_returns200() throws Exception {
    mockMvc
        .perform(get("/api/clauses/categories").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @Order(10)
  void delete_deleteClause_returns204() throws Exception {
    Map<String, Object> clauseBody = TestDocumentBuilder.doc().paragraph("Delete").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "Delete Me Clause");
    requestBody.put("description", null);
    requestBody.put("body", clauseBody);
    requestBody.put("category", "general");

    var result =
        mockMvc
            .perform(
                post("/api/clauses")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andReturn();
    var deleteId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/clauses/" + deleteId).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(11)
  void put_updateSystemClause_returns400() throws Exception {
    Map<String, Object> clauseBody = TestDocumentBuilder.doc().paragraph("System").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "System Ctrl Clause");
    requestBody.put("description", null);
    requestBody.put("body", clauseBody);
    requestBody.put("category", "general");

    var result =
        mockMvc
            .perform(
                post("/api/clauses")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andReturn();
    var sysClauseId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Mark as SYSTEM via direct SQL in the tenant schema
    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    jdbcTemplate.update(
        "UPDATE \"%s\".clauses SET source = 'SYSTEM' WHERE id = ?::uuid".formatted(schema),
        sysClauseId);

    Map<String, Object> updateBody = TestDocumentBuilder.doc().paragraph("Nope").build();

    var updateRequestBody = new LinkedHashMap<String, Object>();
    updateRequestBody.put("title", "Should Fail");
    updateRequestBody.put("description", null);
    updateRequestBody.put("body", updateBody);
    updateRequestBody.put("category", "general");

    mockMvc
        .perform(
            put("/api/clauses/" + sysClauseId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequestBody)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(12)
  void get_listClauses_withCategoryFilter_returns200() throws Exception {
    mockMvc
        .perform(get("/api/clauses?category=legal").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  // --- Round-trip test: POST Tiptap JSON body, verify on GET ---

  @Test
  @Order(13)
  void shouldRoundTripTiptapJsonBody() throws Exception {
    Map<String, Object> clauseBody =
        TestDocumentBuilder.doc()
            .heading(1, "Payment Terms")
            .paragraph("Net 30 days from invoice date.")
            .build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("title", "Round Trip Clause");
    requestBody.put("description", "Testing JSON round-trip");
    requestBody.put("body", clauseBody);
    requestBody.put("category", "billing");

    var createResult =
        mockMvc
            .perform(
                post("/api/clauses")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.body.type").value("doc"))
            .andExpect(jsonPath("$.body.content[0].type").value("heading"))
            .andExpect(jsonPath("$.body.content[1].type").value("paragraph"))
            .andReturn();

    String clauseId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // GET by ID should return the same body structure
    mockMvc
        .perform(get("/api/clauses/" + clauseId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body.type").value("doc"))
        .andExpect(jsonPath("$.body.content[0].type").value("heading"))
        .andExpect(jsonPath("$.body.content[1].type").value("paragraph"));
  }

  // --- Helper: sync member ---
  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  // --- JWT helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clause_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clause_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_clause_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
