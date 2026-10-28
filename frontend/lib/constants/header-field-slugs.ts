import type { FieldDefinitionResponse } from "@/lib/types";

/**
 * Pack custom fields shown as a facts strip under the record title in the matter and client
 * header cards (Phase 88 D9, §13.3.17).
 *
 * Hard-coded legal-za list (Kazi is legal-only since Phase 86; a pack-declared header flag is
 * future work). Not to be confused with PROMOTED_*_SLUGS (promoted-field-slugs.ts) — those
 * slugs became structural columns; these stay in `customFields`.
 */
export const HEADER_FIELD_SLUGS = {
  PROJECT: ["case_number", "court_name", "opposing_party"],
  CUSTOMER: ["id_passport_number"],
} as const satisfies Record<string, readonly string[]>;

/** Visible short prefix per slug; slugs without one get a screen-reader-only label. */
export const HEADER_FIELD_PREFIXES: Readonly<Record<string, string>> = {
  case_number: "Case no.",
  id_passport_number: "ID",
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
