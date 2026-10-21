import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 613C — DiaryPageClient toolbar: view tabs (Day/Week/Month/List), range navigation
// (previous / today / next / date input), header create buttons (module + MANAGE_LEGAL gated) and
// fee-earner / matter filters persisted in the URL across navigations.

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

const { mockPush } = vi.hoisted(() => ({ mockPush: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/diary",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/legal/create-court-date-dialog", () => ({
  CreateCourtDateDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button">{triggerLabel}</button>
  ),
}));
vi.mock("@/components/legal/create-prescription-dialog", () => ({
  CreatePrescriptionDialog: ({ triggerLabel }: { triggerLabel?: React.ReactNode }) => (
    <button type="button">{triggerLabel}</button>
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
import type { DiaryEventResponse, DiaryKind } from "@/app/(app)/org/[slug]/diary/diary-types";

const ALL_KINDS: DiaryKind[] = ["TASK_DUE", "MATTER_DUE", "COURT_DATE", "PRESCRIPTION"];

const EVENTS: DiaryEventResponse[] = [
  {
    id: "cd-1",
    kind: "COURT_DATE",
    date: "2026-10-20",
    title: "Durban High Court — Motion",
    status: "SCHEDULED",
    matterId: "m-1",
    matterName: "Pillay v Transnet",
    feeEarnerId: "fe-1",
    feeEarnerName: "Ayanda Zungu",
    overdue: false,
    href: "/matters/m-1",
  },
  {
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-08",
    title: "Draft discovery affidavit",
    status: "OPEN",
    matterId: "m-2",
    matterName: "Govender Estate",
    feeEarnerId: "fe-2",
    feeEarnerName: "Johan Pretorius",
    overdue: false,
    href: "/matters/m-2",
  },
];

function props(overrides: Partial<DiaryPageClientProps> = {}): DiaryPageClientProps {
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
    members: [
      { id: "fe-1", name: "Ayanda Zungu", email: "ayanda@firm.test" },
      { id: "fe-2", name: "Johan Pretorius", email: "johan@firm.test" },
    ],
    matters: [
      { id: "m-1", name: "Pillay v Transnet", referenceNumber: "LIT-007" },
      { id: "m-2", name: "Govender Estate", referenceNumber: null },
    ],
    currentMemberId: "fe-1",
    isAdminOrOwner: true,
    canManageLegal: true,
    ...overrides,
  };
}

const MONTH = { from: "2026-10-01", to: "2026-10-31" } as const;
const PUSH_OPTS = { scroll: false };

describe("Diary range navigation (613C)", () => {
  let replaceState: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    replaceState = vi.spyOn(window.history, "replaceState");
    mockPush.mockClear();
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("offers Day, Week, Month and List tabs whose active tab controls a real panel", () => {
    render(<DiaryPageClient {...props()} />);
    const tabs = screen.getAllByRole("tab");
    expect(tabs.map((t) => t.textContent)).toEqual(["Day", "Week", "Month", "List"]);
    const active = screen.getByRole("tab", { name: "Week" });
    expect(active).toHaveAttribute("aria-selected", "true");
    const panel = document.getElementById(active.getAttribute("aria-controls")!);
    expect(panel).toHaveAttribute("role", "tabpanel");
    expect(panel).toContainElement(screen.getByTestId("diary-week-grid"));
  });

  it("steps to the next week", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props()} />);
    await user.click(screen.getByRole("button", { name: "Next week" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-26", PUSH_OPTS);
  });

  it("steps to the previous month", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props({ ...MONTH, initialView: "month" })} />);
    expect(screen.getByRole("group", { name: "Date range" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-09-01&view=month", PUSH_OPTS);
  });

  it("steps one day in the Day view", async () => {
    const user = userEvent.setup();
    render(
      <DiaryPageClient {...props({ from: "2026-10-21", to: "2026-10-21", initialView: "day" })} />
    );
    await user.click(screen.getByRole("button", { name: "Previous day" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-20&view=day", PUSH_OPTS);
  });

  it("disables Today when the window already contains today", () => {
    render(<DiaryPageClient {...props()} />);
    expect(screen.getByRole("button", { name: "Today" })).toBeDisabled();
  });

  it("jumps back to today's window", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props({ from: "2026-11-02", to: "2026-11-08" })} />);
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-19", PUSH_OPTS);
  });

  it("labels the range per view", () => {
    render(<DiaryPageClient {...props()} />);
    expect(screen.getByTestId("diary-range")).toHaveTextContent("19 October to 25 October 2026");
    cleanup();
    render(<DiaryPageClient {...props({ ...MONTH, initialView: "list" })} />);
    expect(screen.getByTestId("diary-range")).toHaveTextContent("October 2026");
    cleanup();
    render(
      <DiaryPageClient {...props({ from: "2026-10-21", to: "2026-10-21", initialView: "day" })} />
    );
    expect(screen.getByTestId("diary-range")).toHaveTextContent("Wednesday 21 October 2026");
  });

  it("navigates to the month window when switching Week to Month", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props()} />);
    await user.click(screen.getByRole("tab", { name: "Month" }));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-01&view=month", PUSH_OPTS);
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("anchors a view switch on today when it is in the window, else on the window start", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props()} />);
    await user.click(screen.getByRole("tab", { name: "Day" }));
    expect(mockPush).toHaveBeenLastCalledWith(
      "/org/acme/diary?from=2026-10-21&view=day",
      PUSH_OPTS
    );
    cleanup();
    render(<DiaryPageClient {...props({ from: "2026-11-02", to: "2026-11-08" })} />);
    await user.click(screen.getByRole("tab", { name: "Day" }));
    expect(mockPush).toHaveBeenLastCalledWith(
      "/org/acme/diary?from=2026-11-02&view=day",
      PUSH_OPTS
    );
  });

  it("goes to the window containing a typed date, ignoring partial years and the same window", () => {
    render(<DiaryPageClient {...props()} />);
    const input = screen.getByLabelText("Go to date");
    expect(input).toHaveValue("2026-10-19");
    fireEvent.change(input, { target: { value: "0002-12-25" } });
    fireEvent.change(input, { target: { value: "2026-10-23" } });
    expect(mockPush).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { value: "2026-12-25" } });
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-12-21", PUSH_OPTS);
  });

  it("opens a day from the Month view", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props({ ...MONTH, initialView: "month" })} />);
    await user.click(screen.getByTestId("diary-month-day-2026-10-08"));
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-08&view=day", PUSH_OPTS);
  });

  it("opens a day from the Week view with Enter", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props()} />);
    screen.getByTestId("diary-day-2026-10-21").focus();
    await user.keyboard("{Enter}");
    expect(mockPush).toHaveBeenCalledWith("/org/acme/diary?from=2026-10-21&view=day", PUSH_OPTS);
  });

  it("renders the Day and Month bodies for those views", () => {
    render(
      <DiaryPageClient {...props({ from: "2026-10-20", to: "2026-10-20", initialView: "day" })} />
    );
    expect(screen.getByTestId("diary-day-view")).toBeInTheDocument();
    cleanup();
    render(<DiaryPageClient {...props({ ...MONTH, initialView: "month" })} />);
    expect(screen.getByRole("grid", { name: "October 2026" })).toBeInTheDocument();
  });
});

describe("Diary header buttons (613C)", () => {
  afterEach(() => cleanup());

  it("shows New court date and New prescription tracker when both are served and allowed", () => {
    render(<DiaryPageClient {...props()} />);
    expect(screen.getByRole("button", { name: "New court date" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New prescription tracker" })).toBeInTheDocument();
  });

  it("hides a button whose kind is not served", () => {
    render(<DiaryPageClient {...props({ typesServed: ["TASK_DUE", "MATTER_DUE"] })} />);
    expect(screen.queryByRole("button", { name: "New court date" })).toBeNull();
    expect(screen.queryByRole("button", { name: "New prescription tracker" })).toBeNull();
  });

  it("hides both without MANAGE_LEGAL", () => {
    render(<DiaryPageClient {...props({ canManageLegal: false })} />);
    expect(screen.queryByRole("button", { name: "New court date" })).toBeNull();
    expect(screen.queryByRole("button", { name: "New prescription tracker" })).toBeNull();
  });

  it("renders no header buttons in the load-error state", () => {
    render(<DiaryPageClient {...props({ loadError: true, events: [] })} />);
    expect(screen.queryByRole("button", { name: "New court date" })).toBeNull();
  });
});

describe("Diary filters in the URL (613C)", () => {
  let replaceState: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    replaceState = vi.spyOn(window.history, "replaceState");
    mockPush.mockClear();
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("records a chosen fee earner and carries it through range navigation", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props()} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    await user.click(await screen.findByRole("option", { name: "Johan Pretorius" }));
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-19&feeEarner=fe-2"
    );
    await user.click(screen.getByRole("button", { name: "Next week" }));
    expect(mockPush).toHaveBeenCalledWith(
      "/org/acme/diary?from=2026-10-26&feeEarner=fe-2",
      PUSH_OPTS
    );
  });

  it("records All for a member (whose default is Mine) and omits the default", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props({ isAdminOrOwner: false })} />);
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    await user.click(await screen.findByRole("option", { name: "All fee earners" }));
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-19&feeEarner=all"
    );
    await user.click(screen.getByRole("combobox", { name: "Fee earner" }));
    await user.click(await screen.findByRole("option", { name: "Mine" }));
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "/org/acme/diary?from=2026-10-19");
  });

  it("records the matter filter and carries kinds through a view switch", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...props({ initialKinds: ["COURT_DATE"] })} />);
    await user.click(screen.getByRole("combobox", { name: "Matter" }));
    await user.click(await screen.findByRole("option", { name: /Pillay v Transnet/ }));
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/org/acme/diary?from=2026-10-19&types=COURT_DATE&matter=m-1"
    );
    await user.click(screen.getByRole("tab", { name: "Month" }));
    expect(mockPush).toHaveBeenCalledWith(
      "/org/acme/diary?from=2026-10-01&view=month&types=COURT_DATE&matter=m-1",
      PUSH_OPTS
    );
  });

  it("seeds the fee earner and matter from the URL", () => {
    render(<DiaryPageClient {...props({ initialFeeEarner: "fe-2", initialMatterId: "m-2" })} />);
    expect(screen.getByRole("combobox", { name: "Fee earner" })).toHaveTextContent(
      "Johan Pretorius"
    );
    expect(screen.getByRole("combobox", { name: "Matter" })).toHaveTextContent("Govender Estate");
    expect(screen.queryByText("Durban High Court — Motion")).toBeNull();
  });
});
