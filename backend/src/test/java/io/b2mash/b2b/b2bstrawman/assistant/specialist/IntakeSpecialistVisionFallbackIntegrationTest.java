package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.VisionContentBlock;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.assistant.tool.read.ExtractTextFromDocumentTool;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntakeSpecialistVisionFallbackIntegrationTest {

  private static final String ORG_ID = "org_intake_vision_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ExtractTextFromDocumentTool extractTextTool;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private StorageService storageService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Intake Vision Test Org", null);
    var memberStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_intake_vision_owner",
            "vision@test.com",
            "Vision Owner",
            "owner");
    memberId = UUID.fromString(memberStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runWithCaps(Set<String> caps, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, caps)
        .run(body);
  }

  /** Creates a real customer in the database and returns its ID. */
  private UUID createCustomerInDb(String name, String email) {
    return transactionTemplate.execute(
        status -> {
          var customer = createActiveCustomer(name, email, memberId);
          customer = customerRepository.save(customer);
          return customer.getId();
        });
  }

  @Test
  void textLayerExtraction_returnsTextAndHasTextLayerTrue() throws Exception {
    byte[] pdfBytes =
        createPdfWithText(
            "Test Company (Pty) Ltd\n"
                + "Registration Number: 2019/123456/07\n"
                + "Address: 123 Main Street, Sandton, Johannesburg, Gauteng, 2196\n"
                + "Contact: John Smith, Director\n"
                + "Email: john.smith@testcompany.co.za\n"
                + "Phone: +27 11 234 5678\n"
                + "Tax Number: 9876543210");

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW"),
        () -> {
          var customerId = createCustomerInDb("Vision Text Customer", "vtext@test.com");
          var document =
              transactionTemplate.execute(
                  status -> {
                    var doc =
                        new Document(
                            Document.Scope.CUSTOMER,
                            null,
                            customerId,
                            "test.pdf",
                            "application/pdf",
                            pdfBytes.length,
                            memberId,
                            Document.Visibility.INTERNAL);
                    doc = documentRepository.save(doc);
                    String s3Key = "test/intake/" + doc.getId() + ".pdf";
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    doc = documentRepository.save(doc);
                    storageService.upload(s3Key, pdfBytes, "application/pdf");
                    return doc;
                  });

          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_VIEW"));
          var input = Map.<String, Object>of("documentId", document.getId().toString());

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) extractTextTool.execute(input, ctx);

          assertThat(result.get("hasTextLayer")).isEqualTo(true);
          assertThat((int) result.get("characterCount")).isGreaterThanOrEqualTo(200);
          assertThat((String) result.get("text")).contains("Test Company");
          assertThat((String) result.get("text")).contains("2019/123456/07");
        });
  }

  @Test
  void emptyPdf_returnsHasTextLayerFalse() throws Exception {
    byte[] pdfBytes = createBlankPdf();

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW"),
        () -> {
          var customerId = createCustomerInDb("Vision Blank Customer", "vblank@test.com");
          var document =
              transactionTemplate.execute(
                  status -> {
                    var doc =
                        new Document(
                            Document.Scope.CUSTOMER,
                            null,
                            customerId,
                            "blank.pdf",
                            "application/pdf",
                            pdfBytes.length,
                            memberId,
                            Document.Visibility.INTERNAL);
                    doc = documentRepository.save(doc);
                    String s3Key = "test/intake/blank/" + doc.getId() + ".pdf";
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    doc = documentRepository.save(doc);
                    storageService.upload(s3Key, pdfBytes, "application/pdf");
                    return doc;
                  });

          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_VIEW"));
          var input = Map.<String, Object>of("documentId", document.getId().toString());

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) extractTextTool.execute(input, ctx);

          assertThat(result.get("hasTextLayer")).isEqualTo(false);
        });
  }

  @Test
  void sparseTextLayer_returnsHasTextLayerFalse_visionFallbackHint() throws Exception {
    // PDF with text below the 200-character threshold — should trigger vision fallback
    byte[] pdfBytes = createPdfWithText("Short text");

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW"),
        () -> {
          var customerId = createCustomerInDb("Vision Sparse Customer", "vsparse@test.com");
          var document =
              transactionTemplate.execute(
                  status -> {
                    var doc =
                        new Document(
                            Document.Scope.CUSTOMER,
                            null,
                            customerId,
                            "sparse.pdf",
                            "application/pdf",
                            pdfBytes.length,
                            memberId,
                            Document.Visibility.INTERNAL);
                    doc = documentRepository.save(doc);
                    String s3Key = "test/intake/sparse/" + doc.getId() + ".pdf";
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    doc = documentRepository.save(doc);
                    storageService.upload(s3Key, pdfBytes, "application/pdf");
                    return doc;
                  });

          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_VIEW"));
          var input = Map.<String, Object>of("documentId", document.getId().toString());

          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) extractTextTool.execute(input, ctx);

          // Text layer exists but is below the 200-char threshold → hasTextLayer=false
          // This signals the LLM to trigger the vision fallback path
          assertThat(result.get("hasTextLayer")).isEqualTo(false);
          assertThat((int) result.get("characterCount")).isGreaterThan(0);
          assertThat((int) result.get("characterCount")).isLessThan(200);
        });
  }

  @Test
  void visionContentBlock_serializesToDocumentFormat() {
    var base64Data = Base64.getEncoder().encodeToString("fake-pdf-bytes".getBytes());
    var block = new VisionContentBlock("application/pdf", base64Data);

    assertThat(block.mediaType()).isEqualTo("application/pdf");
    assertThat(block.base64Data()).isEqualTo(base64Data);

    var msg =
        new ChatMessage("user", "Extract fields from this document", List.of(), List.of(block));
    assertThat(msg.visionBlocks()).hasSize(1);
    assertThat(msg.visionBlocks().getFirst().mediaType()).isEqualTo("application/pdf");
  }

  @Test
  @SuppressWarnings("unchecked")
  void documentExceedingMaxPages_returnsError() throws Exception {
    byte[] pdfBytes = createMultiPagePdf(51); // Exceeds 50-page default threshold

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW"),
        () -> {
          var customerId = createCustomerInDb("Vision MedLarge Customer", "vmedlarge@test.com");
          var document =
              transactionTemplate.execute(
                  status -> {
                    var doc =
                        new Document(
                            Document.Scope.CUSTOMER,
                            null,
                            customerId,
                            "medlarge.pdf",
                            "application/pdf",
                            pdfBytes.length,
                            memberId,
                            Document.Visibility.INTERNAL);
                    doc = documentRepository.save(doc);
                    String s3Key = "test/intake/medlarge/" + doc.getId() + ".pdf";
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    doc = documentRepository.save(doc);
                    storageService.upload(s3Key, pdfBytes, "application/pdf");
                    return doc;
                  });

          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_VIEW"));
          var input = Map.<String, Object>of("documentId", document.getId().toString());

          var result = (Map<String, Object>) extractTextTool.execute(input, ctx);

          assertThat(result.get("is_error")).isEqualTo(true);
          assertThat(result.get("errorMessage")).isEqualTo("DOCUMENT_TOO_LARGE");
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void documentExceedingHardCap_returnsError() throws Exception {
    byte[] pdfBytes = createMultiPagePdf(101);

    runWithCaps(
        Set.of("AI_ASSISTANT_USE", "CUSTOMER_VIEW"),
        () -> {
          var customerId = createCustomerInDb("Vision Large Customer", "vlarge@test.com");
          var document =
              transactionTemplate.execute(
                  status -> {
                    var doc =
                        new Document(
                            Document.Scope.CUSTOMER,
                            null,
                            customerId,
                            "large.pdf",
                            "application/pdf",
                            pdfBytes.length,
                            memberId,
                            Document.Visibility.INTERNAL);
                    doc = documentRepository.save(doc);
                    String s3Key = "test/intake/large/" + doc.getId() + ".pdf";
                    doc.assignS3Key(s3Key);
                    doc.confirmUpload();
                    doc = documentRepository.save(doc);
                    storageService.upload(s3Key, pdfBytes, "application/pdf");
                    return doc;
                  });

          var ctx = new TenantToolContext(tenantSchema, memberId, "owner", Set.of("CUSTOMER_VIEW"));
          var input = Map.<String, Object>of("documentId", document.getId().toString());

          var result = (Map<String, Object>) extractTextTool.execute(input, ctx);

          assertThat(result.get("is_error")).isEqualTo(true);
          assertThat(result.get("errorMessage")).isEqualTo("DOCUMENT_TOO_LARGE");
        });
  }

  private static byte[] createPdfWithText(String text) throws Exception {
    try (var doc = new PDDocument()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(50, 700);
        for (String line : text.split("\n")) {
          cs.showText(line);
          cs.newLineAtOffset(0, -15);
        }
        cs.endText();
      }
      var baos = new ByteArrayOutputStream();
      doc.save(baos);
      return baos.toByteArray();
    }
  }

  private static byte[] createBlankPdf() throws Exception {
    try (var doc = new PDDocument()) {
      doc.addPage(new PDPage());
      var baos = new ByteArrayOutputStream();
      doc.save(baos);
      return baos.toByteArray();
    }
  }

  private static byte[] createMultiPagePdf(int pageCount) throws Exception {
    try (var doc = new PDDocument()) {
      for (int i = 0; i < pageCount; i++) {
        doc.addPage(new PDPage());
      }
      var baos = new ByteArrayOutputStream();
      doc.save(baos);
      return baos.toByteArray();
    }
  }
}
