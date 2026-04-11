package io.b2mash.b2b.b2bstrawman.verticals;

/**
 * Classification of a module for gating and UI rendering purposes.
 *
 * <p>See ADR-239 (Horizontal vs. Vertical Module Gating).
 */
public enum ModuleCategory {
  /** Auto-assigned by vertical profile selection. Not shown in Settings. */
  VERTICAL,

  /** Manually toggled by org admin in Settings → Features. Profile-independent. */
  HORIZONTAL
}
