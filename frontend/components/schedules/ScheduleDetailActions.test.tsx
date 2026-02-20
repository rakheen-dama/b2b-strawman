import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ScheduleDetailActions } from "./ScheduleDetailActions";
import type { ScheduleResponse } from "@/lib/api/schedules";

const mockPauseScheduleAction = vi.fn();
const mockResumeScheduleAction = vi.fn();
const mockDeleteScheduleAction = vi.fn();
const mockRouterPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/schedules/actions", () => ({
  pauseScheduleAction: (...args: unknown[]) => mockPauseScheduleAction(...args),
  resumeScheduleAction: (...args: unknown[]) => mockResumeScheduleAction(...args),
  deleteScheduleAction: (...args: unknown[]) => mockDeleteScheduleAction(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockRouterPush }),
}));

const makeSchedule = (overrides: Partial<ScheduleResponse> = {}): ScheduleResponse => ({
  id: "sched-1",
  templateId: "tmpl-1",
  templateName: "Monthly Report",
  customerId: "cust-1",
  customerName: "Acme Corp",
  frequency: "MONTHLY",
  startDate: "2026-01-01",
  endDate: null,
  leadTimeDays: 7,
  nameOverride: null,
  projectLeadMemberId: null,
  projectLeadName: null,
  status: "ACTIVE",
  executionCount: 0,
  lastExecutedAt: null,
  nextExecutionDate: "2026-02-01",
  createdBy: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("ScheduleDetailActions", () => {
  it("opens pause dialog when Pause button is clicked", async () => {
    const user = userEvent.setup();
    render(<ScheduleDetailActions slug="acme" schedule={makeSchedule({ status: "ACTIVE" })} />);

    // Click the Pause toolbar button (has icon + text "Pause")
    await user.click(screen.getByRole("button", { name: "Pause" }));

    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText("Pausing this schedule will stop automatic project creation. You can resume it at any time.")).toBeInTheDocument();
  });

  it("calls pauseScheduleAction with correct args when confirmed", async () => {
    mockPauseScheduleAction.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<ScheduleDetailActions slug="acme" schedule={makeSchedule({ status: "ACTIVE" })} />);

    await user.click(screen.getByRole("button", { name: "Pause" }));
    // The confirm button inside the dialog
    await user.click(screen.getByRole("button", { name: "Pause Schedule" }));

    await waitFor(() => {
      expect(mockPauseScheduleAction).toHaveBeenCalledWith("acme", "sched-1");
    });
  });

  it("shows Delete button for PAUSED schedule", () => {
    render(<ScheduleDetailActions slug="acme" schedule={makeSchedule({ status: "PAUSED" })} />);

    expect(screen.getByRole("button", { name: /delete/i })).toBeInTheDocument();
  });

  it("calls deleteScheduleAction and redirects on successful delete", async () => {
    mockDeleteScheduleAction.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<ScheduleDetailActions slug="acme" schedule={makeSchedule({ status: "PAUSED" })} />);

    await user.click(screen.getByRole("button", { name: /delete/i }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    await waitFor(() => {
      expect(mockDeleteScheduleAction).toHaveBeenCalledWith("acme", "sched-1");
      expect(mockRouterPush).toHaveBeenCalledWith("/org/acme/schedules");
    });
  });
});
