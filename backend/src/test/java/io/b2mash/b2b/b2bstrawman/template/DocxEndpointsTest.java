package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocxEndpointsTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_docx_endpoints_test";
  private static final String ORG_ID_B = "org_docx_endpoints_test_b";
  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String uploadedTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DOCX Endpoints Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(
        ORG_ID, "user_endpoints_owner", "endpoints_owner@test.com", "Endpoints Owner", "owner");
    syncMember(
        ORG_ID, "user_endpoints_member", "endpoints_member@test.com", "Endpoints Member", "member");

    // --- Tenant B (for cross-tenant isolation test) ---
    provisioningService.provisionTenant(ORG_ID_B, "DOCX Endpoints Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    syncMember(
        ORG_ID_B,
        "user_endpoints_owner_b",
        "endpoints_owner_b@test.com",
        "Endpoints Owner B",
        "owner");

    // Upload a DOCX template for use in subsequent tests
    byte[] docxBytes = createTestDocx("Hello {{customer.name}}, project {{project.name}}!");
    MockMultipartFile file =
        new MockMultipartFile("file", "setup-template.docx", DOCX_CONTENT_TYPE, docxBytes);

    var result =
        mockMvc
            .perform(
                multipart("/api/templates/docx/upload")
                    .file(file)
                    .param("name", "Endpoints Test Template")
                    .param("category", "ENGAGEMENT_LETTER")
                    .param("entityType", "PROJECT")
                    .with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    uploadedTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- JWT Helpers ---
  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_endpoints_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_endpoints_member")
                    .claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private JwtRequestPostProcessor ownerJwtTenantB() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_endpoints_owner_b")
                    .claim("o", Map.of("id", ORG_ID_B, "rol", "owner")));
  }

  // --- Member sync helper ---
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

  // --- .docx creation helpers ---
  private byte[] createTestDocx(String text) throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run = para.createRun();
      run.setText(text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  // --- Tests ---

  @Test
  @Order(1)
  void getFields_validDocxTemplate_returnsFields() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + uploadedTemplateId + "/docx/fields").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @Order(2)
  void getFields_nonDocxTemplate_returns400() throws Exception {
    // Create a TIPTAP template
    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Tiptap For Fields Test",
                          "category": "ENGAGEMENT_LETTER",
                          "primaryEntityType": "PROJECT",
                          "content": {"type": "doc", "content": []}
                        }
                        """)
                    .with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    String tiptapId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/templates/" + tiptapId + "/docx/fields").with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Not a DOCX template"));
  }

  @Test
  @Order(3)
  void getFields_notFound_returns404() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + UUID.randomUUID() + "/docx/fields").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(4)
  void download_validDocxTemplate_returns302() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + uploadedTemplateId + "/docx/download").with(ownerJwt()))
        .andExpect(status().isFound())
        .andExpect(header().exists("Location"));
  }

  @Test
  @Order(5)
  void download_nonDocxTemplate_returns400() throws Exception {
    // Create a TIPTAP template
    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Tiptap For Download Test",
                          "category": "ENGAGEMENT_LETTER",
                          "primaryEntityType": "PROJECT",
                          "content": {"type": "doc", "content": []}
                        }
                        """)
                    .with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    String tiptapId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/templates/" + tiptapId + "/docx/download").with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Not a DOCX template"));
  }

  @Test
  @Order(6)
  void replace_validDocx_returns200WithUpdatedFields() throws Exception {
    // Replace with a new DOCX that has different fields
    byte[] newDocxBytes = createTestDocx("Updated: {{customer.email}} and {{invoice.number}}");
    MockMultipartFile newFile =
        new MockMultipartFile("file", "replacement.docx", DOCX_CONTENT_TYPE, newDocxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/" + uploadedTemplateId + "/docx/replace")
                .file(newFile)
                .with(
                    request -> {
                      request.setMethod("PUT");
                      return request;
                    })
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.format").value("DOCX"))
        .andExpect(jsonPath("$.discoveredFields").isArray())
        .andExpect(jsonPath("$.discoveredFields.length()").value(2));
  }

  @Test
  @Order(7)
  void replace_invalidMimeType_returns400() throws Exception {
    MockMultipartFile badFile =
        new MockMultipartFile("file", "bad.txt", "text/plain", "Not a docx".getBytes());

    mockMvc
        .perform(
            multipart("/api/templates/" + uploadedTemplateId + "/docx/replace")
                .file(badFile)
                .with(
                    request -> {
                      request.setMethod("PUT");
                      return request;
                    })
                .with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid file type"));
  }

  @Test
  @Order(8)
  void replace_asMember_returns403() throws Exception {
    byte[] docxBytes = createTestDocx("Member attempt {{customer.name}}");
    MockMultipartFile file =
        new MockMultipartFile("file", "member-replace.docx", DOCX_CONTENT_TYPE, docxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/" + uploadedTemplateId + "/docx/replace")
                .file(file)
                .with(
                    request -> {
                      request.setMethod("PUT");
                      return request;
                    })
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void getFields_crossTenant_returns404() throws Exception {
    // Tenant B's owner tries to access Tenant A's template — should get 404 (schema isolation)
    mockMvc
        .perform(
            get("/api/templates/" + uploadedTemplateId + "/docx/fields").with(ownerJwtTenantB()))
        .andExpect(status().isNotFound());
  }
}
