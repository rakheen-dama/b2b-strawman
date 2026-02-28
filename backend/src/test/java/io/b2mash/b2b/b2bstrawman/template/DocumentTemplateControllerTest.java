package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class DocumentTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dt_ctrl_test";
  private static final String ORG_ID_B = "org_dt_ctrl_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ObjectMapper objectMapper;

  private String memberIdOwner;
  private String memberIdOwnerB;
  private String createdTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DT Controller Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(
            ORG_ID, "user_dt_ctrl_owner", "dt_ctrl_owner@test.com", "DT Ctrl Owner", "owner");
    syncMember(
        ORG_ID, "user_dt_ctrl_member", "dt_ctrl_member@test.com", "DT Ctrl Member", "member");

    // Provision tenant B for isolation tests
    provisioningService.provisionTenant(ORG_ID_B, "DT Controller Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    memberIdOwnerB =
        syncMember(
            ORG_ID_B,
            "user_dt_ctrl_owner_b",
            "dt_ctrl_owner_b@test.com",
            "DT Ctrl Owner B",
            "owner");
  }

  @Test
  @Order(1)
  void shouldCreateTemplateWithSlug() throws Exception {
    Map<String, Object> tiptapContent =
        TestDocumentBuilder.doc().heading(1, "Dear").paragraph("We are pleased to...").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("name", "Engagement Letter");
    requestBody.put("category", "ENGAGEMENT_LETTER");
    requestBody.put("primaryEntityType", "PROJECT");
    requestBody.put("content", tiptapContent);
    requestBody.put("description", "Standard engagement letter");

    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("engagement-letter"))
            .andExpect(jsonPath("$.category").value("ENGAGEMENT_LETTER"))
            .andExpect(jsonPath("$.primaryEntityType").value("PROJECT"))
            .andExpect(jsonPath("$.source").value("ORG_CUSTOM"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.content.type").value("doc"))
            .andReturn();

    createdTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void shouldCreateSecondTemplate() throws Exception {
    Map<String, Object> tiptapContent =
        TestDocumentBuilder.doc().heading(1, "Non-Disclosure Agreement").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("name", "NDA Agreement");
    requestBody.put("category", "NDA");
    requestBody.put("primaryEntityType", "CUSTOMER");
    requestBody.put("content", tiptapContent);

    mockMvc
        .perform(
            post("/api/templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("nda-agreement"));
  }

  @Test
  @Order(3)
  void shouldListActiveTemplates() throws Exception {
    mockMvc
        .perform(get("/api/templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
  }

  @Test
  @Order(4)
  void shouldFilterByCategory() throws Exception {
    mockMvc
        .perform(get("/api/templates?category=ENGAGEMENT_LETTER").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].category").value("ENGAGEMENT_LETTER"));
  }

  @Test
  @Order(5)
  void shouldFilterByEntityType() throws Exception {
    mockMvc
        .perform(get("/api/templates?primaryEntityType=CUSTOMER").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].primaryEntityType").value("CUSTOMER"));
  }

  @Test
  @Order(6)
  void shouldUpdateTemplate() throws Exception {
    Map<String, Object> updatedContent =
        TestDocumentBuilder.doc().paragraph("Updated content").build();

    var updateBody = new LinkedHashMap<String, Object>();
    updateBody.put("name", "Updated Engagement Letter");
    updateBody.put("content", updatedContent);
    updateBody.put("description", "Updated description");

    mockMvc
        .perform(
            put("/api/templates/" + createdTemplateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Engagement Letter"))
        .andExpect(jsonPath("$.content.type").value("doc"))
        .andExpect(jsonPath("$.description").value("Updated description"));
  }

  @Test
  @Order(7)
  void shouldDeactivateTemplate() throws Exception {
    mockMvc
        .perform(delete("/api/templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify deactivated
    mockMvc
        .perform(get("/api/templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @Order(8)
  void listShouldExcludeContentAndCss() throws Exception {
    Map<String, Object> tiptapContent =
        TestDocumentBuilder.doc().heading(1, "Some content").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("name", "Content Exclusion Test");
    requestBody.put("category", "REPORT");
    requestBody.put("primaryEntityType", "PROJECT");
    requestBody.put("content", tiptapContent);
    requestBody.put("css", "body { font-size: 14px; }");

    mockMvc
        .perform(
            post("/api/templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isCreated());

    // List should NOT include content or css fields
    mockMvc
        .perform(get("/api/templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").doesNotExist())
        .andExpect(jsonPath("$[0].css").doesNotExist());
  }

  @Test
  @Order(9)
  void memberCannotCreateTemplate() throws Exception {
    Map<String, Object> tiptapContent = TestDocumentBuilder.doc().paragraph("Should fail").build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("name", "Member Template");
    requestBody.put("category", "OTHER");
    requestBody.put("primaryEntityType", "PROJECT");
    requestBody.put("content", tiptapContent);

    mockMvc
        .perform(
            post("/api/templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
        .andExpect(status().isForbidden());
  }

  // --- Tenant Isolation Tests ---

  @Test
  @Order(10)
  void templateInTenantAIsNotVisibleInTenantB() throws Exception {
    // createdTemplateId was created by tenant A in order 1
    // Attempt to GET it from tenant B — should return 404
    mockMvc
        .perform(get("/api/templates/" + createdTemplateId).with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }

  // --- Round-trip test: POST Tiptap JSON, GET same structure back ---

  @Test
  @Order(11)
  void shouldRoundTripTiptapJsonContent() throws Exception {
    Map<String, Object> tiptapContent =
        TestDocumentBuilder.doc()
            .heading(1, "Engagement Letter")
            .paragraph("Dear valued customer,")
            .variable("customer.name")
            .build();

    var requestBody = new LinkedHashMap<String, Object>();
    requestBody.put("name", "Round Trip Test");
    requestBody.put("category", "ENGAGEMENT_LETTER");
    requestBody.put("primaryEntityType", "PROJECT");
    requestBody.put("content", tiptapContent);

    var createResult =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content.type").value("doc"))
            .andExpect(jsonPath("$.content.content[0].type").value("heading"))
            .andExpect(jsonPath("$.content.content[1].type").value("paragraph"))
            .andReturn();

    String templateId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // GET by ID should return the same content structure
    mockMvc
        .perform(get("/api/templates/" + templateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.type").value("doc"))
        .andExpect(jsonPath("$.content.content[0].type").value("heading"))
        .andExpect(jsonPath("$.content.content[1].type").value("paragraph"));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dt_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_dt_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_dt_ctrl_owner_b")
                    .claim("o", Map.of("id", ORG_ID_B, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Helpers ---

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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
