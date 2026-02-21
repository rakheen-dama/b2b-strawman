import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ScheduleList } from "@/components/schedules/ScheduleList";
import type { ScheduleResponse } from "@/lib/api/schedules";

const mockPauseSchedule = vi.fn();
const mockResumeSchedule = vi.fn();
const mockDeleteSchedule = vi.fn();

vi.mock("@/app/(app)/org/[slug]/schedules/actions", () => ({
  pauseScheduleAction: (...args: unknown[]) => mockPauseSchedule(...args),
  resumeScheduleAction: (...args: unknown[]) => mockResumeSchedule(...args),
  deleteScheduleAction: (...args: unknown[]) => mockDeleteSchedule(...args),
  createScheduleAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const ACTIVE_SCHEDULE: ScheduleResponse = {
  id: "sch-1",
  templateId: "pt-1",
  templateName: "Monthly Bookkeeping",
  customerId: "cust-1",
  customerName: "Acme Corp",
  frequency: "MONTHLY",
  startDate: "2026-01-01",
  endDate: null,
  leadTimeDays: 3,
  status: "ACTIVE",
  nextExecutionDate: "2026-03-01",
  lastExecutedAt: "2026-02-01T10:00:00Z",
  executionCount: 2,
  projectLeadMemberId: null,
  projectLeadName: null,
  nameOverride: null,
  createdBy: "user-1",
  createdByName: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

const PAUSED_SCHEDULE: ScheduleResponse = {
  ...ACTIVE_SCHEDULE,
  id: "sch-2",
  templateName: "Quarterly Tax Review",
  customerName: "Beta Inc",
  frequency: "QUARTERLY",
  status: "PAUSED",
  nextExecutionDate: null,
};

const COMPLETED_SCHEDULE: ScheduleResponse = {
  ...ACTIVE_SCHEDULE,
  id: "sch-3",
  templateName: "Annual Audit",
  customerName: "Gamma LLC",
  frequency: "ANNUALLY",
  status: "COMPLETED",
  nextExecutionDate: null,
  executionCount: 5,
};

const ALL_SCHEDULES = [ACTIVE_SCHEDULE, PAUSED_SCHEDULE, COMPLETED_SCHEDULE];

describe("ScheduleList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders schedule rows with template name and customer name", () => {
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("shows Active status badge with success variant", () => {
    render(<ScheduleList slug="acme" schedules={[ACTIVE_SCHEDULE]} />);
    const badge = screen.getByText("Active", { selector: "[data-slot='badge']" });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("shows Paused status badge with warning variant", async () => {
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    // Switch to Paused tab to see paused schedule
    await userEvent.setup().click(screen.getByRole("button", { name: "Paused" }));
    const badge = screen.getByText("Paused", { selector: "[data-slot='badge']" });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("shows Completed status badge with neutral variant", async () => {
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Completed" }));
    const badge = screen.getByText("Completed", { selector: "[data-slot='badge']" });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("shows Pause button for ACTIVE schedule", () => {
    render(<ScheduleList slug="acme" schedules={[ACTIVE_SCHEDULE]} />);
    expect(screen.getByTitle("Pause schedule")).toBeInTheDocument();
  });

  it("shows Resume button for PAUSED schedule", async () => {
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    const pausedTab = screen.getByText("Paused");
    await userEvent.setup().click(pausedTab);
    expect(screen.getByTitle("Resume schedule")).toBeInTheDocument();
  });

  it("does not show Delete button for ACTIVE schedule", () => {
    render(<ScheduleList slug="acme" schedules={[ACTIVE_SCHEDULE]} />);
    expect(screen.queryByTitle("Delete schedule")).not.toBeInTheDocument();
  });

  it("shows pause confirmation dialog when Pause button clicked", async () => {
    const user = userEvent.setup();
    render(<ScheduleList slug="acme" schedules={[ACTIVE_SCHEDULE]} />);
    await user.click(screen.getByTitle("Pause schedule"));
    expect(
      screen.getByText(/Pausing this schedule will stop automatic project creation/),
    ).toBeInTheDocument();
  });

  it("confirming pause calls pauseScheduleAction", async () => {
    mockPauseSchedule.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<ScheduleList slug="acme" schedules={[ACTIVE_SCHEDULE]} />);
    await user.click(screen.getByTitle("Pause schedule"));
    await user.click(screen.getByRole("button", { name: "Pause Schedule" }));
    await waitFor(() => {
      expect(mockPauseSchedule).toHaveBeenCalledWith("acme", "sch-1");
    });
  });

  it("shows delete confirmation dialog when Delete button clicked on paused schedule", async () => {
    const user = userEvent.setup();
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    await user.click(screen.getByRole("button", { name: "Paused" }));
    await user.click(screen.getByTitle("Delete schedule"));
    expect(screen.getByText("Delete Schedule")).toBeInTheDocument();
  });

  it("confirming delete calls deleteScheduleAction", async () => {
    mockDeleteSchedule.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);
    await user.click(screen.getByRole("button", { name: "Paused" }));
    await user.click(screen.getByTitle("Delete schedule"));
    await user.click(screen.getByText("Delete"));
    await waitFor(() => {
      expect(mockDeleteSchedule).toHaveBeenCalledWith("acme", "sch-2");
    });
  });

  it("filters to only ACTIVE schedules on Active tab and shows all on All tab", async () => {
    const user = userEvent.setup();
    render(<ScheduleList slug="acme" schedules={ALL_SCHEDULES} />);

    // Active tab (default) — only active schedule
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
    expect(screen.queryByText("Quarterly Tax Review")).not.toBeInTheDocument();
    expect(screen.queryByText("Annual Audit")).not.toBeInTheDocument();

    // All tab — all schedules visible
    await user.click(screen.getByText("All"));
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
    expect(screen.getByText("Quarterly Tax Review")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });
});
