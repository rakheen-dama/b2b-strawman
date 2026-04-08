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
  usePathname: () => "/org/acme/court-calendar",
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

// --- Imports after mocks ---

import { CourtDateListView } from "@/components/legal/court-date-list-view";
import { CourtCalendarView } from "@/components/legal/court-calendar-view";
import { PrescriptionTab } from "@/components/legal/prescription-tab";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import { CreatePrescriptionDialog } from "@/components/legal/create-prescription-dialog";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { NAV_GROUPS } from "@/lib/nav-items";
import type {
  CourtDate,
  PrescriptionTracker,
} from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeCourtDate(
  overrides: Partial<CourtDate> = {}
): CourtDate {
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

function makeTracker(
  overrides: Partial<PrescriptionTracker> = {}
): PrescriptionTracker {
  return {
    id: "pt-1",
    projectId: "proj-1",
    projectName: "Smith v Jones",
    customerId: "cust-1",
    customerName: "John Smith",
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

// --- Tests ---

describe("CourtDateListView", () => {
  it("renders court dates with correct status badges", () => {
    const courtDates: CourtDate[] = [
      makeCourtDate({ id: "cd-1", status: "SCHEDULED" }),
      makeCourtDate({ id: "cd-2", status: "POSTPONED", scheduledDate: "2026-04-20" }),
      makeCourtDate({ id: "cd-3", status: "HEARD", scheduledDate: "2026-04-10" }),
      makeCourtDate({ id: "cd-4", status: "CANCELLED", scheduledDate: "2026-04-05" }),
    ];

    render(
      <CourtDateListView
        courtDates={courtDates}
        onEdit={vi.fn()}
        onPostpone={vi.fn()}
        onCancel={vi.fn()}
        onRecordOutcome={vi.fn()}
        onSelect={vi.fn()}
      />
    );

    const list = screen.getByTestId("court-date-list");
    expect(list).toBeInTheDocument();
    expect(screen.getByText("Scheduled")).toBeInTheDocument();
    expect(screen.getByText("Postponed")).toBeInTheDocument();
    expect(screen.getByText("Heard")).toBeInTheDocument();
    expect(screen.getByText("Cancelled")).toBeInTheDocument();
  });

  it("shows empty state when no court dates", () => {
    render(
      <CourtDateListView
        courtDates={[]}
        onEdit={vi.fn()}
        onPostpone={vi.fn()}
        onCancel={vi.fn()}
        onRecordOutcome={vi.fn()}
        onSelect={vi.fn()}
      />
    );

    expect(
      screen.getByText("No court dates found for this period.")
    ).toBeInTheDocument();
  });
});

describe("CourtCalendarView", () => {
  it("renders month grid with weekday headers", () => {
    render(
      <CourtCalendarView
        courtDates={[makeCourtDate({ scheduledDate: "2026-04-15" })]}
        year={2026}
        month={4}
      />
    );

    const calendar = screen.getByTestId("court-calendar-view");
    expect(calendar).toBeInTheDocument();
    expect(screen.getByText("Sun")).toBeInTheDocument();
    expect(screen.getByText("Mon")).toBeInTheDocument();
    expect(screen.getByText("Sat")).toBeInTheDocument();
    // Day 15 should have a status dot
    expect(screen.getByText("15")).toBeInTheDocument();
  });
});

describe("PrescriptionTab", () => {
  it("renders tracker list with status badges and days remaining", () => {
    const trackers: PrescriptionTracker[] = [
      makeTracker({ id: "pt-1", status: "RUNNING" }),
      makeTracker({
        id: "pt-2",
        status: "WARNED",
        prescriptionDate: "2026-06-01",
      }),
      makeTracker({ id: "pt-3", status: "EXPIRED" }),
    ];

    render(
      <PrescriptionTab trackers={trackers} slug="acme" onRefresh={vi.fn()} />
    );

    const tab = screen.getByTestId("prescription-tab");
    expect(tab).toBeInTheDocument();
    expect(screen.getByText("Running")).toBeInTheDocument();
    expect(screen.getByText("Warned")).toBeInTheDocument();
    expect(screen.getByText("Expired")).toBeInTheDocument();
    // "Add Tracker" button
    expect(screen.getByText("Add Tracker")).toBeInTheDocument();
  });

  it("shows empty state when no trackers", () => {
    render(
      <PrescriptionTab trackers={[]} slug="acme" onRefresh={vi.fn()} />
    );

    expect(
      screen.getByText("No prescription trackers found.")
    ).toBeInTheDocument();
  });
});

describe("CreateCourtDateDialog", () => {
  it("renders form fields when opened", async () => {
    const user = userEvent.setup();

    render(<CreateCourtDateDialog slug="acme" />);

    await user.click(screen.getByTestId("create-court-date-trigger"));

    const dialog = screen.getByTestId("create-court-date-dialog");
    expect(dialog).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Schedule Court Date" })
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Matter")).toBeInTheDocument();
    expect(screen.getByLabelText("Type")).toBeInTheDocument();
    expect(screen.getByLabelText("Date")).toBeInTheDocument();
    expect(screen.getByLabelText("Court Name")).toBeInTheDocument();
  });

  it("populates matter dropdown with projects", async () => {
    const { fetchProjects } = await import(
      "@/app/(app)/org/[slug]/court-calendar/actions"
    );
    vi.mocked(fetchProjects).mockResolvedValueOnce([
      { id: "p-1", name: "Smith v Jones" },
      { id: "p-2", name: "Doe v City" },
    ]);

    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);

    await user.click(screen.getByTestId("create-court-date-trigger"));

    // Wait for projects to load
    const matterSelect = screen.getByLabelText("Matter");
    await vi.waitFor(() => {
      expect(matterSelect).not.toBeDisabled();
    });

    const options = matterSelect.querySelectorAll("option");
    expect(options.length).toBe(3); // placeholder + 2 projects
    expect(options[1].textContent).toBe("Smith v Jones");
    expect(options[2].textContent).toBe("Doe v City");
  });

  it("shows error message when fetchProjects fails", async () => {
    const { fetchProjects } = await import(
      "@/app/(app)/org/[slug]/court-calendar/actions"
    );
    vi.mocked(fetchProjects).mockRejectedValueOnce(new Error("Network error"));

    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);

    await user.click(screen.getByTestId("create-court-date-trigger"));

    await vi.waitFor(() => {
      expect(
        screen.getByText("Failed to load matters. Please try again.")
      ).toBeInTheDocument();
    });
  });
});

describe("CreatePrescriptionDialog", () => {
  it("shows customYears field only for CUSTOM type", async () => {
    const user = userEvent.setup();

    render(<CreatePrescriptionDialog slug="acme" />);

    await user.click(screen.getByTestId("create-prescription-trigger"));

    const dialog = screen.getByTestId("create-prescription-dialog");
    expect(dialog).toBeInTheDocument();

    // Custom years should not be visible initially (GENERAL_3Y is default)
    expect(screen.queryByTestId("custom-years-field")).not.toBeInTheDocument();

    // Change to CUSTOM
    const typeSelect = screen.getByLabelText("Prescription Type");
    await user.selectOptions(typeSelect, "CUSTOM");

    // Custom years field should now appear
    expect(screen.getByTestId("custom-years-field")).toBeInTheDocument();
  });
});

describe("Nav items", () => {
  it("court-calendar nav item has requiredModule and no requiredCapability", () => {
    const workGroup = NAV_GROUPS.find((g) => g.id === "work");
    const courtCalItem = workGroup?.items.find(
      (i) => i.label === "Court Calendar"
    );
    expect(courtCalItem).toBeDefined();
    expect(courtCalItem?.requiredModule).toBe("court_calendar");
    expect(courtCalItem?.requiredCapability).toBeUndefined();
  });

  it("court-calendar is hidden when module is disabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <ModuleGate module="court_calendar">
          <span>Court Calendar Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(
      screen.queryByText("Court Calendar Content")
    ).not.toBeInTheDocument();
  });

  it("court-calendar is visible when module is enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["court_calendar"]}
        terminologyNamespace={null}
      >
        <ModuleGate module="court_calendar">
          <span>Court Calendar Content</span>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.getByText("Court Calendar Content")).toBeInTheDocument();
  });
});

describe("Court date detail actions", () => {
  it("shows correct action buttons based on SCHEDULED status", () => {
    const courtDates = [makeCourtDate({ status: "SCHEDULED" })];

    const onPostpone = vi.fn();
    const onCancel = vi.fn();
    const onOutcome = vi.fn();

    render(
      <CourtDateListView
        courtDates={courtDates}
        onPostpone={onPostpone}
        onCancel={onCancel}
        onRecordOutcome={onOutcome}
        onSelect={vi.fn()}
      />
    );

    // SCHEDULED should have an actions dropdown
    const actionsButton = screen.getByRole("button", { name: "Actions" });
    expect(actionsButton).toBeInTheDocument();
  });

  it("hides action buttons for HEARD status", () => {
    const courtDates = [makeCourtDate({ status: "HEARD" })];

    render(
      <CourtDateListView
        courtDates={courtDates}
        onEdit={vi.fn()}
        onPostpone={vi.fn()}
        onCancel={vi.fn()}
        onRecordOutcome={vi.fn()}
        onSelect={vi.fn()}
      />
    );

    // HEARD status should have no actions dropdown
    expect(
      screen.queryByRole("button", { name: "Actions" })
    ).not.toBeInTheDocument();
  });
});
