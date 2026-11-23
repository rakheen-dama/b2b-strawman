package io.b2mash.b2b.b2bstrawman.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The record that customer due diligence for a client was performed by the firm in its previous
 * system, and where the records are (Phase 93 655A, §17.2.5, ADR-388). It is <b>not</b>
 * verification in Kazi; it is a pointer to records that must still exist (FICA s22, s23). Stored
 * evidence the FICA projection reads (655B) — the FICA status itself stays computed (ADR-348).
 *
 * <p>The {@code BillingRun} shape: no public status setter, the one transition {@link #supersede}
 * guarded. The constructor mirrors every table CHECK, so a bad shape fails before the insert.
 */
@Entity
@Table(name = "client_fica_attestations")
public class ClientFicaAttestation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FicaAttestationStatus status;

  @Column(name = "take_on_batch_id", nullable = false, updatable = false)
  private UUID takeOnBatchId;

  @Column(name = "take_on_row_id", nullable = false, updatable = false)
  private UUID takeOnRowId;

  @Column(name = "sign_off_id", nullable = false, updatable = false)
  private UUID signOffId;

  @Column(name = "cdd_completed_date", nullable = false, updatable = false)
  private LocalDate cddCompletedDate;

  @Column(name = "last_review_date", updatable = false)
  private LocalDate lastReviewDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_rating", nullable = false, length = 10, updatable = false)
  private FicaRiskRating riskRating;

  @Enumerated(EnumType.STRING)
  @Column(name = "fica_client_type", nullable = false, length = 30, updatable = false)
  private FicaClientType ficaClientType;

  @Enumerated(EnumType.STRING)
  @Column(name = "beneficial_ownership_status", nullable = false, length = 20, updatable = false)
  private BeneficialOwnershipStatus beneficialOwnershipStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "screening_status", nullable = false, length = 20, updatable = false)
  private FicaScreeningStatus screeningStatus;

  @Column(name = "screening_date", updatable = false)
  private LocalDate screeningDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_of_funds_status", nullable = false, length = 20, updatable = false)
  private SourceOfFundsStatus sourceOfFundsStatus;

  @Column(name = "cdd_records_location", nullable = false, length = 500, updatable = false)
  private String cddRecordsLocation;

  @Column(name = "source_system_ref", length = 200, updatable = false)
  private String sourceSystemRef;

  @Column(name = "next_review_date", updatable = false)
  private LocalDate nextReviewDate;

  @Column(name = "attested_by", nullable = false, updatable = false)
  private UUID attestedBy;

  @Column(name = "attested_at", nullable = false, updatable = false)
  private Instant attestedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  protected ClientFicaAttestation() {}

  /**
   * An {@code ACTIVE} attestation. {@code nextReviewDate} is null exactly when the rating is {@code
   * UNRATED} (CHECK {@code chk_fica_att_review}); a screening date is required unless {@code
   * NOT_SCREENED} (CHECK {@code chk_fica_att_screening_date}).
   */
  public ClientFicaAttestation(
      UUID customerId,
      UUID takeOnBatchId,
      UUID takeOnRowId,
      UUID signOffId,
      LocalDate cddCompletedDate,
      LocalDate lastReviewDate,
      FicaRiskRating riskRating,
      FicaClientType ficaClientType,
      BeneficialOwnershipStatus beneficialOwnershipStatus,
      FicaScreeningStatus screeningStatus,
      LocalDate screeningDate,
      SourceOfFundsStatus sourceOfFundsStatus,
      String cddRecordsLocation,
      String sourceSystemRef,
      LocalDate nextReviewDate,
      UUID attestedBy,
      Instant attestedAt,
      Instant createdAt) {
    this.customerId = Objects.requireNonNull(customerId, "customerId");
    this.takeOnBatchId = Objects.requireNonNull(takeOnBatchId, "takeOnBatchId");
    this.takeOnRowId = Objects.requireNonNull(takeOnRowId, "takeOnRowId");
    this.signOffId = Objects.requireNonNull(signOffId, "signOffId");
    this.cddCompletedDate = Objects.requireNonNull(cddCompletedDate, "cddCompletedDate");
    this.lastReviewDate = lastReviewDate;
    this.riskRating = Objects.requireNonNull(riskRating, "riskRating");
    this.ficaClientType = Objects.requireNonNull(ficaClientType, "ficaClientType");
    this.beneficialOwnershipStatus =
        Objects.requireNonNull(beneficialOwnershipStatus, "beneficialOwnershipStatus");
    this.screeningStatus = Objects.requireNonNull(screeningStatus, "screeningStatus");
    this.screeningDate = screeningDate;
    this.sourceOfFundsStatus =
        sourceOfFundsStatus == null ? SourceOfFundsStatus.NOT_REQUIRED : sourceOfFundsStatus;
    this.cddRecordsLocation = Objects.requireNonNull(cddRecordsLocation, "cddRecordsLocation");
    this.sourceSystemRef = sourceSystemRef;
    this.nextReviewDate = nextReviewDate;
    this.attestedBy = Objects.requireNonNull(attestedBy, "attestedBy");
    this.attestedAt = Objects.requireNonNull(attestedAt, "attestedAt");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.status = FicaAttestationStatus.ACTIVE;
    if (screeningStatus != FicaScreeningStatus.NOT_SCREENED && screeningDate == null) {
      throw new IllegalStateException("A screened client needs the screening date.");
    }
    if ((riskRating == FicaRiskRating.UNRATED) != (nextReviewDate == null)) {
      throw new IllegalStateException(
          "A review date is set for a risk-rated client, and only for one.");
    }
  }

  /** A re-attestation replaced this one (655B+). Only an active attestation can be superseded. */
  public void supersede(Instant now) {
    if (status != FicaAttestationStatus.ACTIVE) {
      throw new IllegalStateException("Only an active FICA attestation can be superseded.");
    }
    this.status = FicaAttestationStatus.SUPERSEDED;
    this.supersededAt = Objects.requireNonNull(now, "now");
  }

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public FicaAttestationStatus getStatus() {
    return status;
  }

  public UUID getTakeOnBatchId() {
    return takeOnBatchId;
  }

  public UUID getTakeOnRowId() {
    return takeOnRowId;
  }

  public UUID getSignOffId() {
    return signOffId;
  }

  public LocalDate getCddCompletedDate() {
    return cddCompletedDate;
  }

  public LocalDate getLastReviewDate() {
    return lastReviewDate;
  }

  public FicaRiskRating getRiskRating() {
    return riskRating;
  }

  public FicaClientType getFicaClientType() {
    return ficaClientType;
  }

  public BeneficialOwnershipStatus getBeneficialOwnershipStatus() {
    return beneficialOwnershipStatus;
  }

  public FicaScreeningStatus getScreeningStatus() {
    return screeningStatus;
  }

  public LocalDate getScreeningDate() {
    return screeningDate;
  }

  public SourceOfFundsStatus getSourceOfFundsStatus() {
    return sourceOfFundsStatus;
  }

  public String getCddRecordsLocation() {
    return cddRecordsLocation;
  }

  public String getSourceSystemRef() {
    return sourceSystemRef;
  }

  public LocalDate getNextReviewDate() {
    return nextReviewDate;
  }

  public UUID getAttestedBy() {
    return attestedBy;
  }

  public Instant getAttestedAt() {
    return attestedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSupersededAt() {
    return supersededAt;
  }
}
