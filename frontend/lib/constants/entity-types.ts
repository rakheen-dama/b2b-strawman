/**
 * Canonical entity types accepted by the backend `Customer.entityType` field.
 *
 * The backend stores `entity_type` as a free-form `String(length=30)`, but the
 * frontend constrains input to this fixed list via a Select so users cannot
 * enter arbitrary values.
 */
export interface EntityTypeOption {
  value: string;
  label: string;
}

export const ENTITY_TYPES: readonly EntityTypeOption[] = [
  { value: "INDIVIDUAL", label: "Individual" },
  { value: "SOLE_PROP", label: "Sole Proprietor" },
  { value: "PTY_LTD", label: "Pty Ltd (Private Company)" },
  { value: "CC", label: "Close Corporation" },
  { value: "PARTNERSHIP", label: "Partnership" },
  { value: "TRUST", label: "Trust" },
  { value: "NON_PROFIT", label: "Non-Profit" },
  { value: "PUBLIC_COMPANY", label: "Public Company" },
] as const;

export const ENTITY_TYPE_VALUES = ENTITY_TYPES.map((e) => e.value);
