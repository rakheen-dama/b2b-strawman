import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

// Phase 87 S9 (616C, ADR-341): the Fees & Trust settings family page — the first family with
// module-gated tabs (Fee runs: bulk_billing, Trust accounting: trust_accounting), so the shared
// seam resolves enabled modules through getOrgSettings. Tab loaders are mocked marker strings.
const { loaders, mockCaps, mockGetOrgSettings, mockReplace } = vi.hoisted(() => ({
  loaders: {
    tariffs: vi.fn(async () => "tariffs panel"),
    tax: vi.fn(async () => "tax panel"),
    "time-recording": vi.fn(async () => "time recording panel"),
    "fee-runs": vi.fn(async () => "fee runs panel"),
    collections: vi.fn(async () => "collections panel"),
    "trust-accounting": vi.fn(async () => "trust accounting panel"),
  },
  mockCaps: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockReplace: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: () => mockGetOrgSettings() }));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/tariffs", () => ({
  renderTariffsTab: loaders.tariffs,
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/tax", () => ({
  renderTaxTab: loaders.tax,
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/time-recording", () => ({
  renderTimeRecordingTab: loaders["time-recording"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs", () => ({
  renderFeeRunsTab: loaders["fee-runs"],
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/collections", () => ({
  renderCollectionsTab: loaders.collections,
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/trust-accounting", () => ({
  renderTrustAccountingTab: loaders["trust-accounting"],
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/fees",
  useSearchParams: () => new URLSearchParams(),
}));

import FeesSettingsPage, { generateMetadata } from "@/app/(app)/org/[slug]/settings/fees/page";
import { FEES_TAB_LOADERS } from "@/app/(app)/org/[slug]/settings/fees/_tabs";
import type { SettingsSearchParams } from "@/components/settings/settings-family-page";
import { getSettingsFamily } from "@/lib/settings-nav";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

const BOTH_MODULES = ["bulk_billing", "trust_accounting"];
const ALL_FEES_TABS = [
  "Tariffs",
  "Tax",
  "Time recording",
  "Fee runs",
  "Collections",
  "Trust accounting",
];

function props(searchParams: SettingsSearchParams) {
  return {
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  };
}

async function renderFees(tab?: string | string[]) {
  const searchParams = tab === undefined ? {} : { tab };
  render(await FeesSettingsPage(props(searchParams)));
}

function tabNames() {
  const list = screen.getByRole("tablist", { name: "Fees & Trust settings" });
  return within(list)
    .getAllByRole("tab")
    .map((t) => t.textContent);
}

function calledLoaders() {
  return Object.entries(loaders)
    .filter(([, fn]) => fn.mock.calls.length > 0)
    .map(([id]) => id);
}

function withModules(enabledModules: string[]) {
  mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", enabledModules });
}

describe("Fees & Trust settings family page", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    withModules(BOTH_MODULES);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders all six tabs in §12.7.1 order for an owner with both modules and loads only Tariffs", async () => {
    await renderFees();
    expect(tabNames()).toEqual(ALL_FEES_TABS);
    expect(screen.getByRole("tab", { name: "Tariffs" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("tariffs panel")).toBeInTheDocument();
    expect(calledLoaders()).toEqual(["tariffs"]);
  });

  it("resolves enabled modules through org settings (Fees has module-gated tabs)", async () => {
    await renderFees();
    expect(mockGetOrgSettings).toHaveBeenCalled();
  });

  it("hides the module-gated tabs when neither module is enabled", async () => {
    withModules([]);
    await renderFees();
    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Collections"]);
  });

  it("shows Fee runs but not Trust accounting to an admin with bulk_billing only", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    withModules(["bulk_billing"]);
    await renderFees();
    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Fee runs", "Collections"]);
  });

  it("treats a failing org-settings fetch as no modules — never an error page", async () => {
    mockGetOrgSettings.mockRejectedValue(new Error("network"));
    await renderFees();
    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Collections"]);
    expect(screen.getByText("tariffs panel")).toBeInTheDocument();
  });

  it("falls back to Tariffs with a notice when the module of a requested tab is off", async () => {
    withModules([]);
    await renderFees("trust-accounting");
    expect(screen.getByText("tariffs panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /don.t have access to Trust accounting — showing Tariffs instead/
    );
    expect(loaders["trust-accounting"]).not.toHaveBeenCalled();
  });

  it("renders the requested tab's panel and passes the loader slug and query", async () => {
    await renderFees("time-recording");
    expect(screen.getByRole("tab", { name: "Time recording" })).toHaveAttribute(
      "aria-selected",
      "true"
    );
    expect(loaders["time-recording"]).toHaveBeenCalledWith({
      slug: "acme",
      searchParams: { tab: "time-recording" },
    });
    expect(calledLoaders()).toEqual(["time-recording"]);
  });

  it("falls back to Tariffs for an unknown tab silently — never a 404", async () => {
    await renderFees("rates"); // the old page name is not a tab id
    expect(screen.getByText("tariffs panel")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  // D19 (D6/D18 rule) + D20: a member keeps only Tariffs (its loader keeps the in-page
  // FINANCIAL_VISIBILITY denial); Tax and Time recording were denial-only pages → adminOnly.
  it("shows a member only Tariffs — admin-only tabs are absent, not disabled", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderFees();
    expect(tabNames()).toEqual(["Tariffs"]);
    for (const tab of screen.getAllByRole("tab")) {
      expect(tab).not.toHaveAttribute("data-disabled");
      expect(tab).not.toBeDisabled();
    }
  });

  it("falls back to Tariffs with a notice when a member asks for Tax", async () => {
    mockCaps.mockResolvedValue(MEMBER);
    await renderFees("tax");
    expect(screen.getByText("tariffs panel")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      /don.t have access to Tax — showing Tariffs instead/
    );
    expect(loaders.tax).not.toHaveBeenCalled();
  });

  it("navigates to ?tab=<id> with router.replace (scroll: false) on tab change", async () => {
    const user = userEvent.setup();
    await renderFees();
    await user.click(screen.getByRole("tab", { name: "Collections" }));
    expect(mockReplace).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith("/org/acme/settings/fees?tab=collections", {
      scroll: false,
    });
  });

  it("declares exactly one loader per Fees tab in settings-nav", () => {
    const tabIds = getSettingsFamily("fees").tabs.map((t) => t.id);
    expect(Object.keys(FEES_TAB_LOADERS).sort()).toEqual([...tabIds].sort());
    for (const id of tabIds) {
      expect(FEES_TAB_LOADERS[id as keyof typeof FEES_TAB_LOADERS]).toBe(
        loaders[id as keyof typeof loaders]
      );
    }
  });

  describe("generateMetadata", () => {
    it("titles the page Fees & Trust — <Tab> · Settings", async () => {
      expect(await generateMetadata(props({ tab: "fee-runs" }))).toEqual({
        title: "Fees & Trust — Fee runs · Settings",
      });
      expect(await generateMetadata(props({}))).toEqual({
        title: "Fees & Trust — Tariffs · Settings",
      });
    });

    it("uses the resolved tab for a member's forbidden tab", async () => {
      mockCaps.mockResolvedValue(MEMBER);
      expect(await generateMetadata(props({ tab: "collections" }))).toEqual({
        title: "Fees & Trust — Tariffs · Settings",
      });
    });
  });
});
