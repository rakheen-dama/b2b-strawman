package io.b2mash.b2b.b2bstrawman.crm;

/**
 * The lifecycle category of a {@link PipelineStage}. Every pipeline must always have at least one
 * stage of each type (Phase 80, §11.2.1).
 */
public enum StageType {
  /** A stage where the deal is still open / in progress. */
  OPEN,
  /** A terminal stage representing a won deal. */
  WON,
  /** A terminal stage representing a lost deal. */
  LOST
}
