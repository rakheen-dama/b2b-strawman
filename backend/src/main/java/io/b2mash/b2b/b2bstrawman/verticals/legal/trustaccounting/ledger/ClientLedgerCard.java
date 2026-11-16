package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The per-client lock anchor (ADR-378, ADR-231): one row per (trust account, client), locked with
 * {@link ClientLedgerCardRepository#findByAccountAndCustomerForUpdate} by every client posting
 * path, so a sufficiency check and the posting it guards cannot race. It stores no balance: since
 * Phase 92 (646A) a client's balance, totals and last transaction date are derived from the posting
 * effects on {@code trust_transactions} ({@code TrustLedgerQueryRepository}).
 */
@Entity
@Table(name = "client_ledger_cards")
public class ClientLedgerCard {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ClientLedgerCard() {}

  public ClientLedgerCard(UUID trustAccountId, UUID customerId) {
    this.trustAccountId = trustAccountId;
    this.customerId = customerId;
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

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
