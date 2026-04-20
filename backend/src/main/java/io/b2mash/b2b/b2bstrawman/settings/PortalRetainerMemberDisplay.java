package io.b2mash.b2b.b2bstrawman.settings;

/**
 * Firm-wide privacy toggle (ADR-255) controlling how firm member names appear on the customer
 * portal's retainer consumption list. Default is {@link #FIRST_NAME_ROLE} — balances transparency
 * with privacy by showing e.g. "Alice (Attorney)".
 */
public enum PortalRetainerMemberDisplay {
  /** Show the member's full name, e.g. "Alice Ndlovu". */
  FULL_NAME,

  /** Show first name + org role, e.g. "Alice (Attorney)". Default. */
  FIRST_NAME_ROLE,

  /** Show the org role only, e.g. "Attorney". */
  ROLE_ONLY,

  /** Fully anonymise — always render "Team member". */
  ANONYMISED
}
