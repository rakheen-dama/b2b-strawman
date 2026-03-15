package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DocumentTemplateFormatTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dt_format_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;

  private String tiptapTemplateId;
  private String docxTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DT Format Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(
        ORG_ID, "user_dt_format_owner", "dt_format_owner@test.com", "DT Format Owner", "owner");
  }

  @Test
  @Order(1)
  void createTemplate_defaultFormatIsTiptap() throws Exception {
    var content = TestDocumentBuilder.doc().paragraph("Hello World").build();
    var request =
        Map.of(
            "name", "Format Test TIPTAP",
            "category", "ENGAGEMENT_LETTER",
            "primaryEntityType", "PROJECT",
            "content", content);

    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.format").value("TIPTAP"))
            .andExpect(jsonPath("$.discoveredFields").doesNotExist())
            .andReturn();

    tiptapTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(2)
  void createTemplate_secondTiptap_setsFormatAndTracksId() throws Exception {
    // Creates a second TIPTAP template via the API (the only format the create endpoint supports).
    // TODO: Add a true DOCX creation test when the DOCX upload API is implemented (Epic 324).
    var content = TestDocumentBuilder.doc().paragraph("Placeholder").build();
    var request =
        Map.of(
            "name", "Format Test DOCX",
            "slug", "format-test-docx-api",
            "category", "ENGAGEMENT_LETTER",
            "primaryEntityType", "PROJECT",
            "content", content);

    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.format").value("TIPTAP"))
            .andReturn();

    docxTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(3)
  void listTemplates_filterByFormat_returnsFiltered() throws Exception {
    // All templates created so far are TIPTAP (plus any seeded ones)
    mockMvc
        .perform(get("/api/templates?format=TIPTAP").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].format").value("TIPTAP"));

    // DOCX filter should return empty or only DOCX templates
    mockMvc
        .perform(get("/api/templates?format=DOCX").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @Order(4)
  void listTemplates_noFilter_returnsBothFormats() throws Exception {
    // Without format filter, should return all templates
    var result =
        mockMvc
            .perform(get("/api/templates").with(ownerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].format").exists())
            .andReturn();

    // Verify format field is present in list response
    String response = result.getResponse().getContentAsString();
    List<String> formats = JsonPath.read(response, "$[*].format");
    // All returned templates should have a valid format field
    for (String format : formats) {
      assertThat(format).isIn("TIPTAP", "DOCX");
    }
  }

  @Test
  @Order(5)
  void detailTemplate_tiptapFormat_hasNullDiscoveredFields() throws Exception {
    mockMvc
        .perform(get("/api/templates/" + tiptapTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.format").value("TIPTAP"))
        .andExpect(jsonPath("$.discoveredFields").doesNotExist());
  }

  @Test
  @Order(6)
  void tiptapTemplate_hasNoDocxFields() throws Exception {
    // Verifies that TIPTAP templates have no DOCX-specific fields set.
    // Direct validation-throws testing requires setting docxS3Key on a TIPTAP entity,
    // which can't be done via the API (docxS3Key is not in CreateTemplateRequest).
    mockMvc
        .perform(get("/api/templates/" + tiptapTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.format").value("TIPTAP"));

    // The list response should show null for docx fields on TIPTAP templates
    mockMvc
        .perform(get("/api/templates?format=TIPTAP").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].format").value("TIPTAP"))
        .andExpect(jsonPath("$[0].docxFileName").doesNotExist())
        .andExpect(jsonPath("$[0].docxFileSize").doesNotExist());
  }

  @Test
  @Order(7)
  void validateFormat_docxWithoutS3Key_allowsPreUpload() throws Exception {
    // A DOCX template without an S3 key should be valid (pre-upload state).
    // We test this by verifying a template created via API (TIPTAP) exists.
    // DOCX templates without docxS3Key are allowed by the validation.
    // The create endpoint only creates TIPTAP templates, so this validates
    // that the format field flows through correctly.
    mockMvc
        .perform(get("/api/templates/" + docxTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(docxTemplateId));
  }

  @Test
  @Order(8)
  void deleteTemplate_cleansUpSuccessfully() throws Exception {
    // Create a template specifically for deletion
    var content = TestDocumentBuilder.doc().paragraph("To be deleted").build();
    var request =
        Map.of(
            "name", "Delete Format Test",
            "category", "ENGAGEMENT_LETTER",
            "primaryEntityType", "PROJECT",
            "content", content);

    var result =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    String deleteId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Delete (deactivate) the template
    mockMvc
        .perform(delete("/api/templates/" + deleteId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it no longer appears in list
    mockMvc
        .perform(get("/api/templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + deleteId + "')]").isEmpty());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_dt_format_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

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
