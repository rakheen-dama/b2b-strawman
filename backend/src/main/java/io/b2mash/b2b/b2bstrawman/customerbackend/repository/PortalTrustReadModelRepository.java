package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustBalanceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTrustTransactionView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Portal read-model repository for the per-matter trust ledger view. Per ADR-253, this uses {@link
 * JdbcClient} against the separate portal data source — there are NO JPA entities on the portal
 * side. All writes are idempotent upserts populated by {@code TrustLedgerPortalSyncService}.
 */
@Repository
public class PortalTrustReadModelRepository {

  private final JdbcClient jdbc;

  public PortalTrustReadModelRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant != null ? Timestamp.from(instant) : null;
  }

  // ── Balance upserts ───────────────────────────────────────────────────

  public void upsertBalance(
      UUID customerId, UUID matterId, BigDecimal currentBalance, Instant lastTransactionAt) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_trust_balance
                (customer_id, matter_id, current_balance, last_transaction_at, last_synced_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (customer_id, matter_id)
            DO UPDATE SET current_balance = EXCLUDED.current_balance,
                          last_transaction_at = EXCLUDED.last_transaction_at,
                          last_synced_at = now()
            """)
        .params(customerId, matterId, currentBalance, toTimestamp(lastTransactionAt))
        .update();
  }

  // ── Transaction upserts ───────────────────────────────────────────────

  /**
   * Upserts one portal trust row. {@code transactionDate} is the firm transaction's SA business
   * date (what the client sees, G89-F39); {@code recordedAt} is the firm row's {@code created_at},
   * the ledger tiebreak after the date; {@code occurredAt} is the approval / recorded instant.
   */
  public void upsertTransaction(
      UUID id,
      UUID customerId,
      UUID matterId,
      String transactionType,
      BigDecimal amount,
      BigDecimal runningBalance,
      LocalDate transactionDate,
      Instant recordedAt,
      Instant occurredAt,
      String description,
      String reference) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_trust_transaction
                (id, customer_id, matter_id, transaction_type, amount, running_balance,
                 transaction_date, recorded_at, occurred_at, description, reference,
                 last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id)
            DO UPDATE SET customer_id = EXCLUDED.customer_id,
                          matter_id = EXCLUDED.matter_id,
                          transaction_type = EXCLUDED.transaction_type,
                          amount = EXCLUDED.amount,
                          running_balance = EXCLUDED.running_balance,
                          transaction_date = EXCLUDED.transaction_date,
                          recorded_at = EXCLUDED.recorded_at,
                          occurred_at = EXCLUDED.occurred_at,
                          description = EXCLUDED.description,
                          reference = EXCLUDED.reference,
                          last_synced_at = now()
            """)
        .params(
            id,
            customerId,
            matterId,
            transactionType,
            amount,
            runningBalance,
            transactionDate,
            toTimestamp(recordedAt),
            toTimestamp(occurredAt),
            description,
            reference)
        .update();
  }

  // ── Balance queries ───────────────────────────────────────────────────

  /**
   * Balances for one client, each with the client's portal matter name (null when the client has no
   * {@code portal_projects} row for the matter). {@code portal_projects} is keyed on {@code (id,
   * customer_id)}, so the join matches the customer too — otherwise a multi-client matter would
   * duplicate the balance row (Phase 89 633B, G88-F31, D6). {@link #findBalance} joins the same
   * way.
   */
  public List<PortalTrustBalanceView> findBalancesByCustomer(UUID customerId) {
    return jdbc.sql(
            """
            SELECT b.customer_id, b.matter_id, b.current_balance, b.last_transaction_at,
                   b.last_synced_at, p.name AS matter_name
            FROM portal.portal_trust_balance b
            LEFT JOIN portal.portal_projects p
                   ON p.id = b.matter_id AND p.customer_id = b.customer_id
            WHERE b.customer_id = ?
            ORDER BY b.last_transaction_at DESC NULLS LAST
            """)
        .params(customerId)
        .query(PortalTrustBalanceView.class)
        .list();
  }

  public Optional<PortalTrustBalanceView> findBalance(UUID customerId, UUID matterId) {
    return jdbc.sql(
            """
            SELECT b.customer_id, b.matter_id, b.current_balance, b.last_transaction_at,
                   b.last_synced_at, p.name AS matter_name
            FROM portal.portal_trust_balance b
            LEFT JOIN portal.portal_projects p
                   ON p.id = b.matter_id AND p.customer_id = b.customer_id
            WHERE b.customer_id = ? AND b.matter_id = ?
            """)
        .params(customerId, matterId)
        .query(PortalTrustBalanceView.class)
        .optional();
  }

  // ── Transaction queries ───────────────────────────────────────────────

  /**
   * Returns transactions for the given customer/matter, optionally bounded by {@code from}/{@code
   * to} occurrence timestamps. Ordered newest-first in firm-ledger order (transaction date, then
   * recorded time, G89-F39) so each row's running balance reads in sequence. Supports offset-based
   * pagination via {@code size} and {@code offset}.
   */
  public List<PortalTrustTransactionView> findTransactions(
      UUID customerId, UUID matterId, Instant from, Instant to, int size, int offset) {
    // Postgres cannot infer the type of a bare `?` inside `? IS NULL`, so the bounds are cast
    // explicitly; each cast is passed the same timestamp twice (one for the null-check, one for
    // the range comparison).
    return jdbc.sql(
            """
            SELECT id, customer_id, matter_id, transaction_type, amount, running_balance,
                   transaction_date, recorded_at, occurred_at, description, reference,
                   last_synced_at
            FROM portal.portal_trust_transaction
            WHERE customer_id = ?
              AND matter_id = ?
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR occurred_at >= CAST(? AS TIMESTAMPTZ))
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR occurred_at <= CAST(? AS TIMESTAMPTZ))
            ORDER BY transaction_date DESC, recorded_at DESC, id DESC
            LIMIT ? OFFSET ?
            """)
        .params(
            customerId,
            matterId,
            toTimestamp(from),
            toTimestamp(from),
            toTimestamp(to),
            toTimestamp(to),
            size,
            offset)
        .query(PortalTrustTransactionView.class)
        .list();
  }

  /**
   * Returns the most recent transactions across all matters belonging to {@code customerId},
   * ordered newest-first. Powers {@code GET /portal/trust/movements} — the "Last trust movement"
   * tile on the portal home page, which is matter-agnostic.
   */
  public List<PortalTrustTransactionView> findRecentTransactionsByCustomer(
      UUID customerId, int limit) {
    return jdbc.sql(
            """
            SELECT id, customer_id, matter_id, transaction_type, amount, running_balance,
                   transaction_date, recorded_at, occurred_at, description, reference,
                   last_synced_at
            FROM portal.portal_trust_transaction
            WHERE customer_id = ?
            ORDER BY transaction_date DESC, recorded_at DESC, id DESC
            LIMIT ?
            """)
        .params(customerId, limit)
        .query(PortalTrustTransactionView.class)
        .list();
  }

  /** Counts transactions matching the same filter used by {@link #findTransactions}. */
  public long countTransactions(UUID customerId, UUID matterId, Instant from, Instant to) {
    return jdbc.sql(
            """
            SELECT COUNT(*) FROM portal.portal_trust_transaction
            WHERE customer_id = ?
              AND matter_id = ?
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR occurred_at >= CAST(? AS TIMESTAMPTZ))
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR occurred_at <= CAST(? AS TIMESTAMPTZ))
            """)
        .params(
            customerId,
            matterId,
            toTimestamp(from),
            toTimestamp(from),
            toTimestamp(to),
            toTimestamp(to))
        .query(Long.class)
        .single();
  }

  // ── Delete helpers (for backfill/tear-down) ────────────────────────────

  public void deleteBalancesByCustomer(UUID customerId) {
    jdbc.sql("DELETE FROM portal.portal_trust_balance WHERE customer_id = ?")
        .params(customerId)
        .update();
  }

  public void deleteTransactionsByCustomer(UUID customerId) {
    jdbc.sql("DELETE FROM portal.portal_trust_transaction WHERE customer_id = ?")
        .params(customerId)
        .update();
  }

  /** Returns every customer id that currently has at least one portal trust row. */
  public Set<UUID> findCustomerIdsWithPortalTrustRows() {
    var balanceCustomers =
        jdbc.sql("SELECT DISTINCT customer_id FROM portal.portal_trust_balance")
            .query(UUID.class)
            .list();
    var txnCustomers =
        jdbc.sql("SELECT DISTINCT customer_id FROM portal.portal_trust_transaction")
            .query(UUID.class)
            .list();
    java.util.HashSet<UUID> all = new java.util.HashSet<>(balanceCustomers);
    all.addAll(txnCustomers);
    return all;
  }

  // ── Backfill drift repair ─────────────────────────────────────────────
  //
  // The backfill rewrites a current-window projection per customer. After writing the fresh
  // rollups, any balance/transaction rows that aren't in the retained window must be dropped
  // so dropped matters and trimmed history don't linger on the portal side (cross-tenant
  // leak risk + stale totals).

  /**
   * Deletes {@code portal_trust_balance} rows for {@code customerId} whose {@code matter_id} is NOT
   * in {@code retainedMatterIds}. Empty-set input deletes all balances for the customer.
   */
  public void deleteBalancesForCustomerNotInMatters(UUID customerId, Set<UUID> retainedMatterIds) {
    if (retainedMatterIds == null || retainedMatterIds.isEmpty()) {
      deleteBalancesByCustomer(customerId);
      return;
    }
    UUID[] retained = retainedMatterIds.toArray(new UUID[0]);
    jdbc.sql(
            """
            DELETE FROM portal.portal_trust_balance
            WHERE customer_id = ? AND NOT (matter_id = ANY (?))
            """)
        .params(customerId, retained)
        .update();
  }

  /**
   * Deletes {@code portal_trust_transaction} rows for {@code customerId} whose {@code matter_id} is
   * NOT in {@code retainedMatterIds}. Empty-set input deletes all transactions for the customer.
   */
  public void deleteTransactionsForCustomerNotInMatters(
      UUID customerId, Set<UUID> retainedMatterIds) {
    if (retainedMatterIds == null || retainedMatterIds.isEmpty()) {
      deleteTransactionsByCustomer(customerId);
      return;
    }
    UUID[] retained = retainedMatterIds.toArray(new UUID[0]);
    jdbc.sql(
            """
            DELETE FROM portal.portal_trust_transaction
            WHERE customer_id = ? AND NOT (matter_id = ANY (?))
            """)
        .params(customerId, retained)
        .update();
  }

  /**
   * Deletes {@code portal_trust_transaction} rows for {@code (customerId, matterId)} whose {@code
   * id} is NOT in {@code retainedTxnIds}. Used to trim matters to the backfill's retained-window
   * view. Empty-set input deletes every transaction on that matter.
   */
  public void deleteTransactionsForMatterNotInIds(
      UUID customerId, UUID matterId, Set<UUID> retainedTxnIds) {
    if (retainedTxnIds == null || retainedTxnIds.isEmpty()) {
      jdbc.sql(
              """
              DELETE FROM portal.portal_trust_transaction
              WHERE customer_id = ? AND matter_id = ?
              """)
          .params(customerId, matterId)
          .update();
      return;
    }
    UUID[] retained = retainedTxnIds.toArray(new UUID[0]);
    jdbc.sql(
            """
            DELETE FROM portal.portal_trust_transaction
            WHERE customer_id = ? AND matter_id = ? AND NOT (id = ANY (?))
            """)
        .params(customerId, matterId, retained)
        .update();
  }
}
