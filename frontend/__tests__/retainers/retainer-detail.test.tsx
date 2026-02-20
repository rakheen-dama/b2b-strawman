import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetainerDetailActions } from "@/components/retainers/retainer-detail-actions";
import { EditRetainerDialog } from "@/components/retainers/edit-retainer-dialog";
import { Button } from "@/components/ui/button";
import type { RetainerResponse } from "@/lib/api/retainers";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/retainers/[id]/actions", () => ({
  updateRetainerAction: vi.fn(),
  closePeriodAction: vi.fn(),
  pauseRetainerAction: vi.fn(),
  resumeRetainerAction: vi.fn(),
  terminateRetainerAction: vi.fn(),
}));

const BASE_RETAINER: RetainerResponse = {
  id: "ret-1",
  customerId: "cust-1",
  scheduleId: null,
  customerName: "Acme Corp",
  name: "Monthly Bookkeeping Retainer",
  type: "HOUR_BANK",
  status: "ACTIVE",
  frequency: "MONTHLY",
  startDate: "2026-01-01",
  endDate: null,
  allocatedHours: 40,
  periodFee: 5000,
  rolloverPolicy: "FORFEIT",
  rolloverCapHours: null,
  notes: "Test notes",
  createdBy: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
  currentPeriod: {
    id: "per-1",
    periodStart: "2026-02-01",
    periodEnd: "2026-02-28",
    status: "OPEN",
    allocatedHours: 40,
    baseAllocatedHours: 40,
    consumedHours: 25,
    remainingHours: 15,
    rolloverHoursIn: 0,
    overageHours: 0,
    rolloverHoursOut: 0,
    invoiceId: null,
    closedAt: null,
    closedBy: null,
    readyToClose: false,
  },
  recentPeriods: [],
};

describe("RetainerDetailActions", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows Pause button for ACTIVE retainer", () => {
    render(
      <RetainerDetailActions slug="acme" retainer={BASE_RETAINER} />,
    );

    expect(screen.getByText("Pause")).toBeInTheDocument();
    expect(screen.getByText("Terminate")).toBeInTheDocument();
    expect(screen.queryByText("Resume")).not.toBeInTheDocument();
  });

  it("shows Resume button for PAUSED retainer, not Pause", () => {
    const paused: RetainerResponse = {
      ...BASE_RETAINER,
      status: "PAUSED",
    };

    render(
      <RetainerDetailActions slug="acme" retainer={paused} />,
    );

    expect(screen.getByText("Resume")).toBeInTheDocument();
    expect(screen.queryByText("Pause")).not.toBeInTheDocument();
    expect(screen.getByText("Terminate")).toBeInTheDocument();
  });

  it("shows Close Period button only when readyToClose is true", () => {
    const readyRetainer: RetainerResponse = {
      ...BASE_RETAINER,
      currentPeriod: {
        ...BASE_RETAINER.currentPeriod!,
        readyToClose: true,
      },
    };

    render(
      <RetainerDetailActions slug="acme" retainer={readyRetainer} />,
    );

    expect(screen.getByText("Close Period")).toBeInTheDocument();
  });

  it("hides Close Period button when readyToClose is false", () => {
    render(
      <RetainerDetailActions slug="acme" retainer={BASE_RETAINER} />,
    );

    expect(screen.queryByText("Close Period")).not.toBeInTheDocument();
  });
});

describe("EditRetainerDialog", () => {
  afterEach(() => {
    cleanup();
  });

  it("pre-fills current retainer values when opened", async () => {
    const { default: userEvent } = await import("@testing-library/user-event");
    const user = userEvent.setup();

    render(
      <EditRetainerDialog slug="acme" retainer={BASE_RETAINER}>
        <Button>Edit Retainer</Button>
      </EditRetainerDialog>,
    );

    await user.click(screen.getByText("Edit Retainer"));

    // Check pre-filled name
    const nameInput = screen.getByLabelText("Name") as HTMLInputElement;
    expect(nameInput.value).toBe("Monthly Bookkeeping Retainer");

    // Check read-only customer shown
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();

    // Check read-only type shown
    expect(screen.getByText("Hour Bank")).toBeInTheDocument();
  });
});
