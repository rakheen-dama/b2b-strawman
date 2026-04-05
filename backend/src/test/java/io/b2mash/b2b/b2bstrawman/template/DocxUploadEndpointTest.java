package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.ByteArrayOutputStream;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocxUploadEndpointTest {
  private static final String ORG_ID = "org_docx_upload_test";
  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DOCX Upload Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_docx_owner", "docx_owner@test.com", "DOCX Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_docx_member", "docx_member@test.com", "DOCX Member", "member");
  }

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
  void upload_validDocx_returns201WithFields() throws Exception {
    byte[] docxBytes = createTestDocx("Hello {{customer.name}}, project {{project.name}}!");
    MockMultipartFile file =
        new MockMultipartFile("file", "test-template.docx", DOCX_CONTENT_TYPE, docxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "Valid Upload Template")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.format").value("DOCX"))
        .andExpect(jsonPath("$.name").value("Valid Upload Template"))
        .andExpect(jsonPath("$.slug").value("valid-upload-template"))
        .andExpect(jsonPath("$.category").value("ENGAGEMENT_LETTER"))
        .andExpect(jsonPath("$.primaryEntityType").value("PROJECT"))
        .andExpect(jsonPath("$.discoveredFields").isArray())
        .andExpect(jsonPath("$.discoveredFields.length()").value(2));
  }

  @Test
  @Order(2)
  void upload_invalidMimeType_returns400() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "Not a docx".getBytes());

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "Bad Mime Template")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid file type"));
  }

  @Test
  @Order(3)
  void upload_missingName_returns400() throws Exception {
    byte[] docxBytes = createTestDocx("Hello {{customer.name}}");
    MockMultipartFile file =
        new MockMultipartFile("file", "test.docx", DOCX_CONTENT_TYPE, docxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(4)
  void upload_corruptDocx_returns400() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "corrupt.docx", DOCX_CONTENT_TYPE, "this is not a docx file".getBytes());

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "Corrupt Template")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Corrupt file"));
  }

  @Test
  @Order(5)
  void upload_asMember_returns403() throws Exception {
    byte[] docxBytes = createTestDocx("Hello {{customer.name}}");
    MockMultipartFile file =
        new MockMultipartFile("file", "test.docx", DOCX_CONTENT_TYPE, docxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "Member Upload Attempt")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_docx_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(6)
  void upload_duplicateSlug_handledGracefully() throws Exception {
    byte[] docxBytes = createTestDocx("Hello {{customer.name}}");

    // First upload
    MockMultipartFile file1 =
        new MockMultipartFile("file", "dup1.docx", DOCX_CONTENT_TYPE, docxBytes);
    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file1)
                .param("name", "Duplicate Slug Test")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("duplicate-slug-test"));

    // Second upload with same name - should get deduplicated slug
    MockMultipartFile file2 =
        new MockMultipartFile("file", "dup2.docx", DOCX_CONTENT_TYPE, docxBytes);
    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file2)
                .param("name", "Duplicate Slug Test")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("duplicate-slug-test-2"));
  }

  @Test
  @Order(7)
  void upload_validDocx_createsAuditEvent() throws Exception {
    byte[] docxBytes = createTestDocx("Audit test {{customer.name}}");
    MockMultipartFile file =
        new MockMultipartFile("file", "audit-test.docx", DOCX_CONTENT_TYPE, docxBytes);

    var result =
        mockMvc
            .perform(
                multipart("/api/templates/docx/upload")
                    .file(file)
                    .param("name", "Audit Event Template")
                    .param("category", "ENGAGEMENT_LETTER")
                    .param("entityType", "PROJECT")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
            .andExpect(status().isCreated())
            .andReturn();

    String templateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Verify audit event was created by querying the audit API
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/audit-events/document_template/" + templateId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].eventType").value("docx_template.uploaded"));
  }

  @Test
  @Order(8)
  void upload_exceedsMaxSize_returns400() throws Exception {
    // Create a byte array just over 10MB
    byte[] oversizedContent = new byte[10 * 1024 * 1024 + 1];
    MockMultipartFile file =
        new MockMultipartFile("file", "large.docx", DOCX_CONTENT_TYPE, oversizedContent);

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "Oversized Template")
                .param("category", "ENGAGEMENT_LETTER")
                .param("entityType", "PROJECT")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("File too large"));
  }

  @Test
  @Order(9)
  void upload_noFields_returns201WithEmptyDiscoveredFields() throws Exception {
    byte[] docxBytes = createTestDocx("No merge fields here, just plain text.");
    MockMultipartFile file =
        new MockMultipartFile("file", "no-fields.docx", DOCX_CONTENT_TYPE, docxBytes);

    mockMvc
        .perform(
            multipart("/api/templates/docx/upload")
                .file(file)
                .param("name", "No Fields Template")
                .param("category", "REPORT")
                .param("entityType", "CUSTOMER")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_docx_owner")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.format").value("DOCX"))
        .andExpect(jsonPath("$.discoveredFields").isArray())
        .andExpect(jsonPath("$.discoveredFields.length()").value(0));
  }
}
