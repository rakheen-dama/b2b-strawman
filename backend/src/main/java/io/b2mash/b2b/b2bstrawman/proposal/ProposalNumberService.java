package io.b2mash.b2b.b2bstrawman.proposal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates sequential proposal numbers using a singleton counter row per tenant schema (ADR-128).
 *
 * <p>Counter is lazily initialized (first call creates the row). Numbers are gap-free
 * (transactional -- rollback reverts the counter). Format: "PROP-" + zero-padded 4-digit number.
 * Deleted DRAFTs leave gaps (by design).
 */
@Service
public class ProposalNumberService {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Assigns the next sequential proposal number for the current tenant schema.
   *
   * <p>Uses an atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING pattern for concurrency
   * safety. Each dedicated tenant schema has at most one row in proposal_counters. Two concurrent
   * calls will serialize on the row-level lock within the UPSERT.
   *
   * @return formatted proposal number (e.g., "PROP-0001")
   */
  @Transactional
  public String allocateNumber() {
    var result =
        entityManager
            .createNativeQuery(
                "INSERT INTO proposal_counters (id, next_number, singleton)"
                    + " VALUES (gen_random_uuid(), 2, TRUE)"
                    + " ON CONFLICT ON CONSTRAINT proposal_counters_singleton"
                    + " DO UPDATE SET next_number = proposal_counters.next_number + 1"
                    + " RETURNING next_number - 1")
            .getSingleResult();
    int number = ((Number) result).intValue();
    return String.format("PROP-%04d", number);
  }
}
