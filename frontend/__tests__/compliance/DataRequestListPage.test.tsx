import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { DataRequestTable } from "@/components/compliance/DataRequestTable";
import type { DataRequestResponse } from "@/lib/types";

// Mock next/navigation (used by Link)
import { vi } from "vitest";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/org/acme/compliance/requests",
}));

const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD"
const [y, m, d] = today.split("-").map(Number);
const pastDate = `${y - 1}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
const futureDate = `${y + 1}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

const mockRequests: DataRequestResponse[] = [
  {
    id: "req-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    requestType: "DELETION",
    status: "RECEIVED",
    description: "Please delete all my data.",
    rejectionReason: null,
    deadline: futureDate,
    requestedAt: "2026-02-18T10:00:00Z",
    requestedBy: "member-1",
    completedAt: null,
    completedBy: null,
    hasExport: false,
    notes: null,
    createdAt: "2026-02-18T10:00:00Z",
  },
  {
    id: "req-2",
    customerId: "cust-2",
    customerName: "Beta Ltd",
    requestType: "ACCESS",
    status: "IN_PROGRESS",
    description: "Please provide a copy of my data.",
    rejectionReason: null,
    deadline: pastDate,
    requestedAt: "2026-02-01T10:00:00Z",
    requestedBy: "member-2",
    completedAt: null,
    completedBy: null,
    hasExport: false,
    notes: null,
    createdAt: "2026-02-01T10:00:00Z",
  },
];

describe("DataRequestTable", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders request row with customer name", () => {
    render(<DataRequestTable requests={mockRequests} slug="acme" />);
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Ltd")).toBeInTheDocument();
  });

  it("shows request type badge for each row", () => {
    render(<DataRequestTable requests={mockRequests} slug="acme" />);
    expect(screen.getByText("Deletion")).toBeInTheDocument();
    expect(screen.getByText("Access")).toBeInTheDocument();
  });

  it("shows overdue deadline in red when deadline is in the past and status is not completed", () => {
    render(<DataRequestTable requests={mockRequests} slug="acme" />);
    // Beta Ltd has a past deadline and IN_PROGRESS status — should show overdue styling
    const overdueCell = screen.getByText(/Overdue/);
    expect(overdueCell).toBeInTheDocument();
    expect(overdueCell).toHaveClass("text-red-600");
  });

  it("does not show overdue text when deadline is in the future", () => {
    render(
      <DataRequestTable
        requests={[mockRequests[0]]}
        slug="acme"
      />,
    );
    // Acme Corp has a future deadline — should NOT show overdue
    expect(screen.queryByText(/Overdue/)).not.toBeInTheDocument();
  });
});
