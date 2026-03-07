package io.b2mash.b2b.b2bstrawman.accessrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_requests", schema = "public")
public class AccessRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "organization_name", nullable = false, length = 200)
  private String organizationName;

  @Column(name = "country", nullable = false, length = 100)
  private String country;

  @Column(name = "industry", nullable = false, length = 100)
  private String industry;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AccessRequestStatus status;

  @Column(name = "otp_hash")
  private String otpHash;

  @Column(name = "otp_expires_at")
  private Instant otpExpiresAt;

  @Column(name = "otp_attempts", nullable = false)
  private int otpAttempts;

  @Column(name = "otp_verified_at")
  private Instant otpVerifiedAt;

  @Column(name = "reviewed_by")
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "keycloak_org_id")
  private String keycloakOrgId;

  @Column(name = "provisioning_error", columnDefinition = "TEXT")
  private String provisioningError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AccessRequest() {}

  public AccessRequest(
      String email, String fullName, String organizationName, String country, String industry) {
    this.email = email;
    this.fullName = fullName;
    this.organizationName = organizationName;
    this.country = country;
    this.industry = industry;
    this.status = AccessRequestStatus.PENDING_VERIFICATION;
    this.otpAttempts = 0;
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getIndustry() {
    return industry;
  }

  public void setIndustry(String industry) {
    this.industry = industry;
  }

  public AccessRequestStatus getStatus() {
    return status;
  }

  public void setStatus(AccessRequestStatus status) {
    this.status = status;
  }

  public String getOtpHash() {
    return otpHash;
  }

  public void setOtpHash(String otpHash) {
    this.otpHash = otpHash;
  }

  public Instant getOtpExpiresAt() {
    return otpExpiresAt;
  }

  public void setOtpExpiresAt(Instant otpExpiresAt) {
    this.otpExpiresAt = otpExpiresAt;
  }

  public int getOtpAttempts() {
    return otpAttempts;
  }

  public void setOtpAttempts(int otpAttempts) {
    this.otpAttempts = otpAttempts;
  }

  public Instant getOtpVerifiedAt() {
    return otpVerifiedAt;
  }

  public void setOtpVerifiedAt(Instant otpVerifiedAt) {
    this.otpVerifiedAt = otpVerifiedAt;
  }

  public String getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(String reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public String getKeycloakOrgId() {
    return keycloakOrgId;
  }

  public void setKeycloakOrgId(String keycloakOrgId) {
    this.keycloakOrgId = keycloakOrgId;
  }

  public String getProvisioningError() {
    return provisioningError;
  }

  public void setProvisioningError(String provisioningError) {
    this.provisioningError = provisioningError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
