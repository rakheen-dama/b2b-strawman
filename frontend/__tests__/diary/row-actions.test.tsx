import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 613C — diary row actions: which rows offer which actions (backend transitions +
// MANAGE_LEGAL / assignee rules), the menu itself, and the page-level hand-off to the retained
// court-date dialogs (real dialogs, mocked server actions) and Mark done.

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

const { mockPush, mockRefresh, mockCompleteTask, mockPostpone, mockToast } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockRefresh: vi.fn(),
  mockCompleteTask: vi.fn(),
  mockPostpone: vi.fn(),
  mockToast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), refresh: mockRefresh, prefetch: vi.fn() }),
  usePathname: () => "/org/acme/diary",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/app/(app)/org/[slug]/court-calendar/actions", () => ({
  postponeCourtDate: mockPostpone,
  cancelCourtDate: vi.fn().mockResolvedValue({ success: true }),
  recordOutcome: vi.fn().mockResolvedValue({ success: true }),
  createCourtDate: vi.fn().mockResolvedValue({ success: true }),
  createPrescriptionTracker: vi.fn().mockResolvedValue({ success: true }),
  fetchProjects: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/app/(app)/org/[slug]/matters/[id]/task-actions", () => ({
  completeTask: mockCompleteTask,
}));
vi.mock("sonner", () => ({ toast: mockToast }));

import {
  DiaryActionsProvider,
  DiaryRowActions,
  availableDiaryActions,
  type DiaryActionPermissions,
} from "@/components/diary/diary-row-actions";
import { DiaryListView } from "@/components/diary/diary-list-view";
import {
  DiaryPageClient,
  type DiaryPageClientProps,
} from "@/app/(app)/org/[slug]/diary/diary-page-client";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function ev(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "x-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Untitled",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Mthembu v Absa Bank",
    overdue: false,
    href: "/matters/m-1",
    ...overrides,
  };
}

const scheduled = ev({
  id: "cd-1",
  kind: "COURT_DATE",
  title: "Johannesburg High Court — Motion",
  status: "SCHEDULED",
  feeEarnerId: "fe-1",
});
const postponed = ev({
  id: "cd-2",
  kind: "COURT_DATE",
  title: "Pretoria High Court — Trial",
  status: "POSTPONED",
});
const ownTask = ev({
  id: "t-1",
  title: "Finalise heads of argument",
  status: "IN_PROGRESS",
  feeEarnerId: "fe-1",
});
const othersTask = ev({
  id: "t-2",
  title: "Index the trial bundle",
  status: "IN_PROGRESS",
  feeEarnerId: "fe-2",
});
const openTask = ev({ id: "t-3", title: "Collect ID copies", status: "OPEN", feeEarnerId: "fe-1" });
const prescription = ev({
  id: "p-1",
  kind: "PRESCRIPTION",
  title: "Prescription — Debt",
  status: "RUNNING",
});
const matterDue = ev({
  id: "md-1",
  kind: "MATTER_DUE",
  title: "Mthembu v Absa Bank",
  status: "ACTIVE",
});

const MEMBER: DiaryActionPermissions = {
  canManageLegal: false,
  canCompleteAnyTask: false,
  currentMemberId: "fe-1",
};
const LEGAL_MEMBER: DiaryActionPermissions = { ...MEMBER, canManageLegal: true };
const OWNER: DiaryActionPermissions = {
  canManageLegal: true,
  canCompleteAnyTask: true,
  currentMemberId: "fe-9",
};

describe("availableDiaryActions", () => {
  it("offers postpone, record outcome and cancel on a scheduled court date", () => {
    expect(availableDiaryActions(scheduled, LEGAL_MEMBER)).toEqual([
      "postpone",
      "outcome",
      "cancel",
    ]);
  });

  it("does not offer postpone on a postponed court date", () => {
    expect(availableDiaryActions(postponed, LEGAL_MEMBER)).toEqual(["outcome", "cancel"]);
  });

  it("offers no court-date actions without MANAGE_LEGAL", () => {
    expect(availableDiaryActions(scheduled, MEMBER)).toEqual([]);
  });

  it("offers Mark done on an in-progress task assigned to the current member", () => {
    expect(availableDiaryActions(ownTask, MEMBER)).toEqual(["mark-done"]);
    expect(availableDiaryActions(othersTask, MEMBER)).toEqual([]);
  });

  it("lets an admin or owner mark anyone's in-progress task done", () => {
    expect(availableDiaryActions(othersTask, OWNER)).toEqual(["mark-done"]);
  });

  it("never offers Mark done on an open task (OPEN → DONE is not a valid transition)", () => {
    expect(availableDiaryActions(openTask, OWNER)).toEqual([]);
  });

  it("offers nothing on prescriptions and matter due dates", () => {
    expect(availableDiaryActions(prescription, OWNER)).toEqual([]);
    expect(availableDiaryActions(matterDue, OWNER)).toEqual([]);
  });

  it("does not treat an unknown member as the assignee of an unassigned task", () => {
    const unassigned = ev({ id: "t-4", status: "IN_PROGRESS" });
    expect(availableDiaryActions(unassigned, { ...MEMBER, currentMemberId: null })).toEqual([]);
  });
});

function renderRow(
  event: DiaryEventResponse,
  permissions: DiaryActionPermissions = LEGAL_MEMBER,
  onAction = vi.fn()
) {
  render(
    <DiaryActionsProvider value={{ ...permissions, onAction }}>
      <DiaryRowActions event={event} />
    </DiaryActionsProvider>
  );
  return onAction;
}

describe("DiaryRowActions", () => {
  it("renders nothing without a provider", () => {
    const { container } = render(<DiaryRowActions event={scheduled} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders no trigger on rows without an available action", () => {
    for (const event of [prescription, matterDue, openTask]) {
      renderRow(event, OWNER);
      expect(screen.queryByRole("button")).toBeNull();
      cleanup();
    }
  });

  it("opens a menu with exactly the status-appropriate items", async () => {
    const user = userEvent.setup();
    renderRow(postponed);
    await user.click(
      screen.getByRole("button", { name: "Actions for Pretoria High Court — Trial" })
    );
    const items = screen.getAllByRole("menuitem").map((i) => i.textContent);
    expect(items).toEqual(["Record outcome", "Cancel"]);
  });

  it("hands the chosen action and event to the page", async () => {
    const user = userEvent.setup();
    const onAction = renderRow(scheduled);
    await user.click(screen.getByRole("button", { name: /Actions for Johannesburg/ }));
    await user.click(screen.getByRole("menuitem", { name: "Postpone" }));
    expect(onAction).toHaveBeenCalledWith(scheduled, "postpone");
  });

  it("disables the menu animation under prefers-reduced-motion", async () => {
    const user = userEvent.setup();
    renderRow(scheduled);
    await user.click(screen.getByRole("button", { name: /Actions for Johannesburg/ }));
    expect(screen.getByRole("menu")).toHaveClass("motion-reduce:animate-none");
  });

  it("sits in the List view's Actions column", () => {
    render(
      <DiaryActionsProvider value={{ ...LEGAL_MEMBER, onAction: vi.fn() }}>
        <DiaryListView events={[scheduled, prescription]} band={[]} slug="acme" />
      </DiaryActionsProvider>
    );
    const row = screen.getByTestId("diary-list-row-cd-1");
    const cells = within(row).getAllByRole("cell");
    expect(within(cells[4]).getByRole("button", { name: /Actions for/ })).toBeInTheDocument();
    expect(within(screen.getByTestId("diary-list-row-p-1")).queryByRole("button")).toBeNull();
  });
});

function clientProps(overrides: Partial<DiaryPageClientProps> = {}): DiaryPageClientProps {
  return {
    slug: "acme",
    today: "2026-10-21",
    from: "2026-10-19",
    to: "2026-10-25",
    initialView: "week",
    initialKinds: null,
    events: [scheduled, ownTask, prescription],
    typesServed: ["TASK_DUE", "MATTER_DUE", "COURT_DATE", "PRESCRIPTION"],
    loadError: false,
    members: [{ id: "fe-1", name: "Lerato Dube", email: "lerato@firm.test" }],
    matters: [],
    currentMemberId: "fe-1",
    isAdminOrOwner: false,
    canCompleteAnyTask: false,
    canManageLegal: true,
    ...overrides,
  };
}

describe("DiaryPageClient row-action host", () => {
  beforeEach(() => {
    mockPostpone.mockResolvedValue({ success: true });
  });

  it("opens the retained Postpone dialog for that court date and refreshes after saving", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(screen.getByRole("button", { name: /Actions for Johannesburg/ }));
    await user.click(screen.getByRole("menuitem", { name: "Postpone" }));

    const dialog = await screen.findByTestId("postpone-dialog");
    await user.type(within(dialog).getByLabelText("New Date"), "2026-11-12");
    await user.type(within(dialog).getByLabelText("Reason"), "Counsel unavailable");
    await user.click(within(dialog).getByRole("button", { name: "Postpone" }));

    await waitFor(() =>
      expect(mockPostpone).toHaveBeenCalledWith("acme", "cd-1", {
        newDate: "2026-11-12",
        reason: "Counsel unavailable",
      })
    );
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());
  });

  it("opens Record outcome and Cancel for the chosen court date", async () => {
    const user = userEvent.setup();
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(screen.getByRole("button", { name: /Actions for Johannesburg/ }));
    await user.click(screen.getByRole("menuitem", { name: "Record outcome" }));
    expect(await screen.findByTestId("outcome-dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByTestId("outcome-dialog")).toBeNull());

    await user.click(screen.getByRole("button", { name: /Actions for Johannesburg/ }));
    await user.click(screen.getByRole("menuitem", { name: "Cancel" }));
    expect(await screen.findByTestId("cancel-court-date-dialog")).toBeInTheDocument();
  });

  it("marks an in-progress task done, toasts and refreshes", async () => {
    const user = userEvent.setup();
    mockCompleteTask.mockResolvedValue({ success: true });
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(
      screen.getByRole("button", { name: "Actions for Finalise heads of argument" })
    );
    await user.click(screen.getByRole("menuitem", { name: "Mark done" }));

    await waitFor(() => expect(mockCompleteTask).toHaveBeenCalledWith("acme", "t-1", "m-1"));
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());
    expect(mockToast.success).toHaveBeenCalledWith("Task marked done");
  });

  it("surfaces a failed Mark done as an error toast without refreshing", async () => {
    const user = userEvent.setup();
    mockCompleteTask.mockResolvedValue({
      success: false,
      error: "Only the assignee or an admin/owner can complete this task",
    });
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(
      screen.getByRole("button", { name: "Actions for Finalise heads of argument" })
    );
    await user.click(screen.getByRole("menuitem", { name: "Mark done" }));

    await waitFor(() =>
      expect(mockToast.error).toHaveBeenCalledWith(
        "Only the assignee or an admin/owner can complete this task"
      )
    );
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("toasts a generic error when the server action throws", async () => {
    const user = userEvent.setup();
    mockCompleteTask.mockRejectedValue(new Error("network"));
    render(<DiaryPageClient {...clientProps()} />);
    await user.click(
      screen.getByRole("button", { name: "Actions for Finalise heads of argument" })
    );
    await user.click(screen.getByRole("menuitem", { name: "Mark done" }));
    await waitFor(() =>
      expect(mockToast.error).toHaveBeenCalledWith("Failed to mark the task done.")
    );
  });

  it("offers no court-date actions without MANAGE_LEGAL", () => {
    render(<DiaryPageClient {...clientProps({ canManageLegal: false })} />);
    expect(screen.getByText("Johannesburg High Court — Motion")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Actions for Johannesburg/ })).toBeNull();
    expect(
      screen.getByRole("button", { name: "Actions for Finalise heads of argument" })
    ).toBeInTheDocument();
  });
});
