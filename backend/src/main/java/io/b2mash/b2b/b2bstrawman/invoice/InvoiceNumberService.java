package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating sequential, gap-free invoice numbers per tenant.
 *
 * <p>Uses a dedicated sequence table (invoice_number_seq) with pessimistic locking (SELECT FOR
 * UPDATE) to guarantee uniqueness and sequential ordering without gaps (rollback doesn't consume a
 * number).
 *
 * <p>Invoice number format: "INV-0001", "INV-0002", etc. (zero-padded 4 digits, expands naturally
 * beyond 9999).
 */
@Service
public class InvoiceNumberService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceNumberService.class);

  @PersistenceContext private EntityManager entityManager;

  /**
   * Assigns the next sequential invoice number for the current tenant. Uses an atomic two-step
   * pattern to guarantee uniqueness without race conditions:
   *
   * <ol>
   *   <li>INSERT ON CONFLICT DO NOTHING — ensures the sequence row exists (safe for concurrent
   *       first-time callers)
   *   <li>UPDATE ... SET next_value = next_value + 1 RETURNING next_value — atomically increments
   *       and returns the new value in a single round-trip
   * </ol>
   *
   * @return the assigned invoice number (e.g., "INV-0001")
   */
  @Transactional
  public String assignInvoiceNumber() {
    String tenantId = RequestScopes.TENANT_ID.get();

    // Step 1: Ensure sequence row exists (idempotent — ON CONFLICT DO NOTHING)
    entityManager
        .createNativeQuery(
            "INSERT INTO invoice_number_seq (id, tenant_id, next_value)"
                + " VALUES (gen_random_uuid(), ?1, 0)"
                + " ON CONFLICT (tenant_id) DO NOTHING")
        .setParameter(1, tenantId)
        .executeUpdate();

    // Step 2: Atomically increment and return the new value
    int nextValue =
        (Integer)
            entityManager
                .createNativeQuery(
                    "UPDATE invoice_number_seq SET next_value = next_value + 1"
                        + " WHERE tenant_id = ?1 RETURNING next_value")
                .setParameter(1, tenantId)
                .getSingleResult();

    log.debug(
        "Assigned invoice number for tenant {}: INV-{}",
        tenantId,
        String.format("%04d", nextValue));

    return String.format("INV-%04d", nextValue);
  }
}
