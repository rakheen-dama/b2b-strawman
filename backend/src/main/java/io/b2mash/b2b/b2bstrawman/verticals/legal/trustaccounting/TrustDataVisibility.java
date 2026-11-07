package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import org.springframework.stereotype.Component;

/**
 * Whether the current member may see trust money in a generated document (security review, PR
 * #1749): the {@code trust_accounting} module is enabled AND the member holds {@code VIEW_TRUST} —
 * the rule the matter list applies to its trust balance ({@code ProjectService}). Owner and admin
 * system roles carry every capability ({@code OrgRoleService}), so they always pass while the
 * module is on.
 *
 * <p>The one place both document builders read: the Client Trust Statement's client context ({@code
 * ClientTrustStatementContext}) and the Statement of Account's trust section ({@code
 * StatementOfAccountContextBuilder}). A generation without a bound capability set (no member
 * request scope) sees no trust data.
 */
@Component
public class TrustDataVisibility {

  public static final String TRUST_ACCOUNTING_MODULE = "trust_accounting";
  public static final String VIEW_TRUST = "VIEW_TRUST";

  private final VerticalModuleGuard moduleGuard;

  public TrustDataVisibility(VerticalModuleGuard moduleGuard) {
    this.moduleGuard = moduleGuard;
  }

  /** True when trust balances, ledger lines and references may be printed for this member. */
  public boolean visibleToCurrentMember() {
    return trustAccountingEnabled() && RequestScopes.hasCapability(VIEW_TRUST);
  }

  /**
   * True when the firm keeps trust money but this member may not see it (module on, no VIEW_TRUST):
   * a trust figure exists and is withheld from them. With the module off there is no trust money to
   * withhold — a statement's trust balance is a genuine zero (CodeRabbit #5 / G89-F51).
   */
  public boolean withheldFromCurrentMember() {
    return trustAccountingEnabled() && !RequestScopes.hasCapability(VIEW_TRUST);
  }

  private boolean trustAccountingEnabled() {
    return moduleGuard.isModuleEnabled(TRUST_ACCOUNTING_MODULE);
  }
}
