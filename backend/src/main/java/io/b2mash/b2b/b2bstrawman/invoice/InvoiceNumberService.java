package io.b2mash.b2b.b2bstrawman.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates sequential invoice numbers per tenant using a counter table with row-level locking.
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
   * Sentinel value used in the counter table for dedicated schemas (where tenantId is null).
   * PostgreSQL treats NULL != NULL in unique indexes, so ON CONFLICT won't fire for NULL values.
   * Using a sentinel avoids this issue while keeping the atomic UPSERT pattern.
   */
  private static final String DEDICATED_SCHEMA_SENTINEL = "__dedicated__";

  @PersistenceContext private EntityManager entityManager;

  /**
   * Assigns the next sequential invoice number for the given tenant.
   *
   * <p>Uses an atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING pattern for concurrency
   * safety. Two concurrent calls will serialize on the row-level lock within the UPSERT.
   *
   * @param tenantId the tenant discriminator value (org ID for shared schemas, null for dedicated
   *     schemas)
   * @return formatted invoice number (e.g., "INV-0001")
   */
  @Transactional
  public String assignNumber(String tenantId) {
    // Use sentinel for null tenantId to avoid PostgreSQL NULL != NULL in unique index
    String effectiveTenantId = tenantId != null ? tenantId : DEDICATED_SCHEMA_SENTINEL;

    // Atomic UPSERT: INSERT on first call, UPDATE on subsequent calls
    // The unique index on (tenant_id) ensures row-level locking on conflict
    // RETURNING gives us the assigned number (next_number - 1 after increment)
    var result =
        entityManager
            .createNativeQuery(
                "INSERT INTO invoice_counters (id, tenant_id, next_number)"
                    + " VALUES (gen_random_uuid(), :tenantId, 2)"
                    + " ON CONFLICT (tenant_id)"
                    + " DO UPDATE SET next_number = invoice_counters.next_number + 1"
                    + " RETURNING next_number - 1")
            .setParameter("tenantId", effectiveTenantId)
            .getSingleResult();

    int number = ((Number) result).intValue();
    return String.format("INV-%04d", number);
  }
}
