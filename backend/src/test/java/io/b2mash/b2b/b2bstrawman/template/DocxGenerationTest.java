package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocxGenerationTest {
  private static final String ORG_ID = "org_docx_gen_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ObjectMapper objectMapper;

  private String memberIdOwner;
  private String docxTemplateId;
  private String tiptapTemplateId;
  private String projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DOCX Gen Test Org", null);

    memberIdOwner =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_docx_gen_owner",
            "docx_gen_owner@test.com",
            "DOCX Gen Owner",
            "owner");

    // Create a project for context resolution
    var projectBody =
        objectMapper.writeValueAsString(
            Map.of("name", "Test Project Alpha", "description", "For DOCX generation tests"));
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(projectBody))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

    // Create a DOCX template by uploading a .docx file
    byte[] docxBytes = createTestDocx("Hello {{project.name}}, from {{customer.name}}");
    var docxFile =
        new MockMultipartFile(
            "file",
            "test-template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docxBytes);
    var uploadResult =
        mockMvc
            .perform(
                multipart("/api/templates/docx/upload")
                    .file(docxFile)
                    .param("name", "Test DOCX Template")
                    .param("category", "ENGAGEMENT_LETTER")
                    .param("entityType", "PROJECT")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner")))
            .andExpect(status().isCreated())
            .andReturn();
    docxTemplateId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

    // Create a Tiptap template for the format validation test
    Map<String, Object> tiptapContent =
        TestDocumentBuilder.doc().heading(1, "Dear").paragraph("Test content").build();
    var tiptapBody = new java.util.LinkedHashMap<String, Object>();
    tiptapBody.put("name", "Tiptap Template");
    tiptapBody.put("category", "ENGAGEMENT_LETTER");
    tiptapBody.put("primaryEntityType", "PROJECT");
    tiptapBody.put("content", tiptapContent);
    var tiptapResult =
        mockMvc
            .perform(
                post("/api/templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(tiptapBody)))
            .andExpect(status().isCreated())
            .andReturn();
    tiptapTemplateId = JsonPath.read(tiptapResult.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(1)
  void generateDocx_validTemplate_returnsMergedDocument() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", docxTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.templateId").value(docxTemplateId))
        .andExpect(jsonPath("$.templateName").value("Test DOCX Template"))
        .andExpect(jsonPath("$.fileName").exists())
        .andExpect(jsonPath("$.downloadUrl").exists())
        .andExpect(jsonPath("$.fileSize").isNumber());
  }

  @Test
  @Order(2)
  void generateDocx_tiptapTemplate_returns400() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", tiptapTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(3)
  void generateDocx_templateNotFound_returns404() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(4)
  void generateDocx_entityNotFound_returns404() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", UUID.randomUUID()));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", docxTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(5)
  void generateDocx_setsOutputFormatDocx() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", docxTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outputFormat").value("DOCX"));
  }

  @Test
  @Order(6)
  void generateDocx_createsGeneratedDocumentRecord() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    var result =
        mockMvc
            .perform(
                post("/api/templates/{id}/generate-docx", docxTemplateId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    String generatedId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Verify the record exists via the generated documents list API
    mockMvc
        .perform(
            get("/api/generated-documents")
                .param("entityType", "PROJECT")
                .param("entityId", projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + generatedId + "')].fileName").exists());
  }

  @Test
  @Order(7)
  void generateDocx_createsAuditEvent() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    // Generate a DOCX document
    var result =
        mockMvc
            .perform(
                post("/api/templates/{id}/generate-docx", docxTemplateId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    String generatedId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Verify audit event exists via the audit API
    mockMvc
        .perform(
            get("/api/audit-events/generated_document/{entityId}", generatedId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].eventType").value("docx_document.generated"));
  }

  @Test
  @Order(8)
  void generateDocx_fileNameFollowsPattern() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    var result =
        mockMvc
            .perform(
                post("/api/templates/{id}/generate-docx", docxTemplateId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    String fileName = JsonPath.read(result.getResponse().getContentAsString(), "$.fileName");
    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    // Pattern: {slug}-{entity-name}-{date}.docx
    assertThat(fileName).endsWith("-" + today + ".docx");
    assertThat(fileName).startsWith("test-docx-template-");
    assertThat(fileName).contains("test-project-alpha");
  }

  @Test
  @Order(9)
  void generateDocx_outputFormatDocx_noPdfGenerated() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(Map.of("entityId", projectId, "outputFormat", "DOCX"));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", docxTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outputFormat").value("DOCX"))
        .andExpect(jsonPath("$.downloadUrl").exists())
        .andExpect(jsonPath("$.pdfDownloadUrl").doesNotExist());
  }

  @Test
  @Order(10)
  void generateDocx_outputFormatPdf_converterUnavailable_returnsDocxWithWarning() throws Exception {
    // In CI/test env, LibreOffice is not installed and docx4j may fail on simple DOCX
    // so this tests the graceful degradation path
    var requestBody =
        objectMapper.writeValueAsString(Map.of("entityId", projectId, "outputFormat", "PDF"));

    var result =
        mockMvc
            .perform(
                post("/api/templates/{id}/generate-docx", docxTemplateId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.downloadUrl").exists())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String outputFormat = JsonPath.read(responseBody, "$.outputFormat");

    // Either PDF conversion succeeded or graceful degradation to DOCX with warning
    if ("DOCX".equals(outputFormat)) {
      List<String> warnings = JsonPath.read(responseBody, "$.warnings");
      assertThat(warnings).contains("PDF conversion unavailable. DOCX output returned instead.");
    } else {
      assertThat(outputFormat).isEqualTo("PDF");
    }
  }

  @Test
  @Order(11)
  void generateDocx_outputFormatBoth_returnsBothOrDocxWithWarning() throws Exception {
    var requestBody =
        objectMapper.writeValueAsString(Map.of("entityId", projectId, "outputFormat", "BOTH"));

    var result =
        mockMvc
            .perform(
                post("/api/templates/{id}/generate-docx", docxTemplateId)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.downloadUrl").exists())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String outputFormat = JsonPath.read(responseBody, "$.outputFormat");

    // BOTH is request-only — stored format is always DOCX
    assertThat(outputFormat).isEqualTo("DOCX");

    // If converter was available, we get pdfDownloadUrl; otherwise a warning
    String pdfUrl = JsonPath.read(responseBody, "$.pdfDownloadUrl");
    List<String> warnings = JsonPath.read(responseBody, "$.warnings");

    if (pdfUrl == null) {
      assertThat(warnings).contains("PDF conversion unavailable. DOCX output returned instead.");
    } else {
      assertThat(pdfUrl).isNotEmpty();
    }
  }

  @Test
  @Order(12)
  void generateDocx_noOutputFormat_defaultsToDocx() throws Exception {
    // Omit outputFormat — should default to DOCX
    var requestBody = objectMapper.writeValueAsString(Map.of("entityId", projectId));

    mockMvc
        .perform(
            post("/api/templates/{id}/generate-docx", docxTemplateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outputFormat").value("DOCX"))
        .andExpect(jsonPath("$.pdfDownloadUrl").doesNotExist());
  }

  // --- Helpers ---

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
}
