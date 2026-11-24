package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One go-live item the firm has acknowledged (Phase 93 656A, §17.2.6): who, when and an optional
 * note. One row per item ({@code item_key} is the key), written by {@link
 * TakeOnGoLiveItemRepository#upsertAcknowledgement} (a new acknowledgement replaces it, D-166);
 * withdrawing an acknowledgement deletes the row. Advisory: nothing in the take-on commit reads it
 * (A2).
 */
@Entity
@Table(name = "take_on_go_live_items")
public class TakeOnGoLiveItem {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "item_key", nullable = false, length = 40, updatable = false)
  private GoLiveItem itemKey;

  @Column(name = "acknowledged_by", nullable = false)
  private UUID acknowledgedBy;

  @Column(name = "acknowledged_at", nullable = false)
  private Instant acknowledgedAt;

  @Column(name = "note", length = 1000)
  private String note;

  protected TakeOnGoLiveItem() {}

  public TakeOnGoLiveItem(GoLiveItem itemKey, UUID acknowledgedBy, Instant at, String note) {
    this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
    this.acknowledgedBy = Objects.requireNonNull(acknowledgedBy, "acknowledgedBy");
    this.acknowledgedAt = Objects.requireNonNull(at, "at");
    this.note = note;
  }

  public GoLiveItem getItemKey() {
    return itemKey;
  }

  public UUID getAcknowledgedBy() {
    return acknowledgedBy;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public String getNote() {
    return note;
  }
}
