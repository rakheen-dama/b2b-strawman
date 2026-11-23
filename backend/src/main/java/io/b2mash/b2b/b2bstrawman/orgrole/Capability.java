package io.b2mash.b2b.b2bstrawman.orgrole;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Capability {
  FINANCIAL_VISIBILITY,
  INVOICING,
  PROJECT_MANAGEMENT,
  TEAM_OVERSIGHT,
  CUSTOMER_MANAGEMENT,
  AUTOMATIONS,
  MANAGE_COMPLIANCE,
  MANAGE_COMPLIANCE_DESTRUCTIVE,
  VIEW_LEGAL,
  MANAGE_LEGAL,
  /** View trust accounts, transactions, and reports. */
  VIEW_TRUST,
  /** Manage trust accounts and record transactions. */
  MANAGE_TRUST,
  /** Approve or reject trust payments. */
  APPROVE_TRUST_PAYMENT,

  /** Create/update/submit legal disbursements. */
  MANAGE_DISBURSEMENTS,
  /** Approve or reject legal disbursements. */
  APPROVE_DISBURSEMENTS,
  /** Write off a disbursement as uncollectable. */
  WRITE_OFF_DISBURSEMENTS,
  /** Close a legal matter after the compliance gates pass. */
  CLOSE_MATTER,
  /** Override a failing matter-closure gate (owner-only by default). */
  OVERRIDE_MATTER_CLOSURE,
  /** Generate a client Statement of Account. */
  GENERATE_STATEMENT_OF_ACCOUNT,

  /** Manage AI firm profile and configuration settings. */
  AI_MANAGE,
  /** Execute AI skills and generate AI content. */
  AI_EXECUTE,
  /** Review and approve AI-generated outputs requiring attorney oversight. */
  AI_REVIEW,

  /** Manage integration connections (connect/disconnect Xero, etc.). */
  INTEGRATION_MANAGE,
  /** View sync status of accounting entries. */
  INTEGRATION_VIEW_SYNC_STATUS,
  /** Reconcile financial discrepancies between Kazi and external systems (owner-only). */
  FINANCIAL_RECONCILE,

  /**
   * Connect the firm's own Claude (MCP connector) to read live Kazi data. Front-door gate
   * (ADR-304).
   */
  MCP_ACCESS,

  /**
   * Write to Kazi over the MCP connector (file correspondence, attach docs, propose tasks).
   * Distinct from read {@code MCP_ACCESS} and gate-approval {@code AI_REVIEW}. Auto-granted to
   * owner/admin (kept out of {@link #OWNER_ONLY} so admin inherits it); other roles need an
   * explicit grant (ADR-321).
   */
  MCP_WRITE,

  /**
   * Run a client and trust take-on from the firm's previous system (upload, map, validate, commit).
   * Admin-level: auto-granted to owner/admin (kept out of {@link #OWNER_ONLY}); custom roles need
   * an explicit grant. Every take-on read or write also needs the dataset's own capability (Phase
   * 93, §17.9).
   */
  MANAGE_TAKE_ON,

  /**
   * Sign off a trust opening position as the responsible partner or director (Rule 54.19).
   * Owner-only by default (in {@link #OWNER_ONLY}); grantable to a custom "Partner" role (FD-4).
   */
  SIGN_OFF_TRUST_TAKE_ON;

  /** Capabilities restricted to the owner role — admin does NOT inherit these. */
  public static final Set<String> OWNER_ONLY =
      Set.of(
          MANAGE_COMPLIANCE_DESTRUCTIVE.name(),
          OVERRIDE_MATTER_CLOSURE.name(),
          FINANCIAL_RECONCILE.name(),
          SIGN_OFF_TRUST_TAKE_ON.name());

  public static final Set<String> ALL_NAMES =
      Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

  public static Capability fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Invalid capability: null. Valid values: %s".formatted(ALL_NAMES));
    }
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid capability: '%s'. Valid values: %s".formatted(value, ALL_NAMES));
    }
  }
}
