import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
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
  usePathname: () => "/org/acme/diary",
}));

vi.mock("@/lib/actions/court-dates", () => ({
  fetchCourtDates: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
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
import { PrescriptionTrackerList } from "@/components/legal/prescription-tracker-list";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import { CreatePrescriptionDialog } from "@/components/legal/create-prescription-dialog";
import { ProjectCourtDatesTab } from "@/components/legal/project-court-dates-tab";
import {
  createPrescriptionTracker,
  fetchProjects,
  interruptPrescription,
} from "@/lib/actions/court-dates";
import type { CourtDate, PrescriptionTracker } from "@/lib/types";
import { inZone } from "@/__tests__/helpers/time-zone";

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
    // Phase 91 668A: the shared CourtDateStatusBadge — visible label (e2e filters rows by it) plus
    // a decorative icon, so colour is never the only signal.
    for (const label of ["Scheduled", "Postponed", "Heard", "Cancelled"]) {
      const badge = within(list).getByText(label);
      expect(badge).toHaveAttribute("data-slot", "badge");
      expect(badge.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
    }
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

    expect(screen.getByText("No court dates found for this period.")).toBeInTheDocument();
  });
});

// Phase 87 613D: the court calendar's Prescriptions tab became the matter-scoped
// PrescriptionTrackerList mounted in the matter's Court dates tab.
describe("PrescriptionTrackerList", () => {
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
      <PrescriptionTrackerList
        trackers={trackers}
        slug="acme"
        matterId="m-1"
        onRefresh={vi.fn()}
        canManageLegal
      />
    );

    const list = screen.getByTestId("prescription-tracker-list");
    expect(list).toBeInTheDocument();
    expect(screen.getByText("Running")).toBeInTheDocument();
    expect(screen.getByText("Warned")).toBeInTheDocument();
    expect(screen.getByText("Expired")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New prescription tracker" })).toBeInTheDocument();
  });

  it("shows empty state when no trackers", () => {
    render(
      <PrescriptionTrackerList trackers={[]} slug="acme" matterId="m-1" onRefresh={vi.fn()} />
    );

    expect(screen.getByText("No prescription trackers on this matter.")).toBeInTheDocument();
  });

  // PR #1624 CodeRabbit: from a matter's Court dates tab a new tracker must land on that matter —
  // a firm-wide picker let it be created elsewhere and "disappear" from this matter-scoped list.
  it("creates a tracker on this matter only: the matter selector is locked to it", async () => {
    const MATTER_ID = "11111111-1111-4111-8111-111111111111";
    vi.mocked(fetchProjects).mockResolvedValueOnce([
      { id: MATTER_ID, name: "Ndlovu v RAF" },
      { id: "22222222-2222-4222-8222-222222222222", name: "Other matter" },
    ]);
    const user = userEvent.setup();
    render(<ProjectCourtDatesTab projectId={MATTER_ID} slug="acme" canManageLegal />);

    await user.click(await screen.findByRole("button", { name: "New prescription tracker" }));
    const matterSelect = screen.getByLabelText("Matter");
    await screen.findByRole("option", { name: "Ndlovu v RAF" });
    expect(matterSelect).toBeDisabled();
    expect(matterSelect).toHaveValue(MATTER_ID);

    fireEvent.change(screen.getByLabelText("Cause of Action Date"), {
      target: { value: "2026-01-15" },
    });
    await user.click(screen.getByRole("button", { name: "Create Tracker" }));

    await vi.waitFor(() =>
      expect(createPrescriptionTracker).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({ matterId: MATTER_ID, causeOfActionDate: "2026-01-15" })
      )
    );
  });
});

describe("PrescriptionTrackerList days left on the South African calendar", () => {
  // Today in South Africa is Fri 27 Nov 2026 at each of these instants.
  const instants = [
    ["01:00 SAST", "2026-11-26T23:00:00Z"],
    ["11:40 SAST", "2026-11-27T09:40:00Z"],
    ["23:30 SAST", "2026-11-27T21:30:00Z"],
  ] as const;
  // The server renders in UTC; a browser may run in any zone. The count must not depend on it.
  const zones = ["UTC", "Africa/Johannesburg", "America/New_York"];
  // Statuses as the backend sends them (PrescriptionTrackerService.computeEffectiveStatus): the
  // stored date is the last day to serve, so it is still live (WARNED within 90 days, otherwise
  // RUNNING) on the day itself, and EXPIRED only from the day after.

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  describe.each(zones)("runtime zone %s", (zone) => {
    it.each(instants)(
      "at %s, yesterday (EXPIRED) reads —, today (the last day to serve) reads Today, tomorrow is 1, +2 is 2",
      (_label, instant) => {
        inZone(zone);
        vi.useFakeTimers({ toFake: ["Date"] });
        vi.setSystemTime(new Date(instant));
        const dates = [
          ["pt-y", "2026-11-26", "EXPIRED"],
          ["pt-t", "2026-11-27", "WARNED"],
          ["pt-1", "2026-11-28", "WARNED"],
          ["pt-2", "2026-11-29", "WARNED"],
        ] as const;
        render(
          <PrescriptionTrackerList
            trackers={dates.map(([id, prescriptionDate, status]) =>
              makeTracker({ id, prescriptionDate, status })
            )}
            slug="acme"
            matterId="m-1"
            onRefresh={vi.fn()}
          />
        );
        const daysLeft = dates.map(([id, prescriptionDate]) => {
          const cells = within(screen.getByTestId(`prescription-tracker-row-${id}`)).getAllByRole(
            "cell"
          );
          const span = cells[4].querySelector("span")!;
          return [prescriptionDate, span.textContent, span.classList.contains("text-red-600")];
        });
        expect(daysLeft).toEqual([
          ["2026-11-26", "—", true],
          ["2026-11-27", "Today", true],
          ["2026-11-28", "1", true],
          ["2026-11-29", "2", true],
        ]);
      }
    );
  });

  it("colours the count from the SA-calendar value at the 30- and 90-day thresholds", () => {
    inZone("UTC");
    vi.useFakeTimers({ toFake: ["Date"] });
    // 01:00 SAST on 27 Nov: the old count read one day more than remains.
    vi.setSystemTime(new Date("2026-11-26T23:00:00Z"));
    const dates = [
      ["pt-30", "2026-12-27", "WARNED"], // 30 days left: red
      ["pt-31", "2026-12-28", "WARNED"], // 31: amber
      ["pt-90", "2027-02-25", "WARNED"], // 90: amber
      ["pt-91", "2027-02-26", "RUNNING"], // 91: slate
    ] as const;
    render(
      <PrescriptionTrackerList
        trackers={dates.map(([id, prescriptionDate, status]) =>
          makeTracker({ id, prescriptionDate, status })
        )}
        slug="acme"
        matterId="m-1"
        onRefresh={vi.fn()}
      />
    );
    const shown = dates.map(([id]) => {
      const span = within(screen.getByTestId(`prescription-tracker-row-${id}`))
        .getAllByRole("cell")[4]
        .querySelector("span")!;
      const colour = ["text-red-600", "text-amber-600", "text-slate-600"].find((c) =>
        span.classList.contains(c)
      );
      return [span.textContent, colour];
    });
    expect(shown).toEqual([
      ["30", "text-red-600"],
      ["31", "text-amber-600"],
      ["90", "text-amber-600"],
      ["91", "text-slate-600"],
    ]);
  });
  // "Today" follows the backend status: a day-0 tracker is live (its last day to serve), so it says
  // Today; one the backend reports EXPIRED (a historic tracker storing the anniversary) or
  // INTERRUPTED reads "—".
  it.each([
    ["WARNED", "Today"],
    ["RUNNING", "Today"],
    ["EXPIRED", "—"],
    ["INTERRUPTED", "—"],
  ] as const)("a day-0 %s tracker reads %s", (status, label) => {
    inZone("UTC");
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-26T23:00:00Z")); // 01:00 SAST, Fri 27 Nov
    render(
      <PrescriptionTrackerList
        trackers={[makeTracker({ id: "pt-0", prescriptionDate: "2026-11-27", status })]}
        slug="acme"
        matterId="m-1"
        onRefresh={vi.fn()}
      />
    );
    const span = within(screen.getByTestId("prescription-tracker-row-pt-0"))
      .getAllByRole("cell")[4]
      .querySelector("span")!;
    expect([span.textContent, span.classList.contains("text-red-600")]).toEqual([label, true]);
  });
});

// Founder ruling 2026-11-28 (tasks/legal/prescription-computation-2026-11-28.md): the stored date
// is the last day to serve, and Interrupt stays available once Expired, judged by the date entered.
describe("PrescriptionTrackerList last day to serve and Interrupt", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  function renderList(trackers: PrescriptionTracker[]) {
    return render(
      <PrescriptionTrackerList
        trackers={trackers}
        slug="acme"
        matterId="m-1"
        onRefresh={vi.fn()}
        canManageLegal
      />
    );
  }

  it("labels the date column Last day to serve", () => {
    renderList([makeTracker({ id: "pt-1", prescriptionDate: "2027-03-14" })]);

    const headers = screen.getAllByRole("columnheader").map((h) => h.textContent);
    expect(headers).toContain("Last day to serve");
    expect(headers).not.toContain("Prescription date");
    expect(
      within(screen.getByTestId("prescription-tracker-row-pt-1")).getByText("2027-03-14")
    ).toBeInTheDocument();
    // The calculated-aid disclaimer sits beside the heading.
    expect(screen.getByRole("button", { name: "Help: Last day to serve" })).toBeInTheDocument();
  });

  it("offers Interrupt on RUNNING, WARNED and EXPIRED trackers, not on INTERRUPTED ones", () => {
    renderList([
      makeTracker({ id: "pt-r", status: "RUNNING" }),
      makeTracker({ id: "pt-w", status: "WARNED" }),
      makeTracker({ id: "pt-e", status: "EXPIRED", prescriptionDate: "2026-11-01" }),
      makeTracker({ id: "pt-i", status: "INTERRUPTED" }),
    ]);

    const interruptIn = (id: string) =>
      within(screen.getByTestId(`prescription-tracker-row-${id}`)).queryByRole("button", {
        name: "Interrupt",
      });
    expect(interruptIn("pt-r")).toBeInTheDocument();
    expect(interruptIn("pt-w")).toBeInTheDocument();
    expect(interruptIn("pt-e")).toBeInTheDocument();
    expect(interruptIn("pt-i")).not.toBeInTheDocument();
  });

  describe("Interrupt dialog date validation (today in SA: Fri 27 Nov 2026)", () => {
    beforeEach(() => {
      inZone("UTC");
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date("2026-11-27T09:40:00Z")); // 11:40 SAST
    });

    async function openDialogFor(prescriptionDate: string) {
      const user = userEvent.setup();
      renderList([makeTracker({ id: "pt-e", status: "EXPIRED", prescriptionDate })]);
      await user.click(
        within(screen.getByTestId("prescription-tracker-row-pt-e")).getByRole("button", {
          name: "Interrupt",
        })
      );
      const dialog = screen.getByTestId("interrupt-dialog");
      fireEvent.change(within(dialog).getByLabelText("Reason"), {
        target: { value: "Summons served" },
      });
      const submitWith = async (date: string) => {
        fireEvent.change(within(dialog).getByLabelText("Interruption Date"), {
          target: { value: date },
        });
        await user.click(within(dialog).getByRole("button", { name: "Record Interruption" }));
      };
      return { dialog, submitWith };
    }

    it("rejects a date after the last day to serve, inline, without calling the backend", async () => {
      const { dialog, submitWith } = await openDialogFor("2026-11-20");

      await submitWith("2026-11-21");

      expect(
        await within(dialog).findByText(
          "The interruption date is after the last day to serve (2026-11-20). The claim had already prescribed, so it cannot be interrupted."
        )
      ).toBeInTheDocument();
      expect(interruptPrescription).not.toHaveBeenCalled();
    });

    it("rejects a date in the future, inline", async () => {
      const { dialog, submitWith } = await openDialogFor("2027-03-14");

      await submitWith("2026-11-28");

      expect(
        await within(dialog).findByText("The interruption date cannot be in the future.")
      ).toBeInTheDocument();
      expect(interruptPrescription).not.toHaveBeenCalled();
    });

    it("records an interruption served on the last day to serve of an EXPIRED tracker", async () => {
      const { submitWith } = await openDialogFor("2026-11-20");

      await submitWith("2026-11-20");

      await vi.waitFor(() =>
        expect(interruptPrescription).toHaveBeenCalledWith("acme", "pt-e", {
          interruptionDate: "2026-11-20",
          interruptionReason: "Summons served",
        })
      );
    });
  });
});

describe("CreateCourtDateDialog", () => {
  it("renders form fields when opened", async () => {
    const user = userEvent.setup();

    render(<CreateCourtDateDialog slug="acme" />);

    await user.click(screen.getByTestId("create-court-date-trigger"));

    const dialog = screen.getByTestId("create-court-date-dialog");
    expect(dialog).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Schedule Court Date" })).toBeInTheDocument();
    expect(screen.getByLabelText("Matter")).toBeInTheDocument();
    expect(screen.getByLabelText("Type")).toBeInTheDocument();
    expect(screen.getByLabelText("Date")).toBeInTheDocument();
    expect(screen.getByLabelText("Court Name")).toBeInTheDocument();
  });

  it("populates matter dropdown with projects", async () => {
    const { fetchProjects } = await import("@/lib/actions/court-dates");
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
    const { fetchProjects } = await import("@/lib/actions/court-dates");
    vi.mocked(fetchProjects).mockRejectedValueOnce(new Error("Network error"));

    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);

    await user.click(screen.getByTestId("create-court-date-trigger"));

    await vi.waitFor(() => {
      expect(screen.getByText("Failed to load matters. Please try again.")).toBeInTheDocument();
    });
  });
});

describe("CreateCourtDateDialog focus return (613C)", () => {
  // The dialog owns a plain Button (no DialogTrigger), so it must announce the popup and restore
  // focus to that button itself when the dialog closes.
  it("announces the dialog popup and its open state on the trigger", async () => {
    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);
    const trigger = screen.getByRole("button", { name: "New Court Date" });
    expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    await user.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("returns focus to the trigger when closed with Escape", async () => {
    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);
    const trigger = screen.getByTestId("create-court-date-trigger");
    await user.click(trigger);
    expect(screen.getByTestId("create-court-date-dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await vi.waitFor(() => expect(screen.queryByTestId("create-court-date-dialog")).toBeNull());
    expect(trigger).toHaveFocus();
  });

  it("returns focus to the trigger when closed with Cancel", async () => {
    const user = userEvent.setup();
    render(<CreateCourtDateDialog slug="acme" />);
    const trigger = screen.getByTestId("create-court-date-trigger");
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await vi.waitFor(() => expect(screen.queryByTestId("create-court-date-dialog")).toBeNull());
    expect(trigger).toHaveFocus();
  });
});

describe("CreatePrescriptionDialog focus return (613C)", () => {
  it("returns focus to the trigger when closed with Cancel", async () => {
    const user = userEvent.setup();
    render(<CreatePrescriptionDialog slug="acme" />);
    const trigger = screen.getByTestId("create-prescription-trigger");
    expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await vi.waitFor(() => expect(screen.queryByTestId("create-prescription-dialog")).toBeNull());
    expect(trigger).toHaveFocus();
  });
});

describe("CreatePrescriptionDialog", () => {
  it("shows customYears field only for CUSTOM type", async () => {
    const user = userEvent.setup();

    render(<CreatePrescriptionDialog slug="acme" />);

    await user.click(screen.getByTestId("create-prescription-trigger"));

    const dialog = screen.getByTestId("create-prescription-dialog");
    expect(dialog).toBeInTheDocument();
    // No fixed matter (the Diary header): the firm-wide matter picker stays open.
    await vi.waitFor(() => expect(screen.getByLabelText("Matter")).toBeEnabled());
    expect(screen.getByLabelText("Matter")).toHaveValue("");

    // Custom years should not be visible initially (GENERAL_3Y is default)
    expect(screen.queryByTestId("custom-years-field")).not.toBeInTheDocument();

    // Change to CUSTOM
    const typeSelect = screen.getByLabelText("Prescription Type");
    await user.selectOptions(typeSelect, "CUSTOM");

    // Custom years field should now appear
    expect(screen.getByTestId("custom-years-field")).toBeInTheDocument();
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
        onEdit={vi.fn()}
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
    expect(screen.queryByRole("button", { name: "Actions" })).not.toBeInTheDocument();
  });
});
