package io.b2mash.b2b.b2bstrawman.collections;

/**
 * The dunning-ladder stage of a {@link CollectionActivity}. Each stage fires when the invoice
 * crosses the corresponding days-overdue threshold configured on {@code CollectionsSettings}
 * (stage1/stage2/stage3/escalate). One {@code collection_activities} row exists per (invoice,
 * stage) — enforced by the {@code ux_collection_activity_invoice_stage} unique index.
 *
 * <ul>
 *   <li>{@link #STAGE_1} — gentle nudge (first reminder email).
 *   <li>{@link #STAGE_2} — firm reminder.
 *   <li>{@link #STAGE_3} — final demand.
 *   <li>{@link #ESCALATION} — flag for a partner call; no email is sent at this stage.
 * </ul>
 */
public enum CollectionStage {
  STAGE_1,
  STAGE_2,
  STAGE_3,
  ESCALATION
}
