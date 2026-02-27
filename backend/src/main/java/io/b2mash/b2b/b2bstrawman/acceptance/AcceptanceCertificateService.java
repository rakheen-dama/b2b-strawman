package io.b2mash.b2b.b2bstrawman.acceptance;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Generates the Certificate of Acceptance PDF for an accepted document request. */
@Service
public class AcceptanceCertificateService {

  private static final Logger log = LoggerFactory.getLogger(AcceptanceCertificateService.class);
  private static final Duration LOGO_URL_EXPIRY = Duration.ofHours(1);
  private static final String DEFAULT_BRAND_COLOR = "#1a5276";

  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final StorageService storageService;
  private final PdfRenderingService pdfRenderingService;
  private final TemplateEngine templateEngine;

  public AcceptanceCertificateService(
      GeneratedDocumentRepository generatedDocumentRepository,
      PortalContactRepository portalContactRepository,
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      StorageService storageService,
      PdfRenderingService pdfRenderingService,
      TemplateEngine templateEngine) {
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.portalContactRepository = portalContactRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.storageService = storageService;
    this.pdfRenderingService = pdfRenderingService;
    this.templateEngine = templateEngine;
  }

  /**
   * Generates a Certificate of Acceptance PDF and stores it in S3. Sets certificateS3Key and
   * certificateFileName on the request after successful upload.
   *
   * @param request the accepted AcceptanceRequest (must be in ACCEPTED status)
   * @param tenantSchema the current tenant schema name
   */
  public void generateCertificate(AcceptanceRequest request, String tenantSchema) {
    // 1. Fetch original document metadata
    GeneratedDocument genDoc =
        generatedDocumentRepository
            .findById(request.getGeneratedDocumentId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "GeneratedDocument", request.getGeneratedDocumentId()));

    // 2. Fetch original PDF bytes from S3
    byte[] originalPdf = storageService.download(genDoc.getS3Key());

    // 3. Compute SHA-256 hash
    String documentHash = sha256Hex(originalPdf);

    // 4. Fetch portal contact for recipient info (graceful null handling)
    PortalContact contact =
        portalContactRepository.findById(request.getPortalContactId()).orElse(null);
    if (contact == null) {
      log.warn(
          "Portal contact not found for request={}, contactId={} — certificate will have missing recipient info",
          request.getId(),
          request.getPortalContactId());
    }

    // 5. Fetch org settings for branding
    OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElse(null);

    // 6. Resolve org name from global organizations table
    String orgName = resolveOrgName();

    // 7. Resolve logo presigned URL
    String orgLogo = resolveLogoUrl(settings);

    // 8. Resolve brand color
    String brandColor =
        settings != null && settings.getBrandColor() != null
            ? settings.getBrandColor()
            : DEFAULT_BRAND_COLOR;

    // 9. Build Thymeleaf context
    var ctx = new Context();
    ctx.setVariable("orgName", orgName);
    ctx.setVariable("orgLogo", orgLogo);
    ctx.setVariable("brandColor", brandColor);
    ctx.setVariable("documentTitle", genDoc.getFileName());
    ctx.setVariable("recipientName", contact != null ? contact.getDisplayName() : null);
    ctx.setVariable("recipientEmail", contact != null ? contact.getEmail() : null);
    ctx.setVariable("acceptorName", request.getAcceptorName());
    ctx.setVariable("acceptedAt", request.getAcceptedAt());
    ctx.setVariable("ipAddress", request.getAcceptorIpAddress());
    ctx.setVariable("userAgent", request.getAcceptorUserAgent());
    ctx.setVariable("documentHash", documentHash);
    ctx.setVariable("requestId", request.getId());
    ctx.setVariable("generatedAt", Instant.now());

    // 10. Render Thymeleaf template (classpath resolver — system-managed template)
    String html = templateEngine.process("certificates/certificate-of-acceptance", ctx);

    // 11. Convert HTML to PDF
    byte[] certPdf = pdfRenderingService.htmlToPdf(html);

    // 12. Build S3 key and filename
    String s3Key = tenantSchema + "/certificates/" + request.getId() + "/certificate.pdf";
    String fileName =
        "Certificate-of-Acceptance-"
            + slugify(genDoc.getFileName())
            + "-"
            + LocalDate.now()
            + ".pdf";

    // 13. Upload to S3
    storageService.upload(s3Key, certPdf, "application/pdf");

    // 14. Set references on request
    request.setCertificateS3Key(s3Key);
    request.setCertificateFileName(fileName);

    log.info(
        "Generated certificate for request={}, s3Key={}, size={}bytes",
        request.getId(),
        s3Key,
        certPdf.length);
  }

  String sha256Hex(byte[] data) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private String resolveOrgName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    if (orgId == null) {
      return "DocTeams";
    }
    return organizationRepository
        .findByClerkOrgId(orgId)
        .map(org -> org.getName())
        .orElse("DocTeams");
  }

  private String resolveLogoUrl(OrgSettings settings) {
    if (settings == null || settings.getLogoS3Key() == null) {
      return null;
    }
    try {
      return storageService.generateDownloadUrl(settings.getLogoS3Key(), LOGO_URL_EXPIRY).url();
    } catch (RuntimeException e) {
      log.warn("Failed to generate logo URL for certificate: {}", e.getMessage());
      return null;
    }
  }

  private String slugify(String text) {
    if (text == null || text.isBlank()) {
      return "document";
    }
    return text.toLowerCase()
        .replaceAll("[\\s]+", "-")
        .replaceAll("[^a-z0-9-]", "")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
