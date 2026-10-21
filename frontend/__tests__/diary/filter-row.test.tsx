import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
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

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: mockRefresh, prefetch: vi.fn() }),
  usePathname: () => "/org/acme/diary",
  useSearchParams: () => new URLSearchParams(),
}));

// The real dialog imports server actions; the empty state only needs its trigger.
vi.mock("@/components/legal/create-court-date-dialog", () => ({
  CreateCourtDateDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button" data-testid="create-court-date-trigger">
      {triggerLabel}
    </button>
  ),
}));

import { DiaryFilterRow } from "@/components/diary/diary-filter-row";
import {
  DiaryPageClient,
  type DiaryPageClientProps,
} from "@/app/(app)/org/[slug]/diary/diary-page-client";
import type { DiaryEventResponse, DiaryKind } from "@/app/(app)/org/[slug]/diary/diary-types";

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

// ---- DiaryFilterRow ----

function filterProps(overrides: Partial<React.ComponentProps<typeof DiaryFilterRow>> = {}) {
  return {
    typesServed: ALL_KINDS,
    kinds: ALL_KINDS,
    onKindsChange: vi.fn(),
    feeEarner: "all",
    onFeeEarnerChange: vi.fn(),
    members: MEMBERS,
    showMine: true,
    matterId: null,
    onMatterChange: vi.fn(),
    matters: MATTERS,
    ...overrides,
  };
}

describe("DiaryFilterRow", () => {
  it("renders a chip per served kind in display order", () => {
    render(<DiaryFilterRow {...filterProps()} />);
    const group = screen.getByRole("group", { name: "Event kinds" });
    expect(
      within(group)
        .getAllByRole("button")
        .map((b) => b.textContent)
    ).toEqual(["Court dates", "Prescriptions", "Matter due dates", "Tasks"]);
  });

  it("hides court chips when the server did not serve them", () => {
    render(<DiaryFilterRow {...filterProps({ typesServed: ["TASK_DUE", "MATTER_DUE"] })} />);
    expect(screen.queryByRole("button", { name: "Court dates" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Prescriptions" })).toBeNull();
    expect(screen.getByRole("button", { name: "Tasks" })).toBeInTheDocument();
  });

  it("hides the chip group entirely when no kinds were served", () => {
    render(<DiaryFilterRow {...filterProps({ typesServed: [], kinds: [] })} />);
    expect(screen.queryByRole("group", { name: "Event kinds" })).toBeNull();
  });

  it("toggles a chip off via onKindsChange and reflects state with aria-pressed", async () => {
    const user = userEvent.setup();
    const props = filterProps({ kinds: ["COURT_DATE", "PRESCRIPTION", "TASK_DUE"] });
    render(<DiaryFilterRow {...props} />);
    expect(screen.getByRole("button", { name: "Court dates" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    expect(screen.getByRole("button", { name: "Matter due dates" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );
    await user.click(screen.getByRole("button", { name: "Court dates" }));
    expect(props.onKindsChange).toHaveBeenCalledWith(["PRESCRIPTION", "TASK_DUE"]);
    await user.click(screen.getByRole("button", { name: "Matter due dates" }));
    expect(props.onKindsChange).toHaveBeenLastCalledWith([
      "COURT_DATE",
      "PRESCRIPTION",
      "MATTER_DUE",
      "TASK_DUE",
    ]);
  });

  it("offers Mine in the fee-earner select only when the current member is known", async () => {
    const user = userEvent.setup();
    render(<DiaryFilterRow {...filterProps({ showMine: false })} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    expect(await screen.findByRole("option", { name: "All fee earners" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Mine" })).toBeNull();
    expect(screen.getByRole("option", { name: "Pieter van Wyk" })).toBeInTheDocument();
  });

  it("selects a fee earner", async () => {
    const user = userEvent.setup();
    const props = filterProps();
    render(<DiaryFilterRow {...props} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    expect(await screen.findByRole("option", { name: "Mine" })).toBeInTheDocument();
    await user.click(screen.getByRole("option", { name: "Thandi Nkosi" }));
    expect(props.onFeeEarnerChange).toHaveBeenCalledWith("fe-1");
  });

  it("selects a matter from the searchable combobox", async () => {
    const user = userEvent.setup();
    const props = filterProps();
    render(<DiaryFilterRow {...props} />);
    const trigger = screen.getByRole("combobox", { name: "Matter" });
    expect(trigger).toHaveTextContent("All matters");
    await user.click(trigger);
    await user.click(await screen.findByText("Dlamini v Road Accident Fund"));
    expect(props.onMatterChange).toHaveBeenCalledWith("m-1");
  });

  it("shows the selected matter name on the trigger", () => {
    render(<DiaryFilterRow {...filterProps({ matterId: "m-2" })} />);
    expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent("Zulu Estate");
  });
});

// ---- DiaryPageClient ----

function clientProps(overrides: Partial<DiaryPageClientProps> = {}): DiaryPageClientProps {
  return {
    slug: "acme",
    today: "2026-10-21",
    from: "2026-10-19",
    to: "2026-10-25",
    initialView: "week",
    initialKinds: [],
    events: EVENTS,
    typesServed: ALL_KINDS,
    loadError: false,
    members: MEMBERS,
    matters: MATTERS,
    currentMemberId: "fe-1",
    isAdminOrOwner: true,
    ...overrides,
  };
}

describe("DiaryPageClient", () => {
  let replaceState: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    replaceState = vi.spyOn(window.history, "replaceState");
    mockRefresh.mockClear();
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

  it("switches to the List view and records view=list in the URL", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    expect(screen.getByTestId("diary-week-grid")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "List" }));
    expect(screen.getByTestId("diary-list")).toBeInTheDocument();
    expect(screen.queryByTestId("diary-week-grid")).toBeNull();
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-19&view=list"
    );
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

  it("offers Add a court date on an empty week when court dates are served", () => {
    render(<DiaryPageClient {...clientProps({ events: [] })} />);
    expect(screen.getByText("Nothing in the diary for this week.")).toBeInTheDocument();
    expect(screen.getByTestId("create-court-date-trigger")).toHaveTextContent("Add a court date");
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
