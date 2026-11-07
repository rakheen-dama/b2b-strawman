package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
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

  /** ProblemDetail title of a refused trust-document read (and the UI's disabled tooltip). */
  public static final String NOT_AVAILABLE_TITLE = "Trust details are not available";

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

  /**
   * G89-F52 (D18): the read rule for a stored document. A document that prints trust money ({@code
   * containsTrustData}) may be downloaded only by a member holding {@code VIEW_TRUST}. Capability
   * only — not the module flag: the stored file still holds trust data after the module is switched
   * off. Owner and admin hold every capability, so they always pass. Callers run their matter check
   * (404) first; this one comes after it (403), like the {@code @RequiresCapability("VIEW_TRUST")}
   * trust endpoints.
   *
   * @throws ForbiddenException the document holds trust data and the member lacks VIEW_TRUST
   */
  public void requireMayRead(boolean containsTrustData) {
    if (containsTrustData && !RequestScopes.hasCapability(VIEW_TRUST)) {
      throw new ForbiddenException(
          NOT_AVAILABLE_TITLE,
          "This document contains trust account details, which your role cannot view.");
    }
  }

  private boolean trustAccountingEnabled() {
    return moduleGuard.isModuleEnabled(TRUST_ACCOUNTING_MODULE);
  }
}
