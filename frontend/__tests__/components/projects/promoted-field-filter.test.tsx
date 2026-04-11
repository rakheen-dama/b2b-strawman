import { describe, it, expect } from "vitest";
import {
  PROMOTED_PROJECT_SLUGS,
  PROMOTED_TASK_SLUGS,
  PROMOTED_INVOICE_SLUGS,
} from "@/lib/constants/promoted-field-slugs";

describe("PROMOTED_PROJECT_SLUGS filter", () => {
  it("includes all Phase 63 promoted project slugs", () => {
    expect(PROMOTED_PROJECT_SLUGS.has("reference_number")).toBe(true);
    expect(PROMOTED_PROJECT_SLUGS.has("priority")).toBe(true);
    expect(PROMOTED_PROJECT_SLUGS.has("engagement_type")).toBe(true);
    expect(PROMOTED_PROJECT_SLUGS.has("matter_type")).toBe(true);
  });

  it("does not include arbitrary custom slugs", () => {
    expect(PROMOTED_PROJECT_SLUGS.has("favorite_color")).toBe(false);
    expect(PROMOTED_PROJECT_SLUGS.has("custom_thing")).toBe(false);
  });

  it("filters field definitions to keep only genuinely custom ones", () => {
    const defs = [
      { id: "1", slug: "reference_number", name: "Ref" },
      { id: "2", slug: "favorite_color", name: "Color" },
      { id: "3", slug: "priority", name: "Priority" },
      { id: "4", slug: "engagement_type", name: "Engagement" },
      { id: "5", slug: "custom_thing", name: "Custom" },
    ];
    const filtered = defs.filter((d) => !PROMOTED_PROJECT_SLUGS.has(d.slug));
    expect(filtered.map((d) => d.slug)).toEqual(["favorite_color", "custom_thing"]);
  });
});

describe("PROMOTED_TASK_SLUGS filter", () => {
  it("includes priority and estimated_hours", () => {
    expect(PROMOTED_TASK_SLUGS.has("priority")).toBe(true);
    expect(PROMOTED_TASK_SLUGS.has("estimated_hours")).toBe(true);
  });
});

describe("PROMOTED_INVOICE_SLUGS filter", () => {
  it("uses backend slug names verbatim", () => {
    // Backend uses purchase_order_number, NOT po_number
    expect(PROMOTED_INVOICE_SLUGS.has("purchase_order_number")).toBe(true);
    expect(PROMOTED_INVOICE_SLUGS.has("po_number")).toBe(false);
    expect(PROMOTED_INVOICE_SLUGS.has("tax_type")).toBe(true);
    expect(PROMOTED_INVOICE_SLUGS.has("billing_period_start")).toBe(true);
    expect(PROMOTED_INVOICE_SLUGS.has("billing_period_end")).toBe(true);
  });

  it("filters out promoted invoice slugs while keeping unrelated ones", () => {
    const defs = [
      { id: "1", slug: "purchase_order_number", name: "PO" },
      { id: "2", slug: "tax_type", name: "Tax" },
      { id: "3", slug: "project_code", name: "Code" },
    ];
    const filtered = defs.filter((d) => !PROMOTED_INVOICE_SLUGS.has(d.slug));
    expect(filtered.map((d) => d.slug)).toEqual(["project_code"]);
  });
});
