package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The firm's go-live checklist (Phase 93 656A, §17.3.14, §17.6.7): the four {@link GoLiveItem}s,
 * each acknowledged (with an optional note) or withdrawn by a member who may manage take-on. Each
 * act is attributable and audited ({@code takeon.go_live.acknowledged|withdrawn}). Advisory: Kazi
 * cannot verify any of the four facts, so nothing reads this checklist to allow or refuse a take-on
 * (A2). The capability is the controller's ({@code MANAGE_TAKE_ON}).
 */
@Service
public class TakeOnGoLiveService {

  /** One item of the checklist, in X3 order; the acknowledgement fields are null when none. */
  public record TakeOnGoLiveItemResponse(
      GoLiveItem itemKey,
      String text,
      boolean acknowledged,
      UUID acknowledgedBy,
      String acknowledgedByName,
      Instant acknowledgedAt,
      String note) {}

  /** {@code PUT …/{itemKey}}: the optional note (trimmed; blank means none). */
  public record TakeOnGoLiveAcknowledgeRequest(@Size(max = 1000) String note) {}

  static final String NOT_ACKNOWLEDGED_TITLE = "Go-live item not acknowledged";
  static final String NOT_ACKNOWLEDGED_DETAIL =
      "This item has not been acknowledged, so there is nothing to withdraw.";

  private final TakeOnGoLiveItemRepository items;
  private final MemberNameResolver names;
  private final TakeOnAudit audit;
  private final Clock clock;

  public TakeOnGoLiveService(
      TakeOnGoLiveItemRepository items, MemberNameResolver names, TakeOnAudit audit, Clock clock) {
    this.items = items;
    this.names = names;
    this.audit = audit;
    this.clock = clock;
  }

  /** The four items in X3 order, each with its acknowledgement (if any). */
  @Transactional(readOnly = true)
  public List<TakeOnGoLiveItemResponse> list() {
    var stored = new EnumMap<GoLiveItem, TakeOnGoLiveItem>(GoLiveItem.class);
    items.findAll().forEach(i -> stored.put(i.getItemKey(), i));
    var memberNames =
        names.resolveNames(
            stored.values().stream().map(TakeOnGoLiveItem::getAcknowledgedBy).toList());
    return Arrays.stream(GoLiveItem.values())
        .map(
            key -> {
              var item = stored.get(key);
              return item == null
                  ? new TakeOnGoLiveItemResponse(key, key.text(), false, null, null, null, null)
                  : new TakeOnGoLiveItemResponse(
                      key,
                      key.text(),
                      true,
                      item.getAcknowledgedBy(),
                      memberNames.get(item.getAcknowledgedBy()),
                      item.getAcknowledgedAt(),
                      item.getNote());
            })
        .toList();
  }

  /**
   * Acknowledges {@code item} as the current member, now. An item already acknowledged is
   * acknowledged again: the member, time and note are replaced and the act is audited again
   * (D-166). One upsert, so concurrent acknowledgements of the same item each succeed and leave one
   * row (D-180).
   */
  @Transactional
  public List<TakeOnGoLiveItemResponse> acknowledge(
      GoLiveItem item, TakeOnGoLiveAcknowledgeRequest request) {
    UUID member = RequestScopes.requireMemberId();
    Instant now = clock.instant();
    String note =
        request == null || request.note() == null || request.note().isBlank()
            ? null
            : request.note().strip();
    items.upsertAcknowledgement(item.name(), member, now, note);
    audit.goLiveAcknowledged(item);
    return list();
  }

  /**
   * Withdraws the acknowledgement of {@code item} (deletes it); 404 when it is not acknowledged.
   */
  @Transactional
  public List<TakeOnGoLiveItemResponse> withdraw(GoLiveItem item) {
    var existing =
        items
            .findById(item)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        NOT_ACKNOWLEDGED_TITLE, NOT_ACKNOWLEDGED_DETAIL));
    items.delete(existing);
    items.flush();
    audit.goLiveWithdrawn(item);
    return list();
  }
}
