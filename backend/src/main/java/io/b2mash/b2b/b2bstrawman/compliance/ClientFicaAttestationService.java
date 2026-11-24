package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.audit.SafeAuditDetails;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.DataRequestSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client FICA attestations (Phase 93 655A, §17.3.4, ADR-388): one {@code ACTIVE} row per client,
 * created when a take-on commit creates the client, from the row's FICA columns and the batch
 * attestation. The projection that reads them is 655B.
 *
 * <p>Takes the attestation's ids rather than the take-on sign-off entity, so {@code compliance}
 * does not depend on {@code takeon} (decision D-row "attestation service signature").
 */
@Service
public class ClientFicaAttestationService {

  /** The CDD facts of one client, as recorded in the previous system. */
  public record AttestedCdd(
      LocalDate cddCompletedDate,
      LocalDate lastReviewDate,
      FicaRiskRating riskRating,
      FicaClientType ficaClientType,
      BeneficialOwnershipStatus beneficialOwnershipStatus,
      FicaScreeningStatus screeningStatus,
      LocalDate screeningDate,
      SourceOfFundsStatus sourceOfFundsStatus,
      String cddRecordsLocation,
      String sourceSystemRef) {}

  /**
   * {@code GET /api/clients/{clientId}/fica-attestation} (Phase 93 655B, §17.3.5): the client's
   * active attestation — the §17.2.5 fields plus who attested ({@code attestedByName} / {@code
   * attestedByRole} / {@code statementVersion} are the sign-off's snapshot) and when. {@code
   * nextReviewDate} is null when {@code riskRating} is {@code UNRATED}.
   */
  public record ClientFicaAttestationResponse(
      UUID id,
      UUID clientId,
      FicaAttestationStatus status,
      UUID takeOnBatchId,
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
      String attestedByName,
      String attestedByRole,
      String statementVersion,
      Instant attestedAt) {}

  /** The batch attestation the client's row is covered by. */
  public record BatchAttestation(
      UUID takeOnBatchId, UUID signOffId, UUID attestedBy, Instant attestedAt) {}

  /** The firm's review intervals in months (FD-7), read once per batch. */
  public record ReviewIntervals(int low, int medium, int high) {

    public static ReviewIntervals of(DataRequestSettings settings) {
      return new ReviewIntervals(
          settings.getFicaReviewMonthsLow(),
          settings.getFicaReviewMonthsMedium(),
          settings.getFicaReviewMonthsHigh());
    }

    /** The interval of a rating; {@code UNRATED} has none. */
    int monthsFor(FicaRiskRating rating) {
      return switch (rating) {
        case LOW -> low;
        case MEDIUM -> medium;
        case HIGH -> high;
        case UNRATED -> throw new IllegalArgumentException("An unrated client has no review date");
      };
    }
  }

  static final String FICA_ATTESTED = "customer.fica_attested";

  private final ClientFicaAttestationRepository attestations;
  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final Clock clock;

  public ClientFicaAttestationService(
      ClientFicaAttestationRepository attestations,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      Clock clock) {
    this.attestations = attestations;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.clock = clock;
  }

  /** The current tenant's review intervals (defaults 36/24/12 when no settings row exists). */
  @Transactional(readOnly = true)
  public ReviewIntervals reviewIntervals() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDataRequest)
        .map(ReviewIntervals::of)
        .orElse(
            new ReviewIntervals(
                DataRequestSettings.DEFAULT_FICA_REVIEW_MONTHS_LOW,
                DataRequestSettings.DEFAULT_FICA_REVIEW_MONTHS_MEDIUM,
                DataRequestSettings.DEFAULT_FICA_REVIEW_MONTHS_HIGH));
  }

  /**
   * {@code next_review_date = UNRATED ? null : coalesce(lastReview, cddCompleted) +
   * months(rating)}.
   */
  public static LocalDate nextReviewDate(AttestedCdd cdd, ReviewIntervals intervals) {
    if (cdd.riskRating() == FicaRiskRating.UNRATED) {
      return null;
    }
    LocalDate from = cdd.lastReviewDate() != null ? cdd.lastReviewDate() : cdd.cddCompletedDate();
    return from.plusMonths(intervals.monthsFor(cdd.riskRating()));
  }

  /**
   * Creates the client's active attestation and audits {@code customer.fica_attested} with ids
   * only. Joins the caller's transaction (the take-on row's).
   */
  @Transactional
  public ClientFicaAttestation attest(
      UUID customerId,
      AttestedCdd cdd,
      BatchAttestation attestation,
      UUID takeOnRowId,
      ReviewIntervals intervals) {
    var saved =
        attestations.save(
            new ClientFicaAttestation(
                customerId,
                attestation.takeOnBatchId(),
                takeOnRowId,
                attestation.signOffId(),
                cdd.cddCompletedDate(),
                cdd.lastReviewDate(),
                cdd.riskRating(),
                cdd.ficaClientType(),
                cdd.beneficialOwnershipStatus(),
                cdd.screeningStatus(),
                cdd.screeningDate(),
                cdd.sourceOfFundsStatus(),
                cdd.cddRecordsLocation(),
                cdd.sourceSystemRef(),
                nextReviewDate(cdd, intervals),
                attestation.attestedBy(),
                attestation.attestedAt(),
                clock.instant()));
    var details =
        SafeAuditDetails.builder() // ids only — the PII-safe guard (R-5, §17.6.4)
            .put("attestation_id", saved.getId())
            .put("take_on_batch_id", attestation.takeOnBatchId())
            .build();
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(FICA_ATTESTED)
            // ADR-342: storage key, do not rename
            .entityType("customer")
            .entityId(customerId)
            .details(details)
            .build());
    return saved;
  }

  @Transactional(readOnly = true)
  public Optional<ClientFicaAttestation> active(UUID customerId) {
    return attestations.findByCustomerIdAndStatus(customerId, FicaAttestationStatus.ACTIVE);
  }

  /**
   * The client's active attestation with the sign-off snapshot, or a 404 when the client has none
   * (a client created in Kazi, or an unknown id).
   */
  @Transactional(readOnly = true)
  public ClientFicaAttestationResponse getActiveForClient(UUID clientId) {
    var a =
        active(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("FICA attestation", clientId));
    var signOff = attestations.findSignOffSnapshot(a.getSignOffId()).orElse(null);
    return new ClientFicaAttestationResponse(
        a.getId(),
        a.getCustomerId(),
        a.getStatus(),
        a.getTakeOnBatchId(),
        a.getCddCompletedDate(),
        a.getLastReviewDate(),
        a.getRiskRating(),
        a.getFicaClientType(),
        a.getBeneficialOwnershipStatus(),
        a.getScreeningStatus(),
        a.getScreeningDate(),
        a.getSourceOfFundsStatus(),
        a.getCddRecordsLocation(),
        a.getSourceSystemRef(),
        a.getNextReviewDate(),
        a.getAttestedBy(),
        signOff == null ? null : signOff.getMemberName(),
        signOff == null ? null : signOff.getRoleName(),
        signOff == null ? null : signOff.getStatementVersion(),
        a.getAttestedAt());
  }

  /**
   * The capability behind every FICA attestation read: {@code GET
   * /api/clients/{clientId}/fica-attestation}, {@code GET /api/clients/{clientId}/fica-status} and
   * the {@code ficaStatus=REVIEW} client list (§17.10 X1, D-152).
   */
  public static final String FICA_ATTESTATION_CAPABILITY = "CUSTOMER_MANAGEMENT";

  /**
   * Whether the current caller may see FICA attestation detail — on the client list (the rows'
   * review reasons, risk rating, next review date and CDD records location, D-148) and the review
   * queue ({@code ficaStatus=REVIEW}, D-152). Owner/admin hold the capability through their system
   * roles; the seeded Member role does not; custom roles only by grant.
   */
  public static boolean callerSeesFicaAttestationDetail() {
    return RequestScopes.hasCapability(FICA_ATTESTATION_CAPABILITY);
  }

  /** The active attestations of many clients in one query (for list projections, 655B). */
  @Transactional(readOnly = true)
  public Map<UUID, ClientFicaAttestation> activeFor(Collection<UUID> customerIds) {
    if (customerIds == null || customerIds.isEmpty()) {
      return Map.of();
    }
    return attestations
        .findByCustomerIdInAndStatus(customerIds, FicaAttestationStatus.ACTIVE)
        .stream()
        .collect(Collectors.toMap(ClientFicaAttestation::getCustomerId, Function.identity()));
  }
}
