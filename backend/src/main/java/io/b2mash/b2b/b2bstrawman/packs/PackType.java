package io.b2mash.b2b.b2bstrawman.packs;

/**
 * Types of content packs tracked per tenant in {@code pack_install}.
 *
 * <p>{@link #DOCUMENT_TEMPLATE} and {@link #AUTOMATION_TEMPLATE} are installer-managed: their rows
 * are created, reconciled and uninstalled by a {@link PackInstaller} and surface in Settings ›
 * Packs. {@link #CLAUSE}, {@link #FIELD}, {@link #REPORT} and {@link #REQUEST} rows are an internal
 * ledger written by their pack seeders so the ADR-356 reconcile version gate ({@link
 * PackVersionGate}) has a recorded version to compare against; they are hidden from the Packs API.
 */
public enum PackType {
  DOCUMENT_TEMPLATE,
  AUTOMATION_TEMPLATE,
  CLAUSE,
  FIELD,
  REPORT,
  REQUEST;

  /**
   * True for types whose pack_install rows are created, reconciled and uninstalled by a {@link
   * PackInstaller} (Settings › Packs). The other types' rows are an internal ledger written by
   * their seeders for the ADR-356 version gate and are hidden from the Packs API.
   */
  public boolean isInstallerManaged() {
    return this == DOCUMENT_TEMPLATE || this == AUTOMATION_TEMPLATE;
  }
}
