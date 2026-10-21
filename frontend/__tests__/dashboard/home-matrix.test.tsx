import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import type { HomeWidgetData } from "@/lib/dashboard-home-fetch";
import type { HomePlan } from "@/lib/dashboard-home";

// Phase 87 614B — Home renders the §12.6.3 widget matrix per audience: fee earner, bookkeeper /
// admin, owner. Present or absent, never greyed; the fan-out (mocked here, tested in
// dashboard-actions.test.ts) is only asked for what will render.

vi.mock("server-only", () => ({}));

const { mockCaps, mockSettings, mockFetchHome, mockFacets, mockAudit, MockApiError } = vi.hoisted(
  () => {
    class MockApiError extends Error {
      constructor(
        public status: number,
        message: string,
        public detail?: { title?: string }
      ) {
        super(message);
        this.name = "ApiError";
      }
    }
    return {
      mockCaps: vi.fn(),
      mockSettings: vi.fn(),
      mockFetchHome: vi.fn(),
      mockFacets: vi.fn(),
      mockAudit: vi.fn(),
      MockApiError,
    };
  }
);

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: mockSettings }));
vi.mock("@/lib/api", () => ({
  ApiError: MockApiError,
  handleApiError: (error: unknown) => {
    throw error;
  },
}));
vi.mock("@/lib/api/audit-events", () => ({
  listFacetEventTypes: mockFacets,
  listAuditEvents: mockAudit,
}));
vi.mock("@/lib/dashboard-home-fetch", () => ({ fetchHomeWidgets: mockFetchHome }));
vi.mock("@/components/auth/return-to-handler", () => ({ ReturnToHandler: () => null }));
vi.mock("@/app/(app)/org/[slug]/dashboard/provisioning-pending-refresh", () => ({
  ProvisioningPendingRefresh: () => null,
}));
// The sensitive-events widget gates itself client-side via the CapabilityProvider; the server gate
// is what this test pins, so stand it in with a marker.
vi.mock("@/components/dashboard/sensitive-events-widget", () => ({
  SensitiveEventsWidget: () => <div data-testid="sensitive-events-widget" />,
}));
vi.mock("@/hooks/use-onboarding-progress", () => ({
  useOnboardingProgress: () => ({
    steps: [{ code: "CREATE_PROJECT", completed: true }],
    completedCount: 1,
    totalCount: 6,
    percentComplete: 17,
    allComplete: false,
    dismissed: false,
    loading: false,
    dismiss: vi.fn(),
  }),
}));
vi.mock("@/lib/messages", () => ({ createMessages: () => ({ t: (key: string) => key }) }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/dashboard",
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
}));
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import OrgDashboardPage from "@/app/(app)/org/[slug]/dashboard/page";

const ALL_MODULES = ["trust_accounting", "disbursements", "court_calendar"];

const FEE_EARNER_CAPS = {
  capabilities: ["VIEW_LEGAL", "VIEW_TRUST", "MANAGE_DISBURSEMENTS"],
  role: "Member",
  isAdmin: false,
  isOwner: false,
};
const ADMIN_CAPS = { capabilities: [], role: "Admin", isAdmin: true, isOwner: false };
const OWNER_CAPS = { capabilities: [], role: "Owner", isAdmin: false, isOwner: true };

const WIP_FULL = {
  asOf: "2026-10-21",
  currency: "ZAR",
  totalMinutes: 600,
  totalValue: 18000,
  unbilledDisbursementTotal: null,
  byFeeEarner: [
    {
      memberId: "m1",
      memberName: "Thandi Nkosi",
      minutes: 600,
      value: 18000,
      oldestEntryDate: "2026-10-01",
    },
  ],
  redacted: false,
};
const WIP_REDACTED = {
  ...WIP_FULL,
  totalMinutes: 120,
  totalValue: null,
  byFeeEarner: [{ ...WIP_FULL.byFeeEarner[0], minutes: 120, value: null }],
  redacted: true,
};
const TRUST = {
  trustAccountId: "ta1",
  accountName: "Trust account",
  bankName: "Standard Bank",
  cashbookBalance: 1000,
  clientLedgerTotal: 1000,
  difference: 0,
  lastReconciliationPeriodEnd: null,
  lastReconciliationStatus: null,
  lastReconciliationBalanced: null,
  daysSinceReconciliation: null,
  pendingApprovalCount: 0,
  accounts: [{ id: "ta1", accountName: "Trust account", isPrimary: true }],
};
const FICA = { outstandingCount: 0, totalActiveClients: 3, clients: [], limit: 5 };
const DEBTORS = { content: [], page: { totalElements: 0, totalPages: 0, size: 5, number: 0 } };

const ok = <T,>(data: T) => ({ state: "ok" as const, data });
const ABSENT = { state: "absent" as const };

/** What the fan-out returns for a plan — only the planned reads come back loaded. */
function dataFor(plan: HomePlan): HomeWidgetData {
  return {
    diary: [],
    tasks: plan.tasks ? ok({ assigned: [], unassigned: [] }) : ABSENT,
    unbilledWip: plan.unbilledWip
      ? ok(plan.audience === "feeEarner" ? WIP_REDACTED : WIP_FULL)
      : ABSENT,
    trustPosition: plan.trustPosition ? ok(TRUST) : ABSENT,
    ficaOutstanding: plan.ficaOutstanding ? ok(FICA) : ABSENT,
    debtors: plan.debtors ? ok(DEBTORS) : ABSENT,
    activity: [],
    matterHealth: plan.matterHealth ? [] : null,
  };
}

async function renderHome() {
  render(await OrgDashboardPage({ params: Promise.resolve({ slug: "acme" }) }));
}

/** Widget cells in DOM order, grouped by row. */
function cellRows(): string[][] {
  return Array.from(document.querySelectorAll("[data-testid='home-page'] > div.grid")).map((row) =>
    Array.from(row.querySelectorAll(":scope > [data-testid^='home-cell-']")).map((cell) =>
      (cell.getAttribute("data-testid") ?? "").replace("home-cell-", "")
    )
  );
}

function plannedWith(): HomePlan {
  return mockFetchHome.mock.calls[0][0] as HomePlan;
}

describe("Home widget matrix (614B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSettings.mockResolvedValue({ enabledModules: ALL_MODULES });
    mockFetchHome.mockImplementation(async (plan: HomePlan) => dataFor(plan));
    mockFacets.mockResolvedValue([]);
    mockAudit.mockResolvedValue({ content: [] });
  });

  afterEach(() => cleanup());

  it("fee earner: diary → my tasks + my unbilled time → matter health + activity", async () => {
    mockCaps.mockResolvedValue(FEE_EARNER_CAPS);
    await renderHome();

    expect(screen.getByRole("heading", { level: 1, name: "Home" })).toBeInTheDocument();
    expect(screen.getByTestId("home-page")).toHaveAttribute("data-audience", "feeEarner");
    expect(cellRows()).toEqual([["diary"], ["tasks", "unbilledWip"], ["matterHealth", "activity"]]);
    expect(screen.getByTestId("home-diary-widget")).toBeInTheDocument();
    expect(screen.getByTestId("my-tasks-widget")).toBeInTheDocument();
    expect(screen.getByTestId("matter-health-panel")).toBeInTheDocument();
    for (const absent of [
      "trust-position-widget",
      "debtors-widget",
      "fica-outstanding-widget",
      "sensitive-events-widget",
    ]) {
      expect(screen.queryByTestId(absent)).not.toBeInTheDocument();
    }
    // Fetch only what will render — VIEW_TRUST on the member role does not fetch trust.
    expect(plannedWith()).toMatchObject({
      diaryScope: "me",
      trustPosition: false,
      debtors: false,
      ficaOutstanding: false,
      sensitive: false,
    });
    expect(mockFacets).not.toHaveBeenCalled();
    expect(mockAudit).not.toHaveBeenCalled();
  });

  it("fee earner redaction: 'My unbilled time' in hours only — no values, no firm total", async () => {
    mockCaps.mockResolvedValue(FEE_EARNER_CAPS);
    await renderHome();

    const card = screen.getByTestId("unbilled-wip-widget");
    expect(within(card).getByText("My unbilled time")).toBeInTheDocument();
    expect(within(card).getByTestId("unbilled-wip-headline")).toHaveTextContent("2h");
    expect(card.textContent).not.toMatch(/R\s?\d/);
    expect(screen.queryByText("Unbilled WIP")).not.toBeInTheDocument();
    expect(within(card).getByRole("link", { name: "Open timesheet" })).toBeInTheDocument();
  });

  it("bookkeeper / admin: WIP + debtors + trust → diary + FICA → activity + sensitive", async () => {
    mockCaps.mockResolvedValue(ADMIN_CAPS);
    await renderHome();

    expect(screen.getByTestId("home-page")).toHaveAttribute("data-audience", "firm");
    expect(cellRows()).toEqual([
      ["unbilledWip", "debtors", "trustPosition"],
      ["diary", "ficaOutstanding"],
      ["activity", "sensitive"],
    ]);
    expect(
      within(screen.getByTestId("unbilled-wip-widget")).getByText("Unbilled WIP")
    ).toBeVisible();
    expect(screen.getByTestId("debtors-widget")).toBeInTheDocument();
    expect(screen.getByText("Balanced")).toBeInTheDocument();
    expect(screen.getByTestId("fica-outstanding-widget")).toBeInTheDocument();
    expect(screen.getByTestId("sensitive-events-widget")).toBeInTheDocument();
    expect(screen.queryByTestId("my-tasks-widget")).not.toBeInTheDocument();
    expect(screen.queryByTestId("matter-health-panel")).not.toBeInTheDocument();
    expect(plannedWith().diaryScope).toBe("firm");
  });

  it("owner: diary + WIP → debtors + trust → FICA + health + activity + sensitive", async () => {
    mockCaps.mockResolvedValue(OWNER_CAPS);
    await renderHome();

    expect(screen.getByTestId("home-page")).toHaveAttribute("data-audience", "owner");
    expect(cellRows()).toEqual([
      ["diary", "unbilledWip"],
      ["debtors", "trustPosition"],
      ["ficaOutstanding", "matterHealth", "activity", "sensitive"],
    ]);
    expect(screen.getByTestId("home-cell-diary")).toHaveClass("lg:col-span-6");
    expect(screen.getByTestId("home-cell-sensitive")).toHaveClass("xl:col-span-3");
    expect(screen.getByTestId("matter-health-panel")).toBeInTheDocument();
  });

  it("no trust account (404 → absent): the trust card drops out and debtors takes the row", async () => {
    mockCaps.mockResolvedValue(OWNER_CAPS);
    mockFetchHome.mockImplementation(async (plan: HomePlan) => ({
      ...dataFor(plan),
      trustPosition: ABSENT,
    }));
    await renderHome();

    expect(screen.queryByTestId("trust-position-widget")).not.toBeInTheDocument();
    expect(cellRows()[1]).toEqual(["debtors"]);
    expect(screen.getByTestId("home-cell-debtors")).toHaveClass("lg:col-span-12");
  });

  it("trust module off: trust is neither fetched nor rendered", async () => {
    mockCaps.mockResolvedValue(ADMIN_CAPS);
    mockSettings.mockResolvedValue({ enabledModules: ["disbursements"] });
    await renderHome();

    expect(plannedWith().trustPosition).toBe(false);
    expect(screen.queryByTestId("trust-position-widget")).not.toBeInTheDocument();
  });

  it("a failed read shows the card's error line rather than dropping the card", async () => {
    mockCaps.mockResolvedValue(ADMIN_CAPS);
    mockFetchHome.mockImplementation(async (plan: HomePlan) => ({
      ...dataFor(plan),
      debtors: { state: "error" as const },
    }));
    await renderHome();

    expect(
      within(screen.getByTestId("debtors-widget")).getByText(/could not be loaded/)
    ).toBeInTheDocument();
  });

  it("while the workspace is provisioning (capabilities 403) shows the setting-up state", async () => {
    mockCaps.mockRejectedValue(new MockApiError(403, "Forbidden"));
    await renderHome();

    expect(screen.getByText("Setting up your workspace")).toBeInTheDocument();
    expect(mockFetchHome).not.toHaveBeenCalled();
  });
});
