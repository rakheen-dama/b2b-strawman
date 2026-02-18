package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
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
}
