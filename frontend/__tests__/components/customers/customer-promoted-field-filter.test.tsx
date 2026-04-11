import { describe, it, expect } from "vitest";
import { PROMOTED_CUSTOMER_SLUGS } from "@/lib/constants/promoted-field-slugs";

describe("PROMOTED_CUSTOMER_SLUGS filter", () => {
  it("includes all Phase 63 promoted slugs", () => {
    expect(PROMOTED_CUSTOMER_SLUGS.has("address_line1")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("city")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("country")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("tax_number")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("vat_number")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("primary_contact_email")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("financial_year_end")).toBe(true);
    expect(PROMOTED_CUSTOMER_SLUGS.has("registration_number")).toBe(true);
  });

  it("excludes non-promoted custom slugs", () => {
    expect(PROMOTED_CUSTOMER_SLUGS.has("favorite_color")).toBe(false);
    expect(PROMOTED_CUSTOMER_SLUGS.has("internal_notes")).toBe(false);
  });

  it("filters field definitions to keep only genuinely custom ones", () => {
    const defs = [
      { id: "1", slug: "address_line1", name: "Address Line 1" },
      { id: "2", slug: "favorite_color", name: "Favorite Color" },
      { id: "3", slug: "vat_number", name: "VAT" },
      { id: "4", slug: "internal_notes", name: "Internal Notes" },
      { id: "5", slug: "registration_number", name: "Registration" },
    ];
    const filtered = defs.filter((d) => !PROMOTED_CUSTOMER_SLUGS.has(d.slug));
    expect(filtered.map((d) => d.slug)).toEqual(["favorite_color", "internal_notes"]);
  });
});
