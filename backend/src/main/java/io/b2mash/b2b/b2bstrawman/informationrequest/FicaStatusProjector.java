package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The FICA onboarding status rule (GAP-L-46), extracted from {@code
 * InformationRequestService#getFicaStatus} so the single-client read, the dashboard batch (Phase
 * 87, Epic 614A) and the client list share ONE rule. Pure — no Spring, no repositories.
 *
 * <p>Rule: consider the client's requests whose template is one of the FICA templates, ignoring
 * {@code CANCELLED}, newest first (by {@code createdAt}, then id for determinism). None → {@code
 * NOT_STARTED}. Otherwise the newest {@code COMPLETED} request wins: {@code DONE} (with its {@code
 * completedAt}) when it has at least one item and every item is {@code ACCEPTED}, else {@code
 * IN_PROGRESS} with that request's id. No {@code COMPLETED} request → {@code IN_PROGRESS} with the
 * newest live request's id.
 */
public final class FicaStatusProjector {

  public static final String NOT_STARTED = "NOT_STARTED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String DONE = "DONE";

  private FicaStatusProjector() {}

  /**
   * Projected FICA status. {@code completedAt} is set only for {@code DONE}; {@code requestId} is
   * null only for {@code NOT_STARTED}.
   */
  public record Projection(String status, Instant completedAt, UUID requestId) {}

  private static final Projection NOT_STARTED_PROJECTION = new Projection(NOT_STARTED, null, null);

  /**
   * Projects one client's FICA status.
   *
   * @param ficaTemplateIds every template id whose pack is the FICA onboarding pack (active or not)
   * @param clientRequests the client's information requests (any template/status — filtered here)
   * @param itemsForRequest item lookup; invoked at most once, for the chosen COMPLETED request
   */
  public static Projection project(
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
          ? new Projection(DONE, r.getCompletedAt(), r.getId())
          : new Projection(IN_PROGRESS, null, r.getId());
    }

    // No completed FICA request — the most-recent non-terminal one.
    return new Projection(IN_PROGRESS, null, fica.getFirst().getId());
  }
}
