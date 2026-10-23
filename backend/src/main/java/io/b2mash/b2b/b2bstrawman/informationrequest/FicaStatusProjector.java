package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
 *       COMPLETED} request → {@code IN_PROGRESS} with the newest live request's id.
 * </ol>
 */
public final class FicaStatusProjector {

  public static final String NOT_STARTED = "NOT_STARTED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String DONE = "DONE";

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
   * requestId} / {@code checklistInstanceId} is set, except for {@code NOT_STARTED} (both null):
   * {@code checklistInstanceId} when the checklist branch decided, {@code requestId} when the
   * information-request branch did.
   */
  public record Projection(
      String status, Instant completedAt, UUID requestId, UUID checklistInstanceId) {}

  private static final Projection NOT_STARTED_PROJECTION =
      new Projection(NOT_STARTED, null, null, null);

  /**
   * Projects one client's FICA status.
   *
   * @param onboardingInstances the client's checklist instances whose template is an onboarding
   *     template ({@code auto_instantiate = true}) — any status, filtered here
   * @param ficaTemplateIds FICA request-template ids (the fallback rule's filter)
   * @param clientRequests the client's information requests (any template/status — filtered here)
   * @param itemsForRequest item lookup; invoked at most once, for the chosen COMPLETED request
   */
  public static Projection project(
      Collection<ChecklistInstance> onboardingInstances,
      Set<UUID> ficaTemplateIds,
      Collection<InformationRequest> clientRequests,
      Function<UUID, List<RequestItem>> itemsForRequest) {
    var live =
        onboardingInstances.stream()
            .filter(i -> !INSTANCE_CANCELLED.equals(i.getStatus()))
            .sorted(NEWEST_INSTANCE_FIRST)
            .toList();
    var open = live.stream().filter(i -> INSTANCE_IN_PROGRESS.equals(i.getStatus())).findFirst();
    if (open.isPresent()) {
      return new Projection(IN_PROGRESS, null, null, open.get().getId());
    }
    var done = live.stream().filter(i -> INSTANCE_COMPLETED.equals(i.getStatus())).findFirst();
    if (done.isPresent()) {
      return new Projection(DONE, done.get().getCompletedAt(), null, done.get().getId());
    }
    return projectFromInformationRequests(ficaTemplateIds, clientRequests, itemsForRequest);
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
          ? new Projection(DONE, r.getCompletedAt(), r.getId(), null)
          : new Projection(IN_PROGRESS, null, r.getId(), null);
    }

    // No completed FICA request — the most-recent non-terminal one.
    return new Projection(IN_PROGRESS, null, fica.getFirst().getId(), null);
  }
}
