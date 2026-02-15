package io.b2mash.b2b.b2bstrawman.settings;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsController.SettingsResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class OrgSettingsService {

  private static final Logger log = LoggerFactory.getLogger(OrgSettingsService.class);
  private static final String DEFAULT_CURRENCY = "USD";

  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final S3Client s3Client;
  private final S3PresignedUrlService s3PresignedUrlService;
  private final String bucketName;

  public OrgSettingsService(
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      S3Client s3Client,
      S3PresignedUrlService s3PresignedUrlService,
      S3Properties s3Properties) {
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.s3Client = s3Client;
    this.s3PresignedUrlService = s3PresignedUrlService;
    this.bucketName = s3Properties.bucketName();
  }

  /**
   * Returns the org settings for the current tenant. Never returns null — if no row exists, returns
   * a default response with "USD" as the currency without persisting.
   */
  @Transactional(readOnly = true)
  public OrgSettingsResponse getSettings() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(s -> new OrgSettingsResponse(s.getDefaultCurrency()))
        .orElse(new OrgSettingsResponse(DEFAULT_CURRENCY));
  }

  /** Returns settings with branding information (logoUrl, brandColor, documentFooterText). */
  @Transactional(readOnly = true)
  public SettingsResponse getSettingsWithBranding() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(
            s -> {
              String logoUrl = generateLogoUrl(s.getLogoS3Key());
              return new SettingsResponse(
                  s.getDefaultCurrency(), logoUrl, s.getBrandColor(), s.getDocumentFooterText());
            })
        .orElse(new SettingsResponse(DEFAULT_CURRENCY, null, null, null));
  }

  /** Updates settings including branding fields. */
  @Transactional
  public SettingsResponse updateSettingsWithBranding(
      String defaultCurrency,
      String brandColor,
      String documentFooterText,
      UUID memberId,
      String orgRole) {
    requireAdminOrOwner(orgRole);

    var existing = orgSettingsRepository.findForCurrentTenant();

    OrgSettings settings;

    if (existing.isPresent()) {
      settings = existing.get();
      settings.updateCurrency(defaultCurrency);
    } else {
      settings = new OrgSettings(defaultCurrency);
    }

    settings.setBrandColor(brandColor);
    settings.setDocumentFooterText(documentFooterText);
    settings = orgSettingsRepository.save(settings);

    log.info(
        "Updated org settings with branding: currency={}, brandColor={}",
        defaultCurrency,
        brandColor);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.updated")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(
                Map.of(
                    "default_currency",
                    defaultCurrency,
                    "brand_color",
                    brandColor != null ? brandColor : ""))
            .build());

    String logoUrl = generateLogoUrl(settings.getLogoS3Key());
    return new SettingsResponse(
        settings.getDefaultCurrency(),
        logoUrl,
        settings.getBrandColor(),
        settings.getDocumentFooterText());
  }

  /** Uploads a logo to S3 and updates the org settings. */
  @Transactional
  public SettingsResponse uploadLogo(MultipartFile file, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    String tenantId = RequestScopes.TENANT_ID.get();
    String ext = extensionFromContentType(file.getContentType());
    String s3Key = "org/" + tenantId + "/branding/logo." + ext;

    try {
      var putRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(s3Key)
              .contentType(file.getContentType())
              .build();

      s3Client.putObject(
          putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to upload logo to S3", e);
    }

    OrgSettings settings;
    try {
      settings =
          orgSettingsRepository
              .findForCurrentTenant()
              .orElseGet(
                  () -> {
                    var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                    return orgSettingsRepository.save(newSettings);
                  });

      settings.setLogoS3Key(s3Key);
      settings = orgSettingsRepository.save(settings);
    } catch (RuntimeException e) {
      // DB save failed — clean up the orphaned S3 object
      log.warn("DB save failed after S3 upload, deleting orphaned object: {}", s3Key);
      try {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(s3Key).build());
      } catch (RuntimeException s3ex) {
        log.error("Failed to clean up orphaned S3 object: {}", s3Key, s3ex);
      }
      throw e;
    }

    log.info("Uploaded logo for tenant {}: {}", tenantId, s3Key);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("org_settings.logo_uploaded")
            .entityType("org_settings")
            .entityId(settings.getId())
            .details(Map.of("s3_key", s3Key))
            .build());

    String logoUrl = generateLogoUrl(settings.getLogoS3Key());
    return new SettingsResponse(
        settings.getDefaultCurrency(),
        logoUrl,
        settings.getBrandColor(),
        settings.getDocumentFooterText());
  }

  /** Deletes the logo from S3 and clears the logoS3Key in org settings. */
  @Transactional
  public SettingsResponse deleteLogo(UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings(DEFAULT_CURRENCY);
                  return orgSettingsRepository.save(newSettings);
                });

    String oldKey = settings.getLogoS3Key();
    if (oldKey != null) {
      var deleteRequest = DeleteObjectRequest.builder().bucket(bucketName).key(oldKey).build();
      s3Client.deleteObject(deleteRequest);
      settings.setLogoS3Key(null);
      settings = orgSettingsRepository.save(settings);

      log.info("Deleted logo: {}", oldKey);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("org_settings.logo_deleted")
              .entityType("org_settings")
              .entityId(settings.getId())
              .details(Map.of("deleted_s3_key", oldKey))
              .build());
    }

    return new SettingsResponse(
        settings.getDefaultCurrency(),
        null,
        settings.getBrandColor(),
        settings.getDocumentFooterText());
  }

  /**
   * Returns the stored default currency for the current tenant, or "USD" if no settings row exists.
   * Useful for other services that need the org default currency.
   */
  @Transactional(readOnly = true)
  public String getDefaultCurrency() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultCurrency)
        .orElse(DEFAULT_CURRENCY);
  }

  private void requireAdminOrOwner(String orgRole) {
    if (!Roles.ORG_ADMIN.equals(orgRole) && !Roles.ORG_OWNER.equals(orgRole)) {
      throw new ForbiddenException(
          "Insufficient permissions", "Only admins and owners can update org settings");
    }
  }

  private String generateLogoUrl(String logoS3Key) {
    if (logoS3Key == null) {
      return null;
    }
    try {
      return s3PresignedUrlService.generateDownloadUrl(logoS3Key).url();
    } catch (IllegalArgumentException e) {
      // Key doesn't match expected pattern; generate URL directly
      log.warn(
          "Logo S3 key does not match presigned URL pattern, generating directly: {}", logoS3Key);
      return null;
    }
  }

  private static final Map<String, String> CONTENT_TYPE_TO_EXT =
      Map.of("image/png", "png", "image/jpeg", "jpg", "image/svg+xml", "svg");

  private String extensionFromContentType(String contentType) {
    if (contentType == null) {
      return "png"; // default
    }
    return CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "png");
  }

  /** Response DTO for org settings. */
  public record OrgSettingsResponse(String defaultCurrency) {}
}
