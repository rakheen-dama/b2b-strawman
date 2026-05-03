package io.b2mash.b2b.b2bstrawman.billing;

/**
 * Tenant subscription tier. Phase 70 introduces this enum to gate AI specialist visibility (PRO
 * only). Plan-tier expansion (gating logic, billing-tier sync) is owned by epic 511B and later
 * billing slices.
 */
public enum PlanTier {
  STARTER,
  PRO
}
