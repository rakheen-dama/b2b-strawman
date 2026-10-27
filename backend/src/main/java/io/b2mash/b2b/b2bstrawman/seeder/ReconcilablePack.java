package io.b2mash.b2b.b2bstrawman.seeder;

import java.util.List;

/**
 * Implemented by pack definition records that carry an ADR-356 {@code reconcile} block. {@link
 * AbstractPackSeeder#getReconcileEntries} reads it generically.
 */
public interface ReconcilablePack {

  /** Raw entries as deserialised; {@code null} when the pack JSON has no block. */
  List<PackReconcileEntry> reconcile();
}
