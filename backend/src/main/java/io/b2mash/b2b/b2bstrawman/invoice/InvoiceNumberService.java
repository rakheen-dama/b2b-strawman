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
   * Assigns the next sequential invoice number for the current tenant. Uses pessimistic locking to
   * serialize concurrent approvals per tenant.
   *
   * <p>If no sequence row exists for the tenant, creates one with next_value = 1 and returns
   * "INV-0001". Otherwise, increments the existing sequence and returns the formatted number.
   *
   * @return the assigned invoice number (e.g., "INV-0001")
   */
  @Transactional
  public String assignInvoiceNumber() {
    String tenantId = RequestScopes.TENANT_ID.get();

    // Try to lock and fetch the current sequence row
    var resultList =
        entityManager
            .createNativeQuery(
                "SELECT next_value FROM invoice_number_seq WHERE tenant_id = ?1 FOR UPDATE")
            .setParameter(1, tenantId)
            .getResultList();

    int nextValue;
    if (resultList.isEmpty()) {
      // No row exists for this tenant — insert with next_value = 1
      entityManager
          .createNativeQuery(
              "INSERT INTO invoice_number_seq (id, tenant_id, next_value) VALUES (gen_random_uuid(), ?1, 1)")
          .setParameter(1, tenantId)
          .executeUpdate();
      nextValue = 1;
      log.info("Initialized invoice number sequence for tenant {}: INV-0001", tenantId);
    } else {
      // Row exists — increment and update
      entityManager
          .createNativeQuery(
              "UPDATE invoice_number_seq SET next_value = next_value + 1 WHERE tenant_id = ?1")
          .setParameter(1, tenantId)
          .executeUpdate();

      // Re-fetch the incremented value (non-locking read is safe — we hold the FOR UPDATE lock)
      nextValue =
          (Integer)
              entityManager
                  .createNativeQuery(
                      "SELECT next_value FROM invoice_number_seq WHERE tenant_id = ?1")
                  .setParameter(1, tenantId)
                  .getSingleResult();

      log.debug(
          "Assigned invoice number for tenant {}: INV-{}",
          tenantId,
          String.format("%04d", nextValue));
    }

    return String.format("INV-%04d", nextValue);
  }
}
