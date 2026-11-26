import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CompliancePackList } from "@/components/compliance/CompliancePackList";
import type { CompliancePackDetail } from "@/lib/types";

// Phase 91 665A: the pack detail's two raw tables render through DataTable (client mode).

// DataTable calls useRouter(); vitest has no app router mounted.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

const { mockGetDetail } = vi.hoisted(() => ({ mockGetDetail: vi.fn() }));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions", () => ({
  getCompliancePackDetail: (...args: unknown[]) => mockGetDetail(...args),
}));

const DETAIL: CompliancePackDetail = {
  packId: "fica-kyc-za",
  name: "FICA KYC (South Africa)",
  description: "Know-your-client checks.",
  version: "1.0.0",
  jurisdiction: "ZA",
  customerType: "INDIVIDUAL",
  checklistTemplate: {
    name: "FICA",
    slug: "fica",
    autoInstantiate: true,
    // Deliberately out of order: the table shows them by sortOrder.
    items: [
      {
        name: "Proof of address",
        description: "Not older than 3 months.",
        sortOrder: 2,
        required: false,
        requiresDocument: true,
        requiredDocumentLabel: "Utility bill",
        dependsOnItemKey: null,
      },
      {
        name: "ID document",
        description: "Green ID book or smart card.",
        sortOrder: 1,
        required: true,
        requiresDocument: false,
        requiredDocumentLabel: null,
        dependsOnItemKey: null,
      },
    ],
  },
  fieldDefinitions: null,
  retentionOverrides: [
    {
      recordType: "CUSTOMER",
      triggerEvent: "CUSTOMER_OFFBOARDED",
      retentionDays: 1825,
      action: "FLAG",
    },
  ],
};

afterEach(() => {
  cleanup();
  mockGetDetail.mockReset();
});

describe("CompliancePackList", () => {
  it("keeps the no-packs line as text", () => {
    render(<CompliancePackList packs={[]} />);
    expect(screen.getByText("No compliance packs applied.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("renders the checklist items and retention overrides through DataTable", async () => {
    const user = userEvent.setup();
    const detail = structuredClone(DETAIL);
    mockGetDetail.mockResolvedValue(detail);
    render(
      <CompliancePackList
        packs={[{ packId: "fica-kyc-za", version: "1.0.0", appliedAt: "2026-11-26T10:00:00Z" }]}
      />
    );

    await user.click(screen.getByRole("button", { name: /fica-kyc-za/ }));
    await screen.findByText("Checklist Items");
    expect(mockGetDetail).toHaveBeenCalledWith("fica-kyc-za");

    const tables = screen.getAllByRole("table");
    expect(tables).toHaveLength(2);
    for (const table of tables) {
      expect(table.closest('[data-slot="data-table"]')).not.toBeNull();
    }

    const [items, overrides] = tables;
    expect(
      within(items)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual(["Name", "Description", "Required", "Document Required"]);
    const [, first, second] = within(items).getAllByRole("row");
    expect(
      within(first)
        .getAllByRole("cell")
        .map((c) => c.textContent)
    ).toEqual(["ID document", "Green ID book or smart card.", "Yes", "No"]);
    expect(
      within(second)
        .getAllByRole("cell")
        .map((c) => c.textContent)
    ).toEqual(["Proof of address", "Not older than 3 months.", "No", "Utility bill"]);
    // Sorted on a copy: the cached detail keeps its original order.
    expect(detail.checklistTemplate?.items.map((i) => i.name)).toEqual([
      "Proof of address",
      "ID document",
    ]);

    expect(
      within(overrides)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual(["Record Type", "Trigger", "Days", "Action"]);
    const [, override] = within(overrides).getAllByRole("row");
    expect(
      within(override)
        .getAllByRole("cell")
        .map((c) => c.textContent)
    ).toEqual(["CUSTOMER", "CUSTOMER_OFFBOARDED", "1825", "FLAG"]);
    expect(screen.getByRole("heading", { level: 4, name: "Retention Overrides" })).toBeVisible();
  });
});
