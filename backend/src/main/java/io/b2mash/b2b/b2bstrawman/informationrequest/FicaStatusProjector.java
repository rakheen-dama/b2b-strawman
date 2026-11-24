package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.compliance.BeneficialOwnershipStatus;
import io.b2mash.b2b.b2bstrawman.compliance.ClientFicaAttestation;
import io.b2mash.b2b.b2bstrawman.compliance.FicaAttestationStatus;
import io.b2mash.b2b.b2bstrawman.compliance.FicaRiskRating;
import io.b2mash.b2b.b2bstrawman.compliance.FicaScreeningStatus;
import io.b2mash.b2b.b2bstrawman.compliance.SourceOfFundsStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The FICA onboarding status rule (GAP-L-46; checklist-first since Phase 88, ADR-348), shared by
 * the single-client read, the dashboard batch and the client list. Pure — no Spring, no
 * repositories.
 *
 * <p>Rule, in order:
 *
 * <ol>
 *   <li><b>Onboarding checklist instances</b> (templates with {@code auto_instantiate = true}; the
 *       caller passes only those), ignoring {@code CANCELLED}: any {@code IN_PROGRESS} → {@code
 *       IN_PROGRESS} with the newest open instance's id (an open obligation outranks a past
 *       completion, A2); otherwise the newest {@code COMPLETED} → {@code DONE} with its {@code
 *       completedAt} and id. The checklist branch outranks the information-request branch entirely.
 *   <li>Otherwise the <b>information-request rule</b>, unchanged: the client's requests on a FICA
 *       template, ignoring {@code CANCELLED}, newest first (by {@code createdAt}, then id). None →
 *       {@code NOT_STARTED}. The newest {@code COMPLETED} request wins: {@code DONE} when it has at
 *       least one item and every item is {@code ACCEPTED}, else {@code IN_PROGRESS}. No {@code
 *       COMPLETED} request → {@code IN_PROGRESS} with the newest live request's id. Returned only
 *       when it gives anything but {@code NOT_STARTED}.
 *   <li>Otherwise the client's <b>active FICA attestation</b> (Phase 93 655B, §17.3.4, ADR-388),
 *       when there is one: no {@link FicaReviewReason} applies → {@code ATTESTED}; one or more
 *       apply → {@code ATTESTED_REVIEW} with the reasons. Kazi evidence (steps 1–2) wins over an
 *       attestation (FD-29).
 *   <li>Otherwise {@code NOT_STARTED}.
 * </ol>
 *
 * <p>Consumers ask {@link #isOutstanding} / {@link #isClear}, never {@code != DONE}.
 */
public final class FicaStatusProjector {

  public static final String NOT_STARTED = "NOT_STARTED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String DONE = "DONE";

  /** Verified in the firm's previous system, attested at take-on, no review reason (655B). */
  public static final String ATTESTED = "ATTESTED";

  /** Attested at take-on, but at least one {@link FicaReviewReason} applies (655B). */
  public static final String ATTESTED_REVIEW = "ATTESTED_REVIEW";

  // ChecklistInstance.status is a String column (IN_PROGRESS / COMPLETED / CANCELLED).
  private static final String INSTANCE_IN_PROGRESS = "IN_PROGRESS";
  private static final String INSTANCE_COMPLETED = "COMPLETED";
  private static final String INSTANCE_CANCELLED = "CANCELLED";

  /** Newest first by {@code createdAt}, then id for determinism (mirrors the request rule). */
  private static final Comparator<ChecklistInstance> NEWEST_INSTANCE_FIRST =
      Comparator.comparing(ChecklistInstance::getCreatedAt)
          .reversed()
          .thenComparing(ChecklistInstance::getId);

  private FicaStatusProjector() {}

  /**
   * Projected FICA status. {@code completedAt} is set only for {@code DONE}. Exactly one of {@code
   * requestId} / {@code checklistInstanceId} / {@code attestationId} is set, except for {@code
   * NOT_STARTED} (all null): {@code checklistInstanceId} when the checklist branch decided, {@code
   * requestId} when the information-request branch did, {@code attestationId} when the attestation
   * did ({@code ATTESTED} / {@code ATTESTED_REVIEW}; {@code completedAt} stays null). {@code
   * reviewReasons} is never null and non-empty only for {@code ATTESTED_REVIEW}, in {@link
   * FicaReviewReason} declaration order.
   */
  public record Projection(
      String status,
      Instant completedAt,
      UUID requestId,
      UUID checklistInstanceId,
      List<String> reviewReasons,
      UUID attestationId) {}

  private static final Projection NOT_STARTED_PROJECTION =
      new Projection(NOT_STARTED, null, null, null, List.of(), null);

  /** {@code NOT_STARTED}, {@code IN_PROGRESS} or {@code ATTESTED_REVIEW} — work is owed. */
  public static boolean isOutstanding(String status) {
    return NOT_STARTED.equals(status)
        || IN_PROGRESS.equals(status)
        || ATTESTED_REVIEW.equals(status);
  }

  /** {@code DONE} or {@code ATTESTED} — nothing is owed. */
  public static boolean isClear(String status) {
    return DONE.equals(status) || ATTESTED.equals(status);
  }

  /**
   * Projects one client's FICA status.
   *
   * @param onboardingInstances the client's checklist instances whose template is an onboarding
   *     template ({@code auto_instantiate = true}) — any status, filtered here
   * @param ficaTemplateIds FICA request-template ids (the fallback rule's filter)
   * @param clientRequests the client's information requests (any template/status — filtered here)
   * @param itemsForRequest item lookup; invoked at most once, for the chosen COMPLETED request
   * @param activeAttestation the client's active FICA attestation, or null (anything not {@code
   *     ACTIVE} is ignored)
   * @param today the firm's today ({@code LocalDate.now(clock)}) — decides {@code
   *     REVIEW_DATE_PASSED}
   */
  public static Projection project(
      Collection<ChecklistInstance> onboardingInstances,
      Set<UUID> ficaTemplateIds,
      Collection<InformationRequest> clientRequests,
      Function<UUID, List<RequestItem>> itemsForRequest,
      ClientFicaAttestation activeAttestation,
      LocalDate today) {
    Objects.requireNonNull(today, "today");
    var live =
        onboardingInstances.stream()
            .filter(i -> !INSTANCE_CANCELLED.equals(i.getStatus()))
            .sorted(NEWEST_INSTANCE_FIRST)
            .toList();
    var open = live.stream().filter(i -> INSTANCE_IN_PROGRESS.equals(i.getStatus())).findFirst();
    if (open.isPresent()) {
      return new Projection(IN_PROGRESS, null, null, open.get().getId(), List.of(), null);
    }
    var done = live.stream().filter(i -> INSTANCE_COMPLETED.equals(i.getStatus())).findFirst();
    if (done.isPresent()) {
      return new Projection(
          DONE, done.get().getCompletedAt(), null, done.get().getId(), List.of(), null);
    }
    var fromRequests =
        projectFromInformationRequests(ficaTemplateIds, clientRequests, itemsForRequest);
    if (!NOT_STARTED.equals(fromRequests.status())) {
      return fromRequests;
    }
    if (activeAttestation != null
        && activeAttestation.getStatus() == FicaAttestationStatus.ACTIVE) {
      List<String> reasons = reviewReasons(activeAttestation, today);
      return new Projection(
          reasons.isEmpty() ? ATTESTED : ATTESTED_REVIEW,
          null,
          null,
          null,
          reasons,
          activeAttestation.getId());
    }
    return NOT_STARTED_PROJECTION;
  }

  /** The attestation's review reasons, in {@link FicaReviewReason} declaration order (FD-30). */
  static List<String> reviewReasons(ClientFicaAttestation attestation, LocalDate today) {
    var reasons = new ArrayList<String>();
    if (attestation.getRiskRating() == FicaRiskRating.UNRATED) {
      reasons.add(FicaReviewReason.UNRATED.name());
    }
    if (attestation.getBeneficialOwnershipStatus() == BeneficialOwnershipStatus.OUTSTANDING) {
      reasons.add(FicaReviewReason.BENEFICIAL_OWNERSHIP_OUTSTANDING.name());
    }
    if (attestation.getSourceOfFundsStatus() == SourceOfFundsStatus.OUTSTANDING) {
      reasons.add(FicaReviewReason.SOURCE_OF_FUNDS_OUTSTANDING.name());
    }
    if (attestation.getScreeningStatus() == FicaScreeningStatus.NOT_SCREENED) {
      reasons.add(FicaReviewReason.NOT_SCREENED.name());
    }
    if (attestation.getNextReviewDate() != null
        && !attestation.getNextReviewDate().isAfter(today)) {
      reasons.add(FicaReviewReason.REVIEW_DATE_PASSED.name());
    }
    return List.copyOf(reasons);
  }

  /** The GAP-L-46 information-request rule — unchanged, now the fallback. */
  private static Projection projectFromInformationRequests(
      Set<UUID> ficaTemplateIds,
      Collection<InformationRequest> clientRequests,
      Function<UUID, List<RequestItem>> itemsForRequest) {
    if (ficaTemplateIds.isEmpty()) {
      return NOT_STARTED_PROJECTION;
    }
    var fica =
        clientRequests.stream()
            .filter(r -> r.getRequestTemplateId() != null)
            .filter(r -> ficaTemplateIds.contains(r.getRequestTemplateId()))
            // Ignore cancelled requests — they were never a live FICA pack.
            .filter(r -> r.getStatus() != RequestStatus.CANCELLED)
            .sorted(
                Comparator.comparing(InformationRequest::getCreatedAt)
                    .reversed()
                    .thenComparing(InformationRequest::getId))
            .toList();
    if (fica.isEmpty()) {
      return NOT_STARTED_PROJECTION;
    }

    // Prefer the most-recent completed request; otherwise the most-recent live one. This lets a
    // firm issue a second FICA pack without losing the earlier verification record while the new
    // one is still open.
    var completed = fica.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).findFirst();
    if (completed.isPresent()) {
      var r = completed.get();
      var items = itemsForRequest.apply(r.getId());
      boolean allAccepted =
          !items.isEmpty() && items.stream().allMatch(i -> i.getStatus() == ItemStatus.ACCEPTED);
      // COMPLETED in lifecycle but not every item ACCEPTED — IN_PROGRESS so the UI prompts review
      // rather than a green check.
      return allAccepted
          ? new Projection(DONE, r.getCompletedAt(), r.getId(), null, List.of(), null)
          : new Projection(IN_PROGRESS, null, r.getId(), null, List.of(), null);
    }

    // No completed FICA request — the most-recent non-terminal one.
    return new Projection(IN_PROGRESS, null, fica.getFirst().getId(), null, List.of(), null);
  }
}
