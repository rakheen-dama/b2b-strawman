package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A non-client trust creditor account of one trust account (Phase 92, ADR-376, §16.2.4): the Fund's
 * interest-and-charges ledger or the unidentified-deposits ledger. Opened with the trust account
 * and never deleted; closing the trust account leaves it. Its balance is derived: {@code
 * SUM(ledger_effect)} over posted {@code NON_CLIENT} rows carrying its id.
 */
@Entity
@Table(name = "trust_creditor_accounts")
public class TrustCreditorAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  /**
   * No DDL CHECK on purpose: further types must not need a schema change (ADR-376). This is the one
   * status-like column in the module without a CHECK; {@link TrustCreditorAccountType} validates.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 40)
  private TrustCreditorAccountType accountType;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TrustCreditorAccount() {}

  public TrustCreditorAccount(
      UUID trustAccountId, TrustCreditorAccountType accountType, String name) {
    this.trustAccountId = trustAccountId;
    this.accountType = accountType;
    this.name = name;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public TrustCreditorAccountType getAccountType() {
    return accountType;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
