package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

@ExtendWith(MockitoExtension.class)
class AcceptanceCertificateServiceTest {

  @Mock private GeneratedDocumentRepository generatedDocumentRepository;
  @Mock private PortalContactRepository portalContactRepository;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private StorageService storageService;
  @Mock private PdfRenderingService pdfRenderingService;
  @Mock private TemplateEngine templateEngine;

  @InjectMocks private AcceptanceCertificateService certificateService;

  private UUID docId;
  private UUID contactId;
  private UUID memberId;
  private AcceptanceRequest request;
  private GeneratedDocument genDoc;
  private PortalContact contact;

  @BeforeEach
  void setUp() {
    docId = UUID.randomUUID();
    contactId = UUID.randomUUID();
    memberId = UUID.randomUUID();

    // Create a request in SENT state, then manually set ACCEPTED fields
    request =
        new AcceptanceRequest(
            docId,
            contactId,
            UUID.randomUUID(),
            "test-token",
            Instant.now().plus(30, ChronoUnit.DAYS),
            memberId);
    request.markSent();
    request.markViewed(Instant.now());
    request.markAccepted("Jane Doe", "10.0.0.1", "Mozilla/5.0");

    genDoc =
        new GeneratedDocument(
            UUID.randomUUID(),
            TemplateEntityType.CUSTOMER,
            UUID.randomUUID(),
            "engagement-letter-acme-2026-02-27.pdf",
            "org/tenant_abc/generated/engagement-letter-acme.pdf",
            12345L,
            memberId);

    contact =
        new PortalContact(
            "org_test123",
            UUID.randomUUID(),
            "jane.doe@acme.com",
            "Jane Doe",
            PortalContact.ContactRole.PRIMARY);
  }

  @Test
  void generateCertificate_computes_hash_and_renders_pdf() {
    byte[] fakePdfBytes = "%PDF-fake".getBytes(StandardCharsets.UTF_8);
    byte[] fakeCertPdf = "%PDF-cert".getBytes(StandardCharsets.UTF_8);
    String tenantSchema = "tenant_abc123";

    when(generatedDocumentRepository.findById(docId)).thenReturn(Optional.of(genDoc));
    when(storageService.download(genDoc.getS3Key())).thenReturn(fakePdfBytes);
    when(portalContactRepository.findById(contactId)).thenReturn(Optional.of(contact));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_test123")).thenReturn(Optional.empty());
    when(templateEngine.process(eq("certificates/certificate-of-acceptance"), any(IContext.class)))
        .thenReturn("<html><body>Certificate HTML</body></html>");
    when(pdfRenderingService.htmlToPdf(anyString())).thenReturn(fakeCertPdf);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, "org_test123")
        .run(() -> certificateService.generateCertificate(request, tenantSchema));

    // Verify template was rendered
    verify(templateEngine)
        .process(eq("certificates/certificate-of-acceptance"), any(IContext.class));
    // Verify PDF conversion was called
    verify(pdfRenderingService).htmlToPdf(anyString());
  }

  @Test
  void generateCertificate_uploads_to_correct_s3_key() {
    byte[] fakePdfBytes = "%PDF-fake".getBytes(StandardCharsets.UTF_8);
    byte[] fakeCertPdf = "%PDF-cert".getBytes(StandardCharsets.UTF_8);
    String tenantSchema = "tenant_abc123";
    UUID reqId = request.getId();

    when(generatedDocumentRepository.findById(docId)).thenReturn(Optional.of(genDoc));
    when(storageService.download(genDoc.getS3Key())).thenReturn(fakePdfBytes);
    when(portalContactRepository.findById(contactId)).thenReturn(Optional.of(contact));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_test123")).thenReturn(Optional.empty());
    when(templateEngine.process(anyString(), any(IContext.class)))
        .thenReturn("<html><body>Certificate</body></html>");
    when(pdfRenderingService.htmlToPdf(anyString())).thenReturn(fakeCertPdf);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, "org_test123")
        .run(() -> certificateService.generateCertificate(request, tenantSchema));

    String expectedKey = tenantSchema + "/certificates/" + reqId + "/certificate.pdf";
    verify(storageService).upload(eq(expectedKey), eq(fakeCertPdf), eq("application/pdf"));
  }

  @Test
  void generateCertificate_sets_certificate_fields_on_request() {
    byte[] fakePdfBytes = "%PDF-fake".getBytes(StandardCharsets.UTF_8);
    byte[] fakeCertPdf = "%PDF-cert".getBytes(StandardCharsets.UTF_8);
    String tenantSchema = "tenant_abc123";

    when(generatedDocumentRepository.findById(docId)).thenReturn(Optional.of(genDoc));
    when(storageService.download(genDoc.getS3Key())).thenReturn(fakePdfBytes);
    when(portalContactRepository.findById(contactId)).thenReturn(Optional.of(contact));
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_test123")).thenReturn(Optional.empty());
    when(templateEngine.process(anyString(), any(IContext.class)))
        .thenReturn("<html><body>Certificate</body></html>");
    when(pdfRenderingService.htmlToPdf(anyString())).thenReturn(fakeCertPdf);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, "org_test123")
        .run(() -> certificateService.generateCertificate(request, tenantSchema));

    assertThat(request.getCertificateS3Key())
        .isNotNull()
        .startsWith(tenantSchema + "/certificates/")
        .endsWith("/certificate.pdf");
    assertThat(request.getCertificateFileName())
        .isNotNull()
        .startsWith("Certificate-of-Acceptance-")
        .endsWith(".pdf");
  }

  @Test
  void sha256Hex_produces_correct_hash() {
    // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    byte[] helloBytes = "hello".getBytes(StandardCharsets.UTF_8);
    String expectedHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    String actualHash = certificateService.sha256Hex(helloBytes);

    assertThat(actualHash).isEqualTo(expectedHash);
  }

  @Test
  void generateCertificate_throws_when_document_not_found() {
    when(generatedDocumentRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, "tenant_test")
                    .call(
                        () -> {
                          certificateService.generateCertificate(request, "tenant_test");
                          return null;
                        }))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
