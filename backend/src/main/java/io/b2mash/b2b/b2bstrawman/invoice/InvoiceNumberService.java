package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates sequential invoice numbers using a singleton counter row per tenant schema.
 *
 * <p>Per ADR-048:
 *
 * <ul>
 *   <li>Counter is lazily initialized (first call creates the row)
 *   <li>Numbers are gap-free (transactional -- rollback reverts the counter)
 *   <li>Format: "INV-" + zero-padded 4-digit number
 *   <li>Voided invoices retain their number permanently
 * </ul>
 */
@Service
public class InvoiceNumberService {

  /**
   * A number in the firm's own format, {@code INV-<digits>} (any case, leading zeros allowed). At
   * most 18 digits, so the value always fits a {@code long}.
   */
  private static final Pattern FIRM_FORMAT =
      Pattern.compile("INV-(\\d{1,18})", Pattern.CASE_INSENSITIVE);

  @PersistenceContext private EntityManager entityManager;

  /**
   * Assigns the next sequential invoice number for the current tenant schema.
   *
   * <p>Uses an atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING pattern for concurrency
   * safety. Each dedicated tenant schema has at most one row in invoice_counters. Two concurrent
   * calls will serialize on the row-level lock within the UPSERT.
   *
   * @return formatted invoice number (e.g., "INV-0001")
   */
  @Transactional
  public String assignNumber() {
    // Atomic UPSERT: INSERT on first call, UPDATE on subsequent calls.
    // Uses ON CONFLICT on the singleton constraint to ensure exactly one row per schema.
    // Concurrent calls serialize on the row-level lock within the UPSERT.
    var result =
        entityManager
            .createNativeQuery(
                "INSERT INTO invoice_counters (id, next_number, singleton)"
                    + " VALUES (gen_random_uuid(), 2, TRUE)"
                    + " ON CONFLICT ON CONSTRAINT invoice_counters_singleton"
                    + " DO UPDATE SET next_number = invoice_counters.next_number + 1"
                    + " RETURNING next_number - 1")
            .getSingleResult();
    int number = ((Number) result).intValue();
    return String.format("INV-%04d", number);
  }

  /**
   * The sequence value of a number in the firm's own format ({@code "INV-0005"} → 5), or empty for
   * any other number, for zero, and for a value the {@code integer} counter can never reach (it
   * cannot collide with a number {@link #assignNumber} hands out).
   */
  public static OptionalLong sequenceValueOf(String invoiceNumber) {
    if (invoiceNumber == null) {
      return OptionalLong.empty();
    }
    var matcher = FIRM_FORMAT.matcher(invoiceNumber.strip());
    if (!matcher.matches()) {
      return OptionalLong.empty();
    }
    long value = Long.parseLong(matcher.group(1));
    return value >= 1 && value < Integer.MAX_VALUE ? OptionalLong.of(value) : OptionalLong.empty();
  }

  /**
   * Phase 93 658A (D-253): moves the counter so the next {@link #assignNumber} is greater than
   * {@code number} — never backwards. Called when a brought-forward fee note keeps a source number
   * in the firm's own format, so a later approval cannot collide with it on {@code
   * idx_invoices_number_unique}. The same singleton upsert as {@link #assignNumber}, so it takes
   * the same row lock (a concurrent approval waits, then numbers after it), in the caller's
   * transaction ({@code MANDATORY}): a rolled-back take-on row leaves the counter as it was.
   *
   * @param number a value from {@link #sequenceValueOf}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void advancePast(long number) {
    if (number < 1 || number >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Not a value of the firm's sequence: " + number);
    }
    entityManager
        .createNativeQuery(
            "INSERT INTO invoice_counters (id, next_number, singleton)"
                + " VALUES (gen_random_uuid(), :next, TRUE)"
                + " ON CONFLICT ON CONSTRAINT invoice_counters_singleton"
                + " DO UPDATE SET next_number ="
                + " GREATEST(invoice_counters.next_number, EXCLUDED.next_number)")
        .setParameter("next", (int) (number + 1))
        .executeUpdate();
  }
}
