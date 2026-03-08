package io.b2mash.b2b.b2bstrawman.billingrun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_run_entry_selections")
public class BillingRunEntrySelection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "billing_run_item_id", nullable = false)
  private UUID billingRunItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 20)
  private EntryType entryType;

  @Column(name = "entry_id", nullable = false)
  private UUID entryId;

  @Column(name = "included", nullable = false)
  private boolean included;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BillingRunEntrySelection() {}

  public BillingRunEntrySelection(UUID billingRunItemId, EntryType entryType, UUID entryId) {
    this.billingRunItemId = billingRunItemId;
    this.entryType = entryType;
    this.entryId = entryId;
    this.included = true;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBillingRunItemId() {
    return billingRunItemId;
  }

  public EntryType getEntryType() {
    return entryType;
  }

  public UUID getEntryId() {
    return entryId;
  }

  public boolean isIncluded() {
    return included;
  }

  public void setIncluded(boolean included) {
    this.included = included;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
