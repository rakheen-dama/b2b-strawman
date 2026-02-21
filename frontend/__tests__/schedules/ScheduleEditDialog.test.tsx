import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ScheduleEditDialog } from "@/components/schedules/ScheduleEditDialog";
import type { ScheduleResponse } from "@/lib/api/schedules";
import type { OrgMember } from "@/lib/types";

const mockUpdateSchedule = vi.fn();

vi.mock("@/app/(app)/org/[slug]/schedules/actions", () => ({
  updateScheduleAction: (...args: unknown[]) => mockUpdateSchedule(...args),
  createScheduleAction: vi.fn(),
  deleteScheduleAction: vi.fn(),
  pauseScheduleAction: vi.fn(),
  resumeScheduleAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const SCHEDULE: ScheduleResponse = {
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
  nameOverride: "Custom Name",
  createdBy: "user-1",
  createdByName: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

const MEMBERS: OrgMember[] = [
  {
    id: "mem-1",
    name: "Jane Doe",
    email: "jane@example.com",
    avatarUrl: null,
    orgRole: "org:admin",
  },
];

describe("ScheduleEditDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders pre-filled nameOverride field", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleEditDialog slug="acme" schedule={SCHEDULE} orgMembers={MEMBERS}>
        <button>Edit Schedule Trigger</button>
      </ScheduleEditDialog>,
    );
    await user.click(screen.getByText("Edit Schedule Trigger"));
    const nameInput = screen.getByLabelText(/Name Override/);
    expect(nameInput).toHaveValue("Custom Name");
  });

  it("displays read-only template and customer name", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleEditDialog slug="acme" schedule={SCHEDULE} orgMembers={MEMBERS}>
        <button>Edit Schedule RO Trigger</button>
      </ScheduleEditDialog>,
    );
    await user.click(screen.getByText("Edit Schedule RO Trigger"));
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("leadTimeDays accepts number input", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleEditDialog slug="acme" schedule={SCHEDULE} orgMembers={MEMBERS}>
        <button>Edit Schedule LT Trigger</button>
      </ScheduleEditDialog>,
    );
    await user.click(screen.getByText("Edit Schedule LT Trigger"));
    const leadTimeInput = screen.getByLabelText("Lead Time (days)");
    expect(leadTimeInput).toHaveAttribute("type", "number");
    expect(leadTimeInput).toHaveValue(3);
  });

  it("submit calls updateScheduleAction with correct args", async () => {
    mockUpdateSchedule.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <ScheduleEditDialog slug="acme" schedule={SCHEDULE} orgMembers={MEMBERS}>
        <button>Edit Schedule Submit Trigger</button>
      </ScheduleEditDialog>,
    );
    await user.click(screen.getByText("Edit Schedule Submit Trigger"));
    await user.click(screen.getByText("Save Changes"));
    await waitFor(() => {
      expect(mockUpdateSchedule).toHaveBeenCalledWith(
        "acme",
        "sch-1",
        expect.objectContaining({ leadTimeDays: 3 }),
      );
    });
  });
});
