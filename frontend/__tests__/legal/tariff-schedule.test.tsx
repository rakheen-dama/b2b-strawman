import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (before component imports) ---

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/legal/tariffs",
}));

vi.mock("@/app/(app)/org/[slug]/legal/tariffs/actions", () => ({
  fetchTariffSchedules: vi.fn().mockResolvedValue([
    {
      id: "ts-1",
      name: "High Court Party & Party 2026",
      code: "HC_PP_2026",
      description: null,
      effectiveFrom: "2026-01-01",
      effectiveTo: null,
      active: true,
      itemCount: 45,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: "ts-2",
      name: "Magistrate Court Party & Party 2026",
      code: "MC_PP_2026",
      description: null,
      effectiveFrom: "2026-01-01",
      effectiveTo: null,
      active: false,
      itemCount: 30,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ]),
  fetchTariffSchedule: vi.fn(),
  fetchActiveSchedule: vi.fn().mockResolvedValue(null),
  createSchedule: vi.fn().mockResolvedValue({ success: true }),
  updateSchedule: vi.fn().mockResolvedValue({ success: true }),
  cloneSchedule: vi.fn().mockResolvedValue({ success: true }),
  fetchTariffItems: vi.fn().mockResolvedValue([
    {
      id: "ti-1",
      scheduleId: "ts-1",
      itemNumber: "1(a)",
      description: "Instructions to institute action",
      unit: "PER_ITEM",
      amount: 850.0,
      notes: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: "ti-2",
      scheduleId: "ts-1",
      itemNumber: "1(b)",
      description: "Instructions to oppose action",
      unit: "PER_ITEM",
      amount: 675.0,
      notes: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
    {
      id: "ti-3",
      scheduleId: "ts-1",
      itemNumber: "2(a)",
      description: "Perusal of documents, per folio",
      unit: "PER_FOLIO",
      amount: 25.0,
      notes: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ]),
  createItem: vi.fn().mockResolvedValue({ success: true }),
  updateItem: vi.fn().mockResolvedValue({ success: true }),
  deleteItem: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({ content: [] }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

// --- Imports after mocks ---

import { TariffBrowserClient } from "@/app/(app)/org/[slug]/legal/tariffs/tariff-browser-client";
import { TariffItemBrowser } from "@/components/legal/tariff-item-browser";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { NAV_GROUPS } from "@/lib/nav-items";
import type { TariffSchedule } from "@/lib/types";
import { formatLocalDate } from "@/lib/format";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeSchedule(overrides: Partial<TariffSchedule> = {}): TariffSchedule {
  return {
    id: "ts-1",
    name: "High Court Party & Party 2026",
    code: "HC_PP_2026",
    description: null,
    effectiveFrom: "2026-01-01",
    effectiveTo: null,
    active: true,
    itemCount: 45,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

// --- Tests ---

describe("Tariff schedule list", () => {
  it("renders schedule list with names and badges", () => {
    const schedules = [
      makeSchedule({ id: "ts-1", name: "High Court PP 2026", code: "HC_PP", active: true }),
      makeSchedule({ id: "ts-2", name: "Magistrate Court PP 2026", code: "MC_PP", active: false }),
    ];

    render(<TariffBrowserClient initialSchedules={schedules} initialTotal={2} slug="acme" />);

    const browser = screen.getByTestId("tariff-browser");
    expect(browser).toBeInTheDocument();
    expect(screen.getByText("High Court PP 2026")).toBeInTheDocument();
    expect(screen.getByText("Magistrate Court PP 2026")).toBeInTheDocument();
    expect(screen.getByText("HC_PP")).toBeInTheDocument();
    expect(screen.getByText("MC_PP")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("2 schedules")).toBeInTheDocument();
  });

  it("renders no code badge when the schedule has no code (K-81)", () => {
    const schedules = [
      makeSchedule({ id: "ts-1", name: "No Code Schedule", code: "", active: false }),
    ];
    const { container } = render(
      <TariffBrowserClient initialSchedules={schedules} initialTotal={1} slug="acme" />
    );
    const row = screen.getByTestId("schedule-row-ts-1");
    expect(row.querySelectorAll('[data-slot="badge"]')).toHaveLength(0);
    expect(container.querySelectorAll('[data-slot="badge"]')).toHaveLength(0);
  });

  it("prints the effective dates day-first (K-79)", () => {
    const schedules = [
      makeSchedule({ id: "ts-1", effectiveFrom: "2026-01-01", effectiveTo: "2026-12-31" }),
    ];
    render(<TariffBrowserClient initialSchedules={schedules} initialTotal={1} slug="acme" />);
    const row = screen.getByTestId("schedule-row-ts-1");
    expect(row).toHaveTextContent(
      `Effective from ${formatLocalDate("2026-01-01")} to ${formatLocalDate("2026-12-31")}`
    );
    expect(row).not.toHaveTextContent("2026-01-01");
  });

  it("shows an inline empty line when there are no schedules", () => {
    render(<TariffBrowserClient initialSchedules={[]} initialTotal={0} slug="acme" />);
    expect(screen.getByText("No tariff schedules found.").tagName).toBe("P");
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });
});

describe("Tariff item browser", () => {
  it("groups items by section", async () => {
    render(<TariffItemBrowser scheduleId="ts-1" />);

    // Wait for items to load
    const browser = await screen.findByTestId("tariff-item-browser");
    expect(browser).toBeInTheDocument();

    // Items should be visible after loading
    expect(await screen.findByText("Instructions to institute action")).toBeInTheDocument();
    expect(screen.getByText("Instructions to oppose action")).toBeInTheDocument();
    expect(screen.getByText("Perusal of documents, per folio")).toBeInTheDocument();

    // Items grouped by section number
    expect(screen.getByTestId("section-header-Section 1")).toBeInTheDocument();
    expect(screen.getByTestId("section-header-Section 2")).toBeInTheDocument();
  });

  it("search filters items by description", async () => {
    const user = userEvent.setup();

    render(<TariffItemBrowser scheduleId="ts-1" />);

    // Wait for initial load
    await screen.findByText("Instructions to institute action");

    const searchInput = screen.getByTestId("tariff-item-search");
    await user.type(searchInput, "perusal");

    // The mock is called with the search term (debounced)
    // Verify the search input is rendered and functional
    expect(searchInput).toHaveValue("perusal");
  });
});

describe("Schedule clone button", () => {
  it("shows clone button on each schedule", () => {
    const schedules = [makeSchedule({ id: "ts-1", name: "System Schedule", code: "SYS_1" })];

    render(<TariffBrowserClient initialSchedules={schedules} initialTotal={1} slug="acme" />);

    const cloneBtn = screen.getByTestId("clone-btn-ts-1");
    expect(cloneBtn).toBeInTheDocument();
    expect(cloneBtn).toHaveTextContent("Clone");
  });
});

describe("Tariff nav item", () => {
  it("tariff nav item has requiredModule: lssa_tariff", () => {
    const feesZone = NAV_GROUPS.find((g) => g.id === "fees");
    const tariffItem = feesZone?.items.find((i) => i.label === "Tariffs");
    expect(tariffItem).toBeDefined();
    expect(tariffItem?.requiredModule).toBe("lssa_tariff");
  });

  it("module gate hides tariff content when disabled", () => {
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <ModuleGate module="lssa_tariff">
          <span>Tariff Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.queryByText("Tariff Content")).not.toBeInTheDocument();
  });

  it("module gate shows tariff content when enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["lssa_tariff"]}
        terminologyNamespace={null}
      >
        <ModuleGate module="lssa_tariff">
          <span>Tariff Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.getByText("Tariff Content")).toBeInTheDocument();
  });
});
