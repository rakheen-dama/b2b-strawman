import type { FieldDefinitionResponse } from "@/lib/types";

/**
 * Pack custom fields shown as a facts strip under the record title in the matter and client
 * header cards (Phase 88 D9, §13.3.17).
 *
 * Hard-coded legal-za list (Kazi is legal-only since Phase 86; a pack-declared header flag is
 * future work). Not to be confused with PROMOTED_*_SLUGS (promoted-field-slugs.ts) — those
 * slugs became structural columns; these stay in `customFields`.
 */
/** The client ID / passport number pack field (legal-za `legal-za-customer.json`). */
export const CLIENT_ID_SLUG = "id_passport_number";

export const HEADER_FIELD_SLUGS = {
  PROJECT: ["case_number", "court_name", "opposing_party"],
  CUSTOMER: [CLIENT_ID_SLUG],
} as const satisfies Record<string, readonly string[]>;

/** Visible short prefix per slug; slugs without one get a screen-reader-only label. */
export const HEADER_FIELD_PREFIXES: Readonly<Partial<Record<string, string>>> = {
  case_number: "Case no.",
  [CLIENT_ID_SLUG]: "ID",
};

export interface HeaderField {
  slug: string;
  /** Field-definition name (e.g. "Court"); the slug when the definition is not loaded. */
  label: string;
  value: string;
}

/** Ordered, non-blank header facts from a record's custom-field values. */
export function buildHeaderFields(
  slugs: readonly string[],
  customFields: Record<string, unknown> | null | undefined,
  definitions: readonly FieldDefinitionResponse[]
): HeaderField[] {
  return slugs.flatMap((slug) => {
    const raw = customFields?.[slug];
    if (typeof raw !== "string" && typeof raw !== "number") return [];
    const value = String(raw).trim();
    if (value === "") return [];
    const label = definitions.find((d) => d.slug === slug)?.name ?? slug;
    return [{ slug, label, value }];
  });
}

/**
 * Client header values: the structural `idNumber` (what the Edit dialog writes and the conflict
 * check matches) wins over the legacy `id_passport_number` custom field when non-blank — the two
 * stores can diverge because the Edit dialog updates only the structural column.
 */
export function clientHeaderFieldValues(
  customFields: Record<string, unknown> | null | undefined,
  idNumber: string | null | undefined
): Record<string, unknown> {
  const structural = idNumber?.trim();
  return structural ? { ...customFields, [CLIENT_ID_SLUG]: structural } : { ...customFields };
}
