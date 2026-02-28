package io.b2mash.b2b.b2bstrawman.invoice;

/** Discriminator for invoice line types (ADR-118). */
public enum InvoiceLineType {
  TIME,
  EXPENSE,
  RETAINER,
  MANUAL
}
