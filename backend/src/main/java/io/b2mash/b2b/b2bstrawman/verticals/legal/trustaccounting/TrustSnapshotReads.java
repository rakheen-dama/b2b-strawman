package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one client document's trust reads on one database snapshot (Phase 92 FU-3; founder ruling
 * R-1, option a): a new transaction ({@code REQUIRES_NEW}), read-only, at {@code REPEATABLE_READ}.
 *
 * <p>Why a new transaction: a document reads the client's general balance, its section 86(4)
 * invested balance and (with several accounts) the balances that choose the account in separate
 * statements, inside the generate transaction, which writes the generated document and runs at READ
 * COMMITTED. An isolation on an inner {@code @Transactional} method is ignored there (it joins),
 * and the writing transaction's own isolation stays as it is (option b, rejected). A posting
 * committed between two of those statements — a placement approved, an investment returned — then
 * tore the stored document: the placement counted in both balances, or an account chosen as holding
 * nothing (648F R-1, 652D F-652D-CR1).
 *
 * <p>Contract for callers:
 *
 * <ul>
 *   <li>Put every trust figure of one document in one call, and nothing else: the transaction holds
 *       a second pooled connection while the caller's holds the first, so keep it to the reads.
 *   <li>Return values and DTOs, never entities: the reads run in their own persistence context,
 *       closed when this returns.
 *   <li>No writes and no locks: the transaction is read-only (in PostgreSQL a read-only {@code
 *       REPEATABLE_READ} transaction cannot fail with a serialization error), and it does not see
 *       the caller's uncommitted writes.
 * </ul>
 *
 * The tenant carries over: the new connection is checked out through the multitenant connection
 * provider, which sets {@code search_path} from {@code RequestScopes.TENANT_ID}, still bound on
 * this thread.
 */
@Component
public class TrustSnapshotReads {

  private final TransactionTemplate snapshot;

  public TrustSnapshotReads(PlatformTransactionManager transactionManager) {
    var template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setReadOnly(true);
    template.setName("TrustSnapshotReads");
    this.snapshot = template;
  }

  /** {@code reads} on one snapshot; its result must not be (or hold) an entity. */
  public <T> T read(Supplier<T> reads) {
    return snapshot.execute(status -> reads.get());
  }
}
