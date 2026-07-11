package io.b2mash.b2b.b2bstrawman.verticals.legal.collections;

import io.b2mash.b2b.b2bstrawman.collections.CollectionsAdvisor;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * legal-za {@link CollectionsAdvisor} (Phase 83, §6.5, ADR-329): a customer holding a positive
 * aggregate trust balance surfaces {@code TRUST_FUNDS_AVAILABLE} advice, so a collector can
 * consider a s86(4) fee transfer <em>for the attorney to weigh</em>. This advisor never moves money
 * and never blocks a reminder — it informs only.
 *
 * <p><strong>Detail-string semantics (ADR-329).</strong> {@code sumBalancesForCustomer} returns the
 * <em>aggregate</em> ledger balance, not matter-earmarked availability, so the detail says {@code
 * "held in trust"} — deliberately NOT "available to transfer".
 *
 * <p><strong>Fail-OPEN.</strong> A {@link DataAccessException} means the trust tables are absent (a
 * non-legal tenant) or the lookup failed; the advisor returns no advice and debug-logs. This
 * mirrors {@code TrustBoundaryGuard}'s table-absence tolerance ("the tables are the ground truth" —
 * no profile detection), but with the opposite default: the guard protects an outbound sync
 * boundary and fails CLOSED, whereas advice decorates a human-reviewed flow and fails OPEN. Only
 * {@code DataAccessException} is caught — broader failures propagate.
 *
 * <p>legal→legal import ({@code ClientLedgerCardRepository}) is fine; the boundary only forbids
 * core {@code collections/} importing {@code verticals/legal}.
 */
@Component
public class TrustAwareCollectionsAdvisor implements CollectionsAdvisor {

  private static final Logger log = LoggerFactory.getLogger(TrustAwareCollectionsAdvisor.class);

  static final String TRUST_FUNDS_AVAILABLE = "TRUST_FUNDS_AVAILABLE";

  private final ClientLedgerCardRepository clientLedgerCardRepository;

  public TrustAwareCollectionsAdvisor(ClientLedgerCardRepository clientLedgerCardRepository) {
    this.clientLedgerCardRepository = clientLedgerCardRepository;
  }

  @Override
  public List<CollectionsAdvice> adviseFor(UUID customerId) {
    try {
      BigDecimal balance = clientLedgerCardRepository.sumBalancesForCustomer(customerId);
      if (balance != null && balance.compareTo(BigDecimal.ZERO) > 0) {
        return List.of(new CollectionsAdvice(TRUST_FUNDS_AVAILABLE, formatDetail(balance)));
      }
      return List.of();
    } catch (DataAccessException e) {
      // Trust tables absent (non-legal tenant) or lookup failure — fail OPEN: no advice, no error.
      log.debug("Trust balance lookup failed (non-legal tenant?): {}", e.getMessage());
      return List.of();
    }
  }

  /** SA-style deterministic format, e.g. {@code R 84 200,00 held in trust} (ADR-329 example). */
  private static String formatDetail(BigDecimal balance) {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator(',');
    DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
    return "R " + format.format(balance) + " held in trust";
  }
}
