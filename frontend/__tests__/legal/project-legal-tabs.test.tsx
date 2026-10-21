import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// --- Mocks (before component imports) ---

vi.mock("swr", () => ({ default: vi.fn() }));

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
  usePathname: () => "/org/acme/projects/proj-1",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/actions/court-dates", () => ({
  fetchCourtDates: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchCourtDate: vi.fn(),
  createCourtDate: vi.fn().mockResolvedValue({ success: true }),
  updateCourtDate: vi.fn().mockResolvedValue({ success: true }),
  postponeCourtDate: vi.fn().mockResolvedValue({ success: true }),
  cancelCourtDate: vi.fn().mockResolvedValue({ success: true }),
  recordOutcome: vi.fn().mockResolvedValue({ success: true }),
  fetchPrescriptionTrackers: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  createPrescriptionTracker: vi.fn().mockResolvedValue({ success: true }),
  interruptPrescription: vi.fn().mockResolvedValue({ success: true }),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchUpcoming: vi.fn().mockResolvedValue({ courtDates: [], prescriptionWarnings: [] }),
}));

vi.mock("@/app/(app)/org/[slug]/legal/adverse-parties/actions", () => ({
  fetchAdverseParties: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchProjectAdverseParties: vi.fn().mockResolvedValue([]),
  unlinkAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchCustomers: vi.fn().mockResolvedValue([]),
  linkAdverseParty: vi.fn().mockResolvedValue({ success: true }),
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

import useSWR from "swr";
import { ProjectCourtDatesTab } from "@/components/legal/project-court-dates-tab";
import { ProjectAdversePartiesTab } from "@/components/legal/project-adverse-parties-tab";
import { UpcomingCourtDatesWidget } from "@/components/legal/upcoming-court-dates-widget";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { NAV_GROUPS } from "@/lib/nav-items";
import { fetchPrescriptionTrackers } from "@/lib/actions/court-dates";
import type { CourtDate, AdversePartyLink, PrescriptionTracker } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeCourtDate(overrides: Partial<CourtDate> = {}): CourtDate {
  return {
    id: "cd-1",
    matterId: "proj-1",
    matterName: "Smith v Jones",
    clientId: "cust-1",
    clientName: "John Smith",
    dateType: "HEARING",
    scheduledDate: "2026-04-15",
    scheduledTime: "09:00",
    courtName: "Johannesburg High Court",
    courtReference: "2026/12345",
    judgeMagistrate: "Judge Mogoeng",
    description: "Motion hearing",
    status: "SCHEDULED",
    outcome: null,
    reminderDays: 7,
    createdBy: "member-1",
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

function makeTracker(overrides: Partial<PrescriptionTracker> = {}): PrescriptionTracker {
  return {
    id: "pt-1",
    matterId: "proj-1",
    matterName: "Smith v Jones",
    clientId: "cust-1",
    clientName: "John Smith",
    causeOfActionDate: "2024-01-15",
    prescriptionType: "GENERAL_3Y",
    customYears: null,
    prescriptionDate: "2027-01-15",
    interruptionDate: null,
    interruptionReason: null,
    status: "RUNNING",
    notes: null,
    createdBy: "member-1",
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

/** Fetchers captured per SWR key, so a test can prove which action a key is wired to. */
const swrFetchers = new Map<string, () => unknown>();

/**
 * The Court dates tab makes two SWR calls (court dates + prescription trackers, 613D): answer
 * each by its key so the prescription list never receives court-date rows.
 */
function mockCourtDatesTabSWR(courtDates: CourtDate[], trackers: PrescriptionTracker[] = []) {
  vi.mocked(useSWR).mockImplementation(((key: string, fetcher: () => unknown) => {
    swrFetchers.set(String(key), fetcher);
    return {
      data: {
        content: String(key).startsWith("project-prescription-trackers-") ? trackers : courtDates,
        page: { totalElements: 0, totalPages: 1, size: 100, number: 0 },
      },
      error: undefined,
      isLoading: false,
      isValidating: false,
      mutate: vi.fn(),
    };
  }) as unknown as typeof useSWR);
}

function makeAdversePartyLink(overrides: Partial<AdversePartyLink> = {}): AdversePartyLink {
  return {
    id: "link-1",
    adversePartyId: "ap-1",
    adversePartyName: "Evil Corp",
    matterId: "proj-1",
    matterName: "Smith v Jones",
    clientId: "cust-1",
    clientName: "John Smith",
    relationship: "OPPOSING_PARTY",
    description: "Primary opposing party",
    createdAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

function withLegalProfile(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile="legal-za"
      enabledModules={["court_calendar", "conflict_check"]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

function withNoModules(ui: React.ReactElement) {
  return (
    <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
      {ui}
    </OrgProfileProvider>
  );
}

// --- Tests ---

describe("ProjectCourtDatesTab", () => {
  it("shows court dates list when court_calendar is enabled", () => {
    mockCourtDatesTabSWR([
      makeCourtDate({ id: "cd-1" }),
      makeCourtDate({ id: "cd-2", scheduledDate: "2026-04-20" }),
    ]);

    render(withLegalProfile(<ProjectCourtDatesTab projectId="proj-1" slug="acme" />));

    const tab = screen.getByTestId("project-court-dates-tab");
    expect(tab).toBeInTheDocument();
    // Court date list should render
    expect(screen.getByTestId("court-date-list")).toBeInTheDocument();
    // Both court dates share the same court name — verify at least one row rendered
    expect(screen.getAllByText("Johannesburg High Court").length).toBeGreaterThanOrEqual(1);
  });

  // Phase 87 613D (Open Question #17): prescription trackers moved here from the deleted
  // court-calendar page's Prescriptions tab.
  it("lists the matter's prescription trackers under Court dates", async () => {
    mockCourtDatesTabSWR([makeCourtDate()], [makeTracker({ id: "pt-1", status: "WARNED" })]);

    render(
      withLegalProfile(<ProjectCourtDatesTab projectId="proj-1" slug="acme" canManageLegal />)
    );

    const list = screen.getByTestId("prescription-tracker-list");
    expect(within(list).getByText("Prescription trackers")).toBeInTheDocument();
    expect(within(list).getByTestId("prescription-tracker-row-pt-1")).toBeInTheDocument();
    expect(within(list).getByText("Warned")).toBeInTheDocument();
    expect(
      within(list).getByRole("button", { name: "New prescription tracker" })
    ).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: "Interrupt" })).toBeInTheDocument();
    // Court-date rows are not fed to the prescription list.
    expect(within(list).queryByText("Johannesburg High Court")).not.toBeInTheDocument();

    // The trackers key is matter-scoped (?matterId=), not the firm-wide list.
    await swrFetchers.get("project-prescription-trackers-proj-1")!();
    expect(fetchPrescriptionTrackers).toHaveBeenCalledWith({ matterId: "proj-1" });
  });

  it("hides prescription writes without MANAGE_LEGAL (fail closed)", () => {
    mockCourtDatesTabSWR([], [makeTracker({ id: "pt-1", status: "RUNNING" })]);

    render(withLegalProfile(<ProjectCourtDatesTab projectId="proj-1" slug="acme" />));

    const list = screen.getByTestId("prescription-tracker-list");
    expect(within(list).getByTestId("prescription-tracker-row-pt-1")).toBeInTheDocument();
    expect(within(list).getByText("Running")).toBeInTheDocument();
    expect(
      within(list).queryByRole("button", { name: "New prescription tracker" })
    ).not.toBeInTheDocument();
    expect(within(list).queryByRole("button", { name: "Interrupt" })).not.toBeInTheDocument();
  });

  it("shows the matter's empty prescription state", () => {
    mockCourtDatesTabSWR([], []);

    render(
      withLegalProfile(<ProjectCourtDatesTab projectId="proj-1" slug="acme" canManageLegal />)
    );

    expect(screen.getByText("No prescription trackers on this matter.")).toBeInTheDocument();
  });

  it("is hidden when court_calendar module is disabled via ModuleGate", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withNoModules(
        <ModuleGate module="court_calendar">
          <ProjectCourtDatesTab projectId="proj-1" slug="acme" />
        </ModuleGate>
      )
    );

    expect(screen.queryByTestId("project-court-dates-tab")).not.toBeInTheDocument();
  });
});

describe("ProjectAdversePartiesTab", () => {
  it("renders linked adverse parties with relationship badges", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: [
        makeAdversePartyLink({ id: "link-1", relationship: "OPPOSING_PARTY" }),
        makeAdversePartyLink({
          id: "link-2",
          adversePartyName: "Witness Corp",
          relationship: "WITNESS",
        }),
      ],
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(withLegalProfile(<ProjectAdversePartiesTab projectId="proj-1" slug="acme" />));

    const tab = screen.getByTestId("project-adverse-parties-tab");
    expect(tab).toBeInTheDocument();
    expect(screen.getByText("Evil Corp")).toBeInTheDocument();
    expect(screen.getByText("Witness Corp")).toBeInTheDocument();
    expect(screen.getByText("Opposing Party")).toBeInTheDocument();
    expect(screen.getByText("Witness")).toBeInTheDocument();
  });
});

describe("UpcomingCourtDatesWidget", () => {
  it("renders upcoming court dates with urgency coloring", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: {
        courtDates: [
          makeCourtDate({
            id: "cd-1",
            scheduledDate: "2026-04-02",
            matterName: "Urgent Matter",
          }),
          makeCourtDate({
            id: "cd-2",
            scheduledDate: "2026-04-15",
            matterName: "Normal Matter",
          }),
        ],
        prescriptionWarnings: [],
      },
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(withLegalProfile(<UpcomingCourtDatesWidget orgSlug="acme" />));

    const widget = screen.getByTestId("upcoming-court-dates-widget");
    expect(widget).toBeInTheDocument();
    expect(screen.getByText("Upcoming Court Dates")).toBeInTheDocument();
    expect(screen.getByText("Urgent Matter")).toBeInTheDocument();
    expect(screen.getByText("Normal Matter")).toBeInTheDocument();
    // View All link
    const link = screen.getByRole("link", { name: /view all/i });
    expect(link).toHaveAttribute("href", "/org/acme/diary?types=COURT_DATE,PRESCRIPTION");
  });
});

describe("Sidebar nav items", () => {
  it("legal nav items have correct requiredModule values", () => {
    // Diary (613D) replaces Court calendar and is ungated — court-date / prescription kinds are
    // gated server-side (X-Diary-Types).
    const todayZone = NAV_GROUPS.find((g) => g.id === "today");
    const diaryItem = todayZone?.items.find((i) => i.label === "Diary");
    expect(diaryItem).toBeDefined();
    expect(diaryItem?.requiredModule).toBeUndefined();

    // Conflict Check
    const mattersZone = NAV_GROUPS.find((g) => g.id === "matters");
    const conflictItem = mattersZone?.items.find((i) => i.label === "Conflict check");
    expect(conflictItem).toBeDefined();
    expect(conflictItem?.requiredModule).toBe("conflict_check");

    // Adverse Parties
    const adverseItem = mattersZone?.items.find((i) => i.label === "Adverse parties");
    expect(adverseItem).toBeDefined();
    expect(adverseItem?.requiredModule).toBe("conflict_check");

    // Tariffs
    const feesZone = NAV_GROUPS.find((g) => g.id === "fees");
    const tariffItem = feesZone?.items.find((i) => i.label === "Tariffs");
    expect(tariffItem).toBeDefined();
    expect(tariffItem?.requiredModule).toBe("lssa_tariff");
  });
});
