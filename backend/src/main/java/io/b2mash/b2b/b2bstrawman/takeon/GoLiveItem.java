package io.b2mash.b2b.b2bstrawman.takeon;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The four go-live acknowledgements of the firm (Phase 93 656A, §17.2.6, §17.10 X3), in X3 order.
 * The text is legal copy pending attorney and founder sign-off (X3-LEGAL, RG-12): it is shown as
 * written here, the one source the frontend renders ({@code text} in the checklist response).
 */
public enum GoLiveItem {
  TRUST_RECORDS_RETAINED(
      "Trust records from the previous system will be kept for at least seven years from the last"
          + " entry, secured and immediately accessible to the firm and the Council (Rules 54.9.1"
          + " and 54.9.2)."),
  FICA_RECORDS_RETAINED(
      "FICA records from the previous system will be kept for at least five years after each"
          + " business relationship ends (section 23)."),
  ARCHIVE_HOSTING_CONSIDERED(
      "If the previous vendor keeps hosting the archive, or its location changes, we have"
          + " considered whether Council consent (Rule 54.9.2) or a notification to the FIC"
          + " (section 24(3)) is needed. Kazi does not file either."),
  AUDITOR_INFORMED(
      "Our trust auditor has been told the cutover date and will receive the take-on report.");

  private final String text;

  GoLiveItem(String text) {
    this.text = text;
  }

  /** The X3 text of the item, verbatim. */
  public String text() {
    return text;
  }

  /**
   * The audit {@code entity_id} of the item: {@code audit_events.entity_id} is a uuid and the
   * item's key is not, so a stable name-based id stands in for it (C-12) and groups the item's
   * history.
   */
  public UUID auditEntityId() {
    return UUID.nameUUIDFromBytes(
        ("take_on_go_live_item:" + name()).getBytes(StandardCharsets.UTF_8));
  }
}
