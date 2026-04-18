package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 1 — matter trust balance must be R0.00. A matter cannot close while it holds client funds
 * (Phase 67 §67.3.4 gate 1, ADR-248).
 */
@Component
public class TrustBalanceZeroGate implements ClosureGate {

  static final String CODE = "TRUST_BALANCE_ZERO";

  private final TrustTransactionRepository trustTransactionRepository;

  public TrustBalanceZeroGate(TrustTransactionRepository trustTransactionRepository) {
    this.trustTransactionRepository = trustTransactionRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 1;
  }

  @Override
  public GateResult evaluate(Project project) {
    BigDecimal balance = trustTransactionRepository.calculateBalanceByProjectId(project.getId());
    if (balance == null) {
      balance = BigDecimal.ZERO;
    }
    if (balance.compareTo(BigDecimal.ZERO) == 0) {
      return new GateResult(true, CODE, "Matter trust balance is R0.00.", Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "Matter trust balance is R%s. Transfer to client or office before closure."
            .formatted(balance.toPlainString()),
        Map.of("balance", balance));
  }
}
