import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 91 669A: the legal light-pass pages render exactly one h1 through PageHeader with today's
// h1 text, on every branch — including the module-off branches (DA-3), which had no h1 before, and
// the information-request permission branch (its h1 + sentence unchanged).

vi.mock("server-only", () => ({}));

const {
  mockSettings,
  mockCaps,
  mockFetchConflictChecks,
  mockFetchAdverseParties,
  mockFetchDisbursements,
  mockFetchTariffSchedules,
} = vi.hoisted(() => ({
  mockSettings: vi.fn(),
  mockCaps: vi.fn(),
  mockFetchConflictChecks: vi.fn(),
  mockFetchAdverseParties: vi.fn(),
  mockFetchDisbursements: vi.fn(),
  mockFetchTariffSchedules: vi.fn(),
}));

vi.mock("@/lib/api/settings", () => ({ getOrgSettings: mockSettings }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api", () => ({ handleApiError: vi.fn() }));
vi.mock("@/lib/api/information-requests", () => ({ getRequest: vi.fn() }));

vi.mock("@/app/(app)/org/[slug]/conflict-check/actions", () => ({
  fetchConflictChecks: mockFetchConflictChecks,
  fetchCustomers: vi.fn().mockResolvedValue([]),
  fetchProjects: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/app/(app)/org/[slug]/conflict-check/resolve-client", () => ({
  resolveClientDetails: vi.fn().mockResolvedValue(null),
}));
vi.mock("@/app/(app)/org/[slug]/conflict-check/conflict-check-client", () => ({
  ConflictCheckClient: () => <div data-testid="conflict-check-client" />,
}));
vi.mock("@/app/(app)/org/[slug]/legal/adverse-parties/actions", () => ({
  fetchAdverseParties: mockFetchAdverseParties,
}));
vi.mock("@/app/(app)/org/[slug]/legal/adverse-parties/adverse-party-registry-client", () => ({
  AdversePartyRegistryClient: () => <div data-testid="adverse-party-registry" />,
}));
vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  fetchDisbursements: mockFetchDisbursements,
  fetchProjects: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/app/(app)/org/[slug]/legal/disbursements/disbursements-list-client", () => ({
  DisbursementsListClient: () => <div data-testid="disbursements-list-page" />,
}));
vi.mock("@/app/(app)/org/[slug]/legal/tariffs/actions", () => ({
  fetchTariffSchedules: mockFetchTariffSchedules,
}));
vi.mock("@/app/(app)/org/[slug]/legal/tariffs/tariff-browser-client", () => ({
  TariffBrowserClient: () => <div data-testid="tariff-browser" />,
}));
vi.mock("@/components/information-requests/request-detail-client", () => ({
  RequestDetailClient: () => <div data-testid="request-detail-client" />,
}));
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/legal",
  useSearchParams: () => new URLSearchParams(),
}));

import ConflictCheckPage from "@/app/(app)/org/[slug]/conflict-check/page";
import AdversePartiesPage from "@/app/(app)/org/[slug]/legal/adverse-parties/page";
import DisbursementsPage from "@/app/(app)/org/[slug]/legal/disbursements/page";
import TariffsPage from "@/app/(app)/org/[slug]/legal/tariffs/page";
import InformationRequestDetailPage from "@/app/(app)/org/[slug]/information-requests/[id]/page";

const params = Promise.resolve({ slug: "acme" });
const ALL_MODULES = { enabledModules: ["conflict_check", "disbursements", "lssa_tariff"] };
const NO_MODULES = { enabledModules: [] };
const EMPTY_PAGE = { content: [], page: { totalElements: 0, totalPages: 0, size: 20, number: 0 } };

function onlyH1(): HTMLElement {
  const headings = screen.getAllByRole("heading", { level: 1 });
  expect(headings).toHaveLength(1);
  return headings[0];
}

beforeEach(() => {
  vi.clearAllMocks();
  mockFetchConflictChecks.mockResolvedValue(EMPTY_PAGE);
  mockFetchAdverseParties.mockResolvedValue(EMPTY_PAGE);
  mockFetchDisbursements.mockResolvedValue(EMPTY_PAGE);
  mockFetchTariffSchedules.mockResolvedValue([]);
});
afterEach(() => cleanup());

describe("/conflict-check", () => {
  it('renders one h1 "Conflict Check" with its description (K-60)', async () => {
    mockSettings.mockResolvedValue(ALL_MODULES);
    render(await ConflictCheckPage({ params, searchParams: Promise.resolve({}) }));
    expect(onlyH1().textContent).toBe("Conflict Check");
    expect(screen.getByText("Run conflict of interest checks and review history")).toBeVisible();
    expect(screen.getByTestId("conflict-check-client")).toBeInTheDocument();
  });

  it("module off: the same h1 above the unchanged Module Not Available block (DA-3)", async () => {
    mockSettings.mockResolvedValue(NO_MODULES);
    render(await ConflictCheckPage({ params, searchParams: Promise.resolve({}) }));
    expect(onlyH1().textContent).toBe("Conflict Check");
    expect(screen.getByRole("heading", { level: 2, name: "Module Not Available" })).toBeVisible();
    expect(
      screen.getByText("The Conflict Check module is not enabled for your organization.")
    ).toBeVisible();
  });
});

describe("/legal/adverse-parties", () => {
  it('renders one h1 "Adverse Parties" with its description (K-60)', async () => {
    mockSettings.mockResolvedValue(ALL_MODULES);
    render(await AdversePartiesPage({ params }));
    expect(onlyH1().textContent).toBe("Adverse Parties");
    expect(
      screen.getByText("Manage the adverse party registry for conflict checking")
    ).toBeVisible();
  });
});

describe("/legal/disbursements", () => {
  it('renders one h1 "Disbursements" with its description', async () => {
    mockSettings.mockResolvedValue(ALL_MODULES);
    render(await DisbursementsPage({ params }));
    expect(onlyH1().textContent).toBe("Disbursements");
    expect(
      screen.getByText(
        "Manage client disbursements — matter costs paid by the firm on the client's behalf."
      )
    ).toBeVisible();
    expect(screen.getByTestId("disbursements-list-page")).toBeInTheDocument();
  });
});

describe("/legal/tariffs", () => {
  it('renders one h1 "Tariff Schedules" with its description', async () => {
    mockSettings.mockResolvedValue(ALL_MODULES);
    render(await TariffsPage({ params }));
    expect(onlyH1().textContent).toBe("Tariff Schedules");
    expect(screen.getByText("Browse and manage LSSA tariff schedules and items")).toBeVisible();
  });

  it("module off: the same h1 above the unchanged Module Not Available block (DA-3)", async () => {
    mockSettings.mockResolvedValue(NO_MODULES);
    render(await TariffsPage({ params }));
    expect(onlyH1().textContent).toBe("Tariff Schedules");
    expect(screen.getByRole("heading", { level: 2, name: "Module Not Available" })).toBeVisible();
  });
});

describe("/information-requests/[id]", () => {
  it('non-admin: one h1 "Information Request" and the permission sentence', async () => {
    mockCaps.mockResolvedValue({
      capabilities: [],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });
    render(
      await InformationRequestDetailPage({ params: Promise.resolve({ slug: "acme", id: "r1" }) })
    );
    expect(onlyH1().textContent).toBe("Information Request");
    expect(
      screen.getByText("You do not have permission to view information requests.")
    ).toBeVisible();
  });
});
