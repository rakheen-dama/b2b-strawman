package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Branding / document-identity settings group (Wave 3.3 embeddable refactor). Holds the firm's logo
 * key, brand colour, and document footer text — the visual-identity fields applied to generated
 * documents, the customer portal, and outbound email.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}).
 *
 * <p>Setters here intentionally do NOT bump {@code OrgSettings.updatedAt}; the embeddable has no
 * reference to the owning entity's timestamp. Hibernate dirty-checks the embedded columns and
 * persists changes regardless. Callers that need an explicit {@code updatedAt} bump should mutate
 * through an {@code OrgSettings} domain method.
 */
@Embeddable
public class BrandingSettings {

  @Column(name = "logo_s3_key", length = 500)
  private String logoS3Key;

  @Column(name = "brand_color", length = 7)
  private String brandColor;

  @Column(name = "document_footer_text", columnDefinition = "TEXT")
  private String documentFooterText;

  protected BrandingSettings() {}

  public String getLogoS3Key() {
    return logoS3Key;
  }

  public void setLogoS3Key(String logoS3Key) {
    this.logoS3Key = logoS3Key;
  }

  public String getBrandColor() {
    return brandColor;
  }

  public void setBrandColor(String brandColor) {
    this.brandColor = brandColor;
  }

  public String getDocumentFooterText() {
    return documentFooterText;
  }

  public void setDocumentFooterText(String documentFooterText) {
    this.documentFooterText = documentFooterText;
  }
}
