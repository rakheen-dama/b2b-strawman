import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ScheduleCreateDialog } from "@/components/schedules/ScheduleCreateDialog";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

const mockCreateSchedule = vi.fn();

vi.mock("@/app/(app)/org/[slug]/schedules/actions", () => ({
  createScheduleAction: (...args: unknown[]) => mockCreateSchedule(...args),
  pauseScheduleAction: vi.fn(),
  resumeScheduleAction: vi.fn(),
  deleteScheduleAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const TEMPLATES: ProjectTemplateResponse[] = [
  {
    id: "pt-1",
    name: "Monthly Bookkeeping",
    namePattern: "{customer} \u2014 {month} {year}",
    description: "Standard monthly bookkeeping",
    billableDefault: true,
    source: "MANUAL",
    sourceProjectId: null,
    active: true,
    taskCount: 5,
    tagCount: 1,
    tasks: [],
    tags: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

const CUSTOMERS: Customer[] = [
  {
    id: "cust-1",
    name: "Acme Corp",
    email: "acme@example.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "user-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

const MEMBERS: OrgMember[] = [
  {
    id: "mem-1",
    name: "Jane Doe",
    email: "jane@example.com",
    avatarUrl: null,
    orgRole: "org:admin",
  },
];

describe("ScheduleCreateDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog when trigger is clicked", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleCreateDialog
        slug="acme"
        templates={TEMPLATES}
        customers={CUSTOMERS}
        orgMembers={MEMBERS}
      >
        <button>Create Schedule trigger</button>
      </ScheduleCreateDialog>,
    );
    await user.click(screen.getByText("Create Schedule trigger"));
    expect(screen.getByText("New Recurring Schedule")).toBeInTheDocument();
  });

  it("shows exactly 6 frequency options", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleCreateDialog
        slug="acme"
        templates={TEMPLATES}
        customers={CUSTOMERS}
        orgMembers={MEMBERS}
      >
        <button>Create Schedule trigger freq</button>
      </ScheduleCreateDialog>,
    );
    await user.click(screen.getByText("Create Schedule trigger freq"));
    const frequencySelect = screen.getByLabelText("Frequency");
    const options = frequencySelect.querySelectorAll("option");
    expect(options).toHaveLength(6);
    expect(options[0].textContent).toBe("Weekly");
    expect(options[1].textContent).toBe("Fortnightly");
    expect(options[2].textContent).toBe("Monthly");
    expect(options[3].textContent).toBe("Quarterly");
    expect(options[4].textContent).toBe("Semi-Annually");
    expect(options[5].textContent).toBe("Annually");
  });

  it("has a required start date input", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleCreateDialog
        slug="acme"
        templates={TEMPLATES}
        customers={CUSTOMERS}
        orgMembers={MEMBERS}
      >
        <button>Create Schedule trigger date</button>
      </ScheduleCreateDialog>,
    );
    await user.click(screen.getByText("Create Schedule trigger date"));
    const startDateInput = screen.getByLabelText("Start Date");
    expect(startDateInput).toBeInTheDocument();
    expect(startDateInput).toHaveAttribute("type", "date");
    expect(startDateInput).toBeRequired();
  });

  it("shows name preview when template is selected", async () => {
    const user = userEvent.setup();
    render(
      <ScheduleCreateDialog
        slug="acme"
        templates={TEMPLATES}
        customers={CUSTOMERS}
        orgMembers={MEMBERS}
      >
        <button>Create Schedule trigger preview</button>
      </ScheduleCreateDialog>,
    );
    await user.click(screen.getByText("Create Schedule trigger preview"));

    // Select the template
    await user.click(screen.getByText("Monthly Bookkeeping"));

    // Name preview should appear (without customer it resolves partially)
    await waitFor(() => {
      expect(screen.getByText("Name preview:")).toBeInTheDocument();
    });
  });

  it("closes dialog on successful submit", async () => {
    mockCreateSchedule.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <ScheduleCreateDialog
        slug="acme"
        templates={TEMPLATES}
        customers={CUSTOMERS}
        orgMembers={MEMBERS}
      >
        <button>Create Schedule trigger submit</button>
      </ScheduleCreateDialog>,
    );
    await user.click(screen.getByText("Create Schedule trigger submit"));

    // Fill required fields
    await user.click(screen.getByText("Monthly Bookkeeping"));

    const customerSelect = screen.getByLabelText("Customer");
    await user.selectOptions(customerSelect, "cust-1");

    const startDateInput = screen.getByLabelText("Start Date");
    await user.type(startDateInput, "2026-03-01");

    await user.click(screen.getByText("Create Schedule"));

    await waitFor(() => {
      expect(mockCreateSchedule).toHaveBeenCalledWith("acme", expect.objectContaining({
        templateId: "pt-1",
        customerId: "cust-1",
        frequency: "MONTHLY",
        startDate: "2026-03-01",
      }));
    });

    await waitFor(() => {
      expect(screen.queryByText("New Recurring Schedule")).not.toBeInTheDocument();
    });
  });
});
