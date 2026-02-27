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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateClauseControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_tc_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String templateId;
  private String clause1Id;
  private String clause2Id;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TC Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_tc_owner", "tc_owner@test.com", "TC Owner", "owner");
    syncMember(ORG_ID, "user_tc_admin", "tc_admin@test.com", "TC Admin", "admin");
    syncMember(ORG_ID, "user_tc_member", "tc_member@test.com", "TC Member", "member");

    // Create a template
    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    jdbcTemplate.execute("SET search_path TO " + schema);
    jdbcTemplate.update(
        """
        INSERT INTO document_templates (id, primary_entity_type, name, slug, category, content, source, active, sort_order, created_at, updated_at)
        VALUES (gen_random_uuid(), 'PROJECT', 'TC Ctrl Template', 'tc-ctrl-template', 'ENGAGEMENT_LETTER', '<p>content</p>', 'ORG_CUSTOM', true, 0, now(), now())
        """);
    templateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM document_templates WHERE slug = 'tc-ctrl-template'", String.class);

    // Create two clauses
    jdbcTemplate.update(
        """
        INSERT INTO clauses (id, title, slug, body, category, source, active, sort_order, created_at, updated_at)
        VALUES (gen_random_uuid(), 'TC Ctrl Clause 1', 'tc-ctrl-clause-1', '<p>Body one content here</p>', 'general', 'CUSTOM', true, 0, now(), now())
        """);
    clause1Id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM clauses WHERE slug = 'tc-ctrl-clause-1'", String.class);

    jdbcTemplate.update(
        """
        INSERT INTO clauses (id, title, slug, body, category, source, active, sort_order, created_at, updated_at)
        VALUES (gen_random_uuid(), 'TC Ctrl Clause 2', 'tc-ctrl-clause-2', '<p>Body two content here</p>', 'payment', 'CUSTOM', true, 0, now(), now())
        """);
    clause2Id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM clauses WHERE slug = 'tc-ctrl-clause-2'", String.class);

    jdbcTemplate.execute("SET search_path TO public");
  }

  @Test
  @Order(1)
  void get_returnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + templateId + "/clauses").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @Order(2)
  void put_replacesClauseList_asAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/templates/" + templateId + "/clauses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clauses":[{"clauseId":"%s","sortOrder":0,"required":true},{"clauseId":"%s","sortOrder":1,"required":false}]}
                    """
                        .formatted(clause1Id, clause2Id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].title").value("TC Ctrl Clause 1"))
        .andExpect(jsonPath("$[0].required").value(true))
        .andExpect(jsonPath("$[0].sortOrder").value(0))
        .andExpect(jsonPath("$[1].title").value("TC Ctrl Clause 2"))
        .andExpect(jsonPath("$[1].required").value(false));
  }

  @Test
  @Order(3)
  void get_returnsEnrichedClauses() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + templateId + "/clauses").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].clauseId").value(clause1Id))
        .andExpect(jsonPath("$[0].bodyPreview").exists())
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  @Order(4)
  void put_returns403_forMember() throws Exception {
    mockMvc
        .perform(
            put("/api/templates/" + templateId + "/clauses")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clauses":[]}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void post_addsSingleClause_asOwner() throws Exception {
    // First clear the associations via PUT
    mockMvc
        .perform(
            put("/api/templates/" + templateId + "/clauses")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clauses":[]}
                    """))
        .andExpect(status().isOk());

    // Now add a single clause
    mockMvc
        .perform(
            post("/api/templates/" + templateId + "/clauses")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clauseId":"%s","required":true}
                    """
                        .formatted(clause1Id)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clauseId").value(clause1Id))
        .andExpect(jsonPath("$.required").value(true))
        .andExpect(jsonPath("$.sortOrder").value(0));
  }

  @Test
  @Order(6)
  void post_returns409_forDuplicate() throws Exception {
    mockMvc
        .perform(
            post("/api/templates/" + templateId + "/clauses")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clauseId":"%s","required":false}
                    """
                        .formatted(clause1Id)))
        .andExpect(status().isConflict());
  }

  @Test
  @Order(7)
  void delete_removesClause() throws Exception {
    mockMvc
        .perform(delete("/api/templates/" + templateId + "/clauses/" + clause1Id).with(adminJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/templates/" + templateId + "/clauses").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
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
        .jwt(j -> j.subject("user_tc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tc_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_tc_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
