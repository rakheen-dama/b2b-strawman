package io.b2mash.b2b.b2bstrawman.crm;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates sequential deal numbers using a singleton counter row per tenant schema (Phase 80).
 * Mirrors {@code ProposalNumberService}.
 *
 * <p>Counter is lazily initialized (first call creates the row). Numbers are gap-free
 * (transactional — rollback reverts the counter). Format: "DEAL-" + zero-padded 4-digit number.
 */
@Service
public class DealNumberService {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Assigns the next sequential deal number for the current tenant schema.
   *
   * <p>Uses an atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING pattern for concurrency
   * safety. Each dedicated tenant schema has at most one row in {@code deal_counters}. Two
   * concurrent calls serialize on the row-level lock within the UPSERT.
   *
   * @return formatted deal number (e.g., "DEAL-0001")
   */
  @Transactional
  public String allocateNumber() {
    var result =
        entityManager
            .createNativeQuery(
                "INSERT INTO deal_counters (id, next_number, singleton)"
                    + " VALUES (gen_random_uuid(), 2, TRUE)"
                    + " ON CONFLICT ON CONSTRAINT deal_counters_singleton"
                    + " DO UPDATE SET next_number = deal_counters.next_number + 1"
                    + " RETURNING next_number - 1")
            .getSingleResult();
    int number = ((Number) result).intValue();
    return String.format(Locale.ROOT, "DEAL-%04d", number);
  }
}
