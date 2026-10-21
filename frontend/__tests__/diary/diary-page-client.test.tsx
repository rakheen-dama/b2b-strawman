import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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

const { mockPush, mockRefresh } = vi.hoisted(() => ({ mockPush: vi.fn(), mockRefresh: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), refresh: mockRefresh, prefetch: vi.fn() }),
  usePathname: () => "/org/acme/diary",
  useSearchParams: () => new URLSearchParams(),
}));

// The real dialogs import server actions; these tests only need their triggers.
vi.mock("@/components/legal/create-court-date-dialog", () => ({
  CreateCourtDateDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button" data-testid="create-court-date-trigger">
      {triggerLabel}
    </button>
  ),
}));
// 613C: the page client also mounts these (header button, row-action dialogs, Mark done).
vi.mock("@/components/legal/create-prescription-dialog", () => ({
  CreatePrescriptionDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button" data-testid="create-prescription-trigger">
      {triggerLabel}
    </button>
  ),
}));
vi.mock("@/components/legal/postpone-dialog", () => ({ PostponeDialog: () => null }));
vi.mock("@/components/legal/outcome-dialog", () => ({ OutcomeDialog: () => null }));
vi.mock("@/components/legal/cancel-court-date-dialog", () => ({
  CancelCourtDateDialog: () => null,
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/task-actions", () => ({ completeTask: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import {
  DiaryPageClient,
  type DiaryPageClientProps,
} from "@/app/(app)/org/[slug]/diary/diary-page-client";
import {
  parseDiaryKindsParam,
  type DiaryEventResponse,
  type DiaryKind,
} from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const ALL_KINDS: DiaryKind[] = ["TASK_DUE", "MATTER_DUE", "COURT_DATE", "PRESCRIPTION"];
const MEMBERS = [
  { id: "fe-1", name: "Thandi Nkosi", email: "thandi@firm.test" },
  { id: "fe-2", name: "Pieter van Wyk", email: "pieter@firm.test" },
];
const MATTERS = [
  { id: "m-1", name: "Dlamini v Road Accident Fund", referenceNumber: "LIT-001" },
  { id: "m-2", name: "Zulu Estate", referenceNumber: null },
];

function ev(overrides: Partial<DiaryEventResponse>): DiaryEventResponse {
  return {
    id: "x",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Untitled",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Dlamini v Road Accident Fund",
    overdue: false,
    href: "/matters/m-1",
    ...overrides,
  };
}

const EVENTS: DiaryEventResponse[] = [
  ev({
    id: "cd-1",
    kind: "COURT_DATE",
    date: "2026-10-20",
    title: "Durban High Court — Motion",
    feeEarnerId: "fe-1",
    feeEarnerName: "Thandi Nkosi",
  }),
  ev({
    id: "p-1",
    kind: "PRESCRIPTION",
    date: "2026-10-22",
    title: "Prescription — Debt (6 years)",
    feeEarnerId: "fe-2",
    feeEarnerName: "Pieter van Wyk",
  }),
  ev({
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-23",
    title: "Collect medical reports",
    matterId: "m-2",
    matterName: "Zulu Estate",
    feeEarnerId: "fe-2",
    feeEarnerName: "Pieter van Wyk",
  }),
];

function clientProps(overrides: Partial<DiaryPageClientProps> = {}): DiaryPageClientProps {
  return {
    slug: "acme",
    today: "2026-10-21",
    from: "2026-10-19",
    to: "2026-10-25",
    initialView: "week",
    initialKinds: null,
    events: EVENTS,
    typesServed: ALL_KINDS,
    loadError: false,
    members: MEMBERS,
    matters: MATTERS,
    currentMemberId: "fe-1",
    isAdminOrOwner: true,
    canCompleteAnyTask: true,
    canManageLegal: true,
    ...overrides,
  };
}

describe("DiaryPageClient", () => {
  let replaceState: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    // The spy calls through, so reset the jsdom URL a previous test's sync left behind.
    window.history.replaceState(null, "", "/");
    replaceState = vi.spyOn(window.history, "replaceState");
    mockRefresh.mockClear();
    mockPush.mockClear();
  });

  it("canonicalizes a mid-month ?from= to the snapped month window on load (R-3)", () => {
    window.history.replaceState(null, "", "/org/acme/diary?from=2026-10-21&view=month&matter=m-2");
    replaceState.mockClear();
    render(
      <DiaryPageClient
        {...clientProps({
          from: "2026-10-01",
          to: "2026-10-31",
          initialView: "month",
          initialMatterId: "m-2",
        })}
      />
    );
    expect(replaceState).toHaveBeenCalledTimes(1);
    expect(replaceState).toHaveBeenCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-01&view=month&matter=m-2"
    );
    expect(mockPush).not.toHaveBeenCalled();
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("canonicalizes an invalid ?from= to the loaded window on load", () => {
    window.history.replaceState(null, "", "/org/acme/diary?from=not-a-date");
    replaceState.mockClear();
    render(<DiaryPageClient {...clientProps()} />);
    expect(replaceState).toHaveBeenCalledWith(null, "", "/org/acme/diary?from=2026-10-19");
  });

  it("leaves an already-snapped or absent ?from= untouched on load", () => {
    window.history.replaceState(null, "", "/org/acme/diary?from=2026-10-01&view=month");
    replaceState.mockClear();
    render(
      <DiaryPageClient
        {...clientProps({ from: "2026-10-01", to: "2026-10-31", initialView: "month" })}
      />
    );
    expect(replaceState).not.toHaveBeenCalled();
    cleanup();
    window.history.replaceState(null, "", "/org/acme/diary");
    replaceState.mockClear();
    render(<DiaryPageClient {...clientProps()} />);
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("shows every fee earner's events to an owner", () => {
    render(<DiaryPageClient {...clientProps()} />);
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent(
      "All fee earners"
    );
    expect(screen.getByText("Durban High Court — Motion")).toBeInTheDocument();
    expect(screen.getByText("Collect medical reports")).toBeInTheDocument();
  });

  it("preselects Mine for a member and hides other fee earners' events", () => {
    render(<DiaryPageClient {...clientProps({ isAdminOrOwner: false })} />);
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent("Mine");
    expect(screen.getByText("Durban High Court — Motion")).toBeInTheDocument();
    expect(screen.queryByText("Collect medical reports")).toBeNull();
    expect(screen.queryByText("Prescription — Debt (6 years)")).toBeNull();
  });

  it("falls back to all fee earners when the current member is unknown", () => {
    render(<DiaryPageClient {...clientProps({ isAdminOrOwner: false, currentMemberId: null })} />);
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent(
      "All fee earners"
    );
    expect(screen.getByText("Collect medical reports")).toBeInTheDocument();
  });

  it("preselects kind chips from ?types= and shows only those rows", () => {
    render(<DiaryPageClient {...clientProps({ initialKinds: ["PRESCRIPTION"] })} />);
    expect(screen.getByRole("button", { name: "Prescriptions" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    expect(screen.getByRole("button", { name: "Court dates" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );
    expect(screen.getByText("Prescription — Debt (6 years)")).toBeInTheDocument();
    expect(screen.queryByText("Durban High Court — Motion")).toBeNull();
  });

  it("ignores ?types= kinds the server did not serve", () => {
    render(
      <DiaryPageClient
        {...clientProps({
          initialKinds: ["COURT_DATE"],
          typesServed: ["TASK_DUE", "MATTER_DUE"],
          events: EVENTS.filter((e) => e.kind === "TASK_DUE"),
        })}
      />
    );
    expect(screen.getByRole("button", { name: "Tasks" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Collect medical reports")).toBeInTheDocument();
  });

  it("selects no kinds for a present-but-empty ?types= and offers Clear filters", () => {
    render(<DiaryPageClient {...clientProps({ initialKinds: [] })} />);
    for (const name of ["Court dates", "Prescriptions", "Matter due dates", "Tasks"]) {
      expect(screen.getByRole("button", { name })).toHaveAttribute("aria-pressed", "false");
    }
    expect(screen.getByText("No events match these filters.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Clear filters" })).toBeInTheDocument();
  });

  it("round-trips every chip off through types= so a reload reproduces the view", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    for (const name of ["Court dates", "Prescriptions", "Matter due dates", "Tasks"]) {
      await user.click(screen.getByRole("button", { name }));
    }
    const url = replaceState.mock.lastCall?.[2] as string;
    expect(url).toBe("/org/acme/diary?from=2026-10-19&types=");
    cleanup();

    const reloaded = new URL(url, "http://localhost").searchParams.get("types");
    render(<DiaryPageClient {...clientProps({ initialKinds: parseDiaryKindsParam(reloaded) })} />);
    expect(screen.getByText("No events match these filters.")).toBeInTheDocument();
  });

  it("omits types from the URL once the selection equals every served kind", async () => {
    const user = userEvent.setup();
    render(
      <DiaryPageClient
        {...clientProps({
          initialKinds: ["TASK_DUE"],
          typesServed: ["TASK_DUE", "MATTER_DUE"],
          events: EVENTS.filter((e) => e.kind === "TASK_DUE"),
        })}
      />
    );
    await user.click(screen.getByRole("button", { name: "Matter due dates" }));
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "/org/acme/diary?from=2026-10-19");
  });

  it("syncs chip toggles to ?types= without a server round trip", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(screen.getByRole("button", { name: "Tasks" }));
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-19&types=COURT_DATE%2CPRESCRIPTION%2CMATTER_DUE"
    );
    expect(screen.queryByText("Collect medical reports")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Tasks" }));
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "/org/acme/diary?from=2026-10-19");
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  // 613C amendment (B-7, R-1/R-4): List loads the calendar month, so Week → List is a navigation
  // to the month window (router.push), not a client-only switch.
  it("switches to the List view by navigating to the month window", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    expect(screen.getByTestId("diary-week-grid")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "List" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-01&view=list", {
      scroll: false,
    });
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("switches Month to List client-side when the window is the same", async () => {
    const user = userEvent.setup();
    render(
      <DiaryPageClient
        {...clientProps({ from: "2026-10-01", to: "2026-10-31", initialView: "month" })}
      />
    );
    expect(screen.getByTestId("diary-month-grid")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "List" }));
    expect(screen.getByTestId("diary-list")).toBeInTheDocument();
    expect(screen.queryByTestId("diary-month-grid")).toBeNull();
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-01&view=list"
    );
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("opens on the List view when initialView is list", () => {
    render(<DiaryPageClient {...clientProps({ initialView: "list" })} />);
    expect(screen.getByRole("tab", { name: "List" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("diary-list")).toBeInTheDocument();
  });

  it("shows a no-match state with Clear filters that restores every event", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps({ initialKinds: ["MATTER_DUE"] })} />);
    expect(screen.getByText("No events match these filters.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(screen.queryByText("No events match these filters.")).toBeNull();
    expect(screen.getByText("Durban High Court — Motion")).toBeInTheDocument();
    expect(screen.getByText("Collect medical reports")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent(
      "All fee earners"
    );
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "/org/acme/diary?from=2026-10-19");
  });

  // 613C amendment (B-7, R-8): the header's "New court date" shares the trigger testid, so the
  // empty-state action is found by its accessible name.
  it("offers Add a court date on an empty week when court dates are served", () => {
    render(<DiaryPageClient {...clientProps({ events: [] })} />);
    expect(screen.getByText("Nothing in the diary for this week.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add a court date" })).toBeInTheDocument();
  });

  it("hides Add a court date without MANAGE_LEGAL", () => {
    render(<DiaryPageClient {...clientProps({ events: [], canManageLegal: false })} />);
    expect(screen.getByText("Nothing in the diary for this week.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add a court date" })).toBeNull();
  });

  it("words the empty and no-match states for the view's range", () => {
    render(
      <DiaryPageClient
        {...clientProps({ events: [], from: "2026-10-21", to: "2026-10-21", initialView: "day" })}
      />
    );
    expect(screen.getByText("Nothing in the diary for this day.")).toBeInTheDocument();
    cleanup();
    render(
      <DiaryPageClient
        {...clientProps({
          from: "2026-10-01",
          to: "2026-10-31",
          initialView: "list",
          initialKinds: [],
        })}
      />
    );
    expect(
      screen.getByText("Change the filters or clear them to see the whole month.")
    ).toBeInTheDocument();
  });

  it("omits the court date action when court dates are not served", () => {
    render(
      <DiaryPageClient {...clientProps({ events: [], typesServed: ["TASK_DUE", "MATTER_DUE"] })} />
    );
    expect(screen.getByText("Nothing in the diary for this week.")).toBeInTheDocument();
    expect(
      screen.getByText("Matter due dates and task due dates appear here.")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("create-court-date-trigger")).toBeNull();
  });

  it("shows an error state when the diary failed to load", () => {
    render(<DiaryPageClient {...clientProps({ events: [], loadError: true })} />);
    expect(screen.getByText("Unable to load the diary")).toBeInTheDocument();
    expect(screen.queryByTestId("diary-filter-row")).toBeNull();
  });
});
