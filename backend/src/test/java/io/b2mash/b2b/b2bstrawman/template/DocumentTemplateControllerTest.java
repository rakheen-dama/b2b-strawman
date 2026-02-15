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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dt_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;
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
  }

  @Test
  @Order(1)
  void shouldCreateTemplateWithSlug() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Engagement Letter",
                          "category": "ENGAGEMENT_LETTER",
                          "primaryEntityType": "PROJECT",
                          "content": "<h1>Dear {{customer.name}}</h1><p>We are pleased to...</p>",
                          "description": "Standard engagement letter"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("engagement-letter"))
            .andExpect(jsonPath("$.category").value("ENGAGEMENT_LETTER"))
            .andExpect(jsonPath("$.primaryEntityType").value("PROJECT"))
            .andExpect(jsonPath("$.source").value("ORG_CUSTOM"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

    createdTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void shouldCreateSecondTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "NDA Agreement",
                      "category": "NDA",
                      "primaryEntityType": "CUSTOMER",
                      "content": "<h1>Non-Disclosure Agreement</h1>"
                    }
                    """))
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
    mockMvc
        .perform(
            put("/api/templates/" + createdTemplateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Engagement Letter",
                      "content": "<h1>Updated content</h1>",
                      "description": "Updated description"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Engagement Letter"))
        .andExpect(jsonPath("$.content").value("<h1>Updated content</h1>"))
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
    // Create a template with content and css
    mockMvc
        .perform(
            post("/api/templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Content Exclusion Test",
                      "category": "REPORT",
                      "primaryEntityType": "PROJECT",
                      "content": "<h1>Some content</h1>",
                      "css": "body { font-size: 14px; }"
                    }
                    """))
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
    mockMvc
        .perform(
            post("/api/templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Member Template",
                      "category": "OTHER",
                      "primaryEntityType": "PROJECT",
                      "content": "<p>Should fail</p>"
                    }
                    """))
        .andExpect(status().isForbidden());
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
