package io.b2mash.b2b.b2bstrawman.clause;

/** Source of a clause in the clause library. */
public enum ClauseSource {
  /** Read-only clause created by pack seeder. */
  SYSTEM,

  /** Customizable copy of a SYSTEM clause. */
  CLONED,

  /** Org-created clause by admin/owner. */
  CUSTOM
}
