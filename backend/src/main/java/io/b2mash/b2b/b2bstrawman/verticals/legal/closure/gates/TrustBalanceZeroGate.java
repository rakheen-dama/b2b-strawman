package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 1 — matter trust balance must be zero. A matter cannot close while it holds client funds
 * (Phase 67 §67.3.4 gate 1, ADR-248). The balance in the message goes through the canonical
 * plain-text currency formatter in the tenant's locale (Phase 88 626E) — "R38 000,00", never a raw
 * decimal.
 */
@Component
public class TrustBalanceZeroGate implements ClosureGate {

  static final String CODE = "TRUST_BALANCE_ZERO";

  private final TrustTransactionRepository trustTransactionRepository;
  private final OrgSettingsRepository orgSettingsRepository;

  public TrustBalanceZeroGate(
      TrustTransactionRepository trustTransactionRepository,
      OrgSettingsRepository orgSettingsRepository) {
    this.trustTransactionRepository = trustTransactionRepository;
    this.orgSettingsRepository = orgSettingsRepository;
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
    String amount = VariableFormatter.formatCurrencyText(balance, tenantLocale());
    if (balance.compareTo(BigDecimal.ZERO) == 0) {
      return new GateResult(true, CODE, "Matter trust balance is %s.".formatted(amount), Map.of());
    }
    return new GateResult(
        false,
        CODE,
        "Matter trust balance is %s. Transfer to client or office before closure."
            .formatted(amount),
        Map.of("balance", balance));
  }

  /**
   * Plain text, not {@code VariableFormatter.format(.., "currency", ..)}: the message is JSON shown
   * verbatim by the closure dialog, so the HTML-escaped {@code &nbsp;} grouping would print
   * literally.
   */
  private Locale tenantLocale() {
    return VariableFormatter.resolveLocale(
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getDefaultCurrency)
            .orElse(null));
  }
}
