package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

/**
 * Allowed closure-reason values for a matter (Phase 67, ADR-248). Mirrors the {@code
 * ck_matter_closure_log_reason} DB CHECK constraint — keep in sync when adding values.
 */
public enum ClosureReason {
  CONCLUDED,
  CLIENT_TERMINATED,
  REFERRED_OUT,
  OTHER
}
