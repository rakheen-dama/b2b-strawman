package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Firm letterhead, contact, banking and signatory group (Phase 88 U8, D5, ADR-353). Printed on
 * generated documents through {@code TemplateContextHelper.buildOrgContext} ({@code org.address},
 * {@code org.addressLines}, {@code org.phone}, {@code org.email}, {@code org.website}, {@code
 * org.bankingDetails.*}, {@code org.signatory.*}). The VAT number stays on {@link TaxSettings};
 * logo and brand colour stay on {@link BrandingSettings}.
 *
 * <p>Persisted inline on {@code org_settings} via {@code @Embedded} + {@code @AttributeOverride} on
 * {@link OrgSettings}; all sixteen columns are nullable (tenant {@code V7__phase88_letterhead}).
 * Because every column is nullable, a row with no letterhead materialises a NULL embedded on reload
 * — {@link OrgSettings#getLetterhead()} lazily re-creates it (guarded by {@code
 * OrgSettingsEmbeddableNullReloadTest}).
 *
 * <p>{@link #updateLetterhead} is the domain mutator {@code OrgSettingsService} calls (full
 * replace, mirroring {@link TaxSettings#updateTaxSettings}). Neither it nor the setters bump {@code
 * OrgSettings.updatedAt}; the entity's {@code @PreUpdate} callback refreshes it on every dirty
 * flush.
 */
@Embeddable
public class LetterheadSettings {

  @Column(name = "letterhead_address_line1", length = 255)
  private String addressLine1;

  @Column(name = "letterhead_address_line2", length = 255)
  private String addressLine2;

  @Column(name = "letterhead_address_line3", length = 255)
  private String addressLine3;

  @Column(name = "letterhead_city", length = 100)
  private String city;

  @Column(name = "letterhead_postal_code", length = 20)
  private String postalCode;

  @Column(name = "letterhead_country", length = 100)
  private String country;

  @Column(name = "letterhead_phone", length = 50)
  private String phone;

  @Column(name = "letterhead_email", length = 255)
  private String email;

  @Column(name = "letterhead_website", length = 255)
  private String website;

  @Column(name = "letterhead_bank_name", length = 100)
  private String bankName;

  @Column(name = "letterhead_bank_branch_code", length = 20)
  private String bankBranchCode;

  @Column(name = "letterhead_bank_account_number", length = 50)
  private String bankAccountNumber;

  @Column(name = "letterhead_bank_account_type", length = 30)
  private String bankAccountType;

  @Column(name = "letterhead_payment_reference_note", columnDefinition = "TEXT")
  private String paymentReferenceNote;

  @Column(name = "letterhead_signatory_name", length = 255)
  private String signatoryName;

  @Column(name = "letterhead_signatory_designation", length = 255)
  private String signatoryDesignation;

  protected LetterheadSettings() {}

  /**
   * Full replace of all sixteen letterhead fields (Phase 88 U8, R-2): every value is written as
   * given, so a {@code null} clears the stored value. Callers pass already-normalised values
   * (trimmed, blank → null — {@code UpdateLetterheadSettingsRequest} does this).
   */
  public void updateLetterhead(
      String addressLine1,
      String addressLine2,
      String addressLine3,
      String city,
      String postalCode,
      String country,
      String phone,
      String email,
      String website,
      String bankName,
      String bankBranchCode,
      String bankAccountNumber,
      String bankAccountType,
      String paymentReferenceNote,
      String signatoryName,
      String signatoryDesignation) {
    this.addressLine1 = addressLine1;
    this.addressLine2 = addressLine2;
    this.addressLine3 = addressLine3;
    this.city = city;
    this.postalCode = postalCode;
    this.country = country;
    this.phone = phone;
    this.email = email;
    this.website = website;
    this.bankName = bankName;
    this.bankBranchCode = bankBranchCode;
    this.bankAccountNumber = bankAccountNumber;
    this.bankAccountType = bankAccountType;
    this.paymentReferenceNote = paymentReferenceNote;
    this.signatoryName = signatoryName;
    this.signatoryDesignation = signatoryDesignation;
  }

  /**
   * Non-blank address lines in print order: line 1, line 2, line 3, "city postal-code" (one line,
   * whichever parts are set), country. Empty (never null) when nothing is set.
   */
  public List<String> addressLines() {
    return Stream.of(addressLine1, addressLine2, addressLine3, cityPostal(), country)
        .filter(LetterheadSettings::hasText)
        .map(String::strip)
        .toList();
  }

  /**
   * {@link #addressLines()} joined with {@code ", "} for single-line use. Returns the empty string
   * (never null) when no address is set — callers decide whether an empty address is printed.
   */
  public String addressJoined() {
    return String.join(", ", addressLines());
  }

  private String cityPostal() {
    return Stream.of(city, postalCode)
        .filter(LetterheadSettings::hasText)
        .map(String::strip)
        .collect(Collectors.joining(" "));
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  public String getAddressLine1() {
    return addressLine1;
  }

  public void setAddressLine1(String addressLine1) {
    this.addressLine1 = addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public void setAddressLine2(String addressLine2) {
    this.addressLine2 = addressLine2;
  }

  public String getAddressLine3() {
    return addressLine3;
  }

  public void setAddressLine3(String addressLine3) {
    this.addressLine3 = addressLine3;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBankBranchCode() {
    return bankBranchCode;
  }

  public void setBankBranchCode(String bankBranchCode) {
    this.bankBranchCode = bankBranchCode;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public void setBankAccountNumber(String bankAccountNumber) {
    this.bankAccountNumber = bankAccountNumber;
  }

  public String getBankAccountType() {
    return bankAccountType;
  }

  public void setBankAccountType(String bankAccountType) {
    this.bankAccountType = bankAccountType;
  }

  public String getPaymentReferenceNote() {
    return paymentReferenceNote;
  }

  public void setPaymentReferenceNote(String paymentReferenceNote) {
    this.paymentReferenceNote = paymentReferenceNote;
  }

  public String getSignatoryName() {
    return signatoryName;
  }

  public void setSignatoryName(String signatoryName) {
    this.signatoryName = signatoryName;
  }

  public String getSignatoryDesignation() {
    return signatoryDesignation;
  }

  public void setSignatoryDesignation(String signatoryDesignation) {
    this.signatoryDesignation = signatoryDesignation;
  }
}
