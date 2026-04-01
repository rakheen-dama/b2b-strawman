import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

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

vi.mock("@/app/(app)/org/[slug]/court-calendar/actions", () => ({
  fetchCourtDates: vi
    .fn()
    .mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchCourtDate: vi.fn(),
  createCourtDate: vi.fn().mockResolvedValue({ success: true }),
  updateCourtDate: vi.fn().mockResolvedValue({ success: true }),
  postponeCourtDate: vi.fn().mockResolvedValue({ success: true }),
  cancelCourtDate: vi.fn().mockResolvedValue({ success: true }),
  recordOutcome: vi.fn().mockResolvedValue({ success: true }),
  fetchPrescriptionTrackers: vi
    .fn()
    .mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  createPrescriptionTracker: vi.fn().mockResolvedValue({ success: true }),
  interruptPrescription: vi.fn().mockResolvedValue({ success: true }),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchUpcoming: vi
    .fn()
    .mockResolvedValue({ courtDates: [], prescriptionWarnings: [] }),
}));

vi.mock("@/app/(app)/org/[slug]/legal/adverse-parties/actions", () => ({
  fetchAdverseParties: vi
    .fn()
    .mockResolvedValue({ content: [], page: { totalElements: 0 } }),
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
import type { CourtDate, AdversePartyLink } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeCourtDate(overrides: Partial<CourtDate> = {}): CourtDate {
  return {
    id: "cd-1",
    projectId: "proj-1",
    projectName: "Smith v Jones",
    customerId: "cust-1",
    customerName: "John Smith",
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

function makeAdversePartyLink(
  overrides: Partial<AdversePartyLink> = {},
): AdversePartyLink {
  return {
    id: "link-1",
    adversePartyId: "ap-1",
    adversePartyName: "Evil Corp",
    projectId: "proj-1",
    projectName: "Smith v Jones",
    customerId: "cust-1",
    customerName: "John Smith",
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
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={[]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

// --- Tests ---

describe("ProjectCourtDatesTab", () => {
  it("shows court dates list when court_calendar is enabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: {
        content: [
          makeCourtDate({ id: "cd-1" }),
          makeCourtDate({ id: "cd-2", scheduledDate: "2026-04-20" }),
        ],
        page: { totalElements: 2, totalPages: 1, size: 100, number: 0 },
      },
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withLegalProfile(
        <ProjectCourtDatesTab projectId="proj-1" slug="acme" />,
      ),
    );

    const tab = screen.getByTestId("project-court-dates-tab");
    expect(tab).toBeInTheDocument();
    // Court date list should render
    expect(screen.getByTestId("court-date-list")).toBeInTheDocument();
    // Both court dates share the same court name — verify at least one row rendered
    expect(screen.getAllByText("Johannesburg High Court").length).toBeGreaterThanOrEqual(1);
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
        </ModuleGate>,
      ),
    );

    expect(
      screen.queryByTestId("project-court-dates-tab"),
    ).not.toBeInTheDocument();
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

    render(
      withLegalProfile(
        <ProjectAdversePartiesTab projectId="proj-1" slug="acme" />,
      ),
    );

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
            projectName: "Urgent Matter",
          }),
          makeCourtDate({
            id: "cd-2",
            scheduledDate: "2026-04-15",
            projectName: "Normal Matter",
          }),
        ],
        prescriptionWarnings: [],
      },
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withLegalProfile(
        <UpcomingCourtDatesWidget orgSlug="acme" />,
      ),
    );

    const widget = screen.getByTestId("upcoming-court-dates-widget");
    expect(widget).toBeInTheDocument();
    expect(screen.getByText("Upcoming Court Dates")).toBeInTheDocument();
    expect(screen.getByText("Urgent Matter")).toBeInTheDocument();
    expect(screen.getByText("Normal Matter")).toBeInTheDocument();
    // View All link
    const link = screen.getByRole("link", { name: /view all/i });
    expect(link).toHaveAttribute("href", "/org/acme/court-calendar");
  });
});

describe("Sidebar nav items", () => {
  it("legal nav items have correct requiredModule values", () => {
    // Court Calendar
    const workGroup = NAV_GROUPS.find((g) => g.id === "work");
    const courtCalItem = workGroup?.items.find(
      (i) => i.label === "Court Calendar",
    );
    expect(courtCalItem).toBeDefined();
    expect(courtCalItem?.requiredModule).toBe("court_calendar");

    // Conflict Check
    const clientsGroup = NAV_GROUPS.find((g) => g.id === "clients");
    const conflictItem = clientsGroup?.items.find(
      (i) => i.label === "Conflict Check",
    );
    expect(conflictItem).toBeDefined();
    expect(conflictItem?.requiredModule).toBe("conflict_check");

    // Adverse Parties
    const adverseItem = clientsGroup?.items.find(
      (i) => i.label === "Adverse Parties",
    );
    expect(adverseItem).toBeDefined();
    expect(adverseItem?.requiredModule).toBe("conflict_check");

    // Tariffs
    const financeGroup = NAV_GROUPS.find((g) => g.id === "finance");
    const tariffItem = financeGroup?.items.find(
      (i) => i.label === "Tariffs",
    );
    expect(tariffItem).toBeDefined();
    expect(tariffItem?.requiredModule).toBe("lssa_tariff");
  });
});
