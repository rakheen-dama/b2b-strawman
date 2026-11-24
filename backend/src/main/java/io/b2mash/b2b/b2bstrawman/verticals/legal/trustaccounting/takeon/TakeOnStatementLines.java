package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 93 (657D, §17.3.15): the take-on is one brought-forward figure on a statement, never a
 * movement. {@link #fold} replaces every take-on row of a client ({@code take_on_batch_id} set) in
 * a statement's rows with <b>one</b> line dated at the cutover, "Balance brought forward —
 * take-on", whose amount is the Σ of their {@code ledger_effect} — the client's {@code
 * TAKEON_CLIENT} balance while the items outstanding at cutover stand (an item and its offset net
 * to nothing). The statement's opening and closing balances are unchanged: they are derived from
 * the same rows.
 *
 * <p>A later reversal of a cutover cheque is an ordinary {@code REVERSAL} row without a batch id
 * ({@code chk_trust_txn_take_on_batch}) and stays an ordinary line after the brought-forward one.
 */
public final class TakeOnStatementLines {

  /** The X3 label of the brought-forward line (= the {@code TAKEON_CLIENT} registry label). */
  public static final String LABEL = "Balance brought forward — take-on";

  private TakeOnStatementLines() {}

  /** A statement entry: an ordinary row, or the take-on folded into one brought-forward line. */
  public sealed interface Entry {

    /** An ordinary row, listed as it is. */
    record Row(TrustTransaction transaction) implements Entry {}

    /**
     * The client's take-on rows as one line: {@code date} = the cutover date, {@code ledgerEffect}
     * = Σ their ledger effect, {@code anchorRowId} = the {@code TAKEON_CLIENT} row's id, else the
     * first take-on row's (a nil-balance client with only an item outstanding at cutover).
     */
    record BroughtForward(
        LocalDate date, BigDecimal ledgerEffect, UUID anchorRowId, UUID customerId)
        implements Entry {}
  }

  /**
   * Folds {@code rows} (oldest first, as the statement reads them): every take-on row is replaced
   * by one {@link Entry.BroughtForward} per client, placed where that client's first take-on row
   * was; every other row is an {@link Entry.Row} in its own place.
   */
  public static List<Entry> fold(List<TrustTransaction> rows) {
    Map<UUID, List<TrustTransaction>> takeOnByClient = new LinkedHashMap<>();
    for (var row : rows) {
      if (row.getTakeOnBatchId() != null) {
        takeOnByClient.computeIfAbsent(row.getCustomerId(), k -> new ArrayList<>()).add(row);
      }
    }
    if (takeOnByClient.isEmpty()) {
      return rows.stream().<Entry>map(Entry.Row::new).toList();
    }
    var entries = new ArrayList<Entry>(rows.size());
    var emitted = new java.util.HashSet<UUID>();
    for (var row : rows) {
      if (row.getTakeOnBatchId() == null) {
        entries.add(new Entry.Row(row));
        continue;
      }
      var client = row.getCustomerId();
      if (!emitted.add(client)) {
        continue;
      }
      entries.add(broughtForward(client, takeOnByClient.get(client)));
    }
    return List.copyOf(entries);
  }

  private static Entry.BroughtForward broughtForward(UUID client, List<TrustTransaction> rows) {
    var sum = BigDecimal.ZERO;
    for (var row : rows) {
      sum = sum.add(row.getLedgerEffect());
    }
    var anchor =
        rows.stream()
            .filter(r -> Objects.equals("TAKEON_CLIENT", r.getTransactionType()))
            .findFirst()
            .orElse(rows.getFirst());
    return new Entry.BroughtForward(
        rows.getFirst().getTransactionDate(), sum, anchor.getId(), client);
  }
}
