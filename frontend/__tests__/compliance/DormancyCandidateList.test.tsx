import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DormancyCandidateList } from "@/components/compliance/DormancyCandidateList";

const mockMarkDormant = vi.fn();

vi.mock("@/app/(app)/org/[slug]/compliance/actions", () => ({
  markCustomerDormant: (...args: unknown[]) => mockMarkDormant(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

describe("DormancyCandidateList", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows empty state when no candidates", () => {
    render(<DormancyCandidateList candidates={[]} orgSlug="test-org" />);
    expect(screen.getByText("No dormant customers detected.")).toBeInTheDocument();
  });

  it("renders rows with customer name and days since activity", () => {
    const candidates = [
      {
        customerId: "c1",
        customerName: "Acme Corp",
        lastActivityDate: "2025-01-01T00:00:00Z",
        daysSinceActivity: 45,
      },
      {
        customerId: "c2",
        customerName: "Beta Inc",
        lastActivityDate: null,
        daysSinceActivity: 120,
      },
    ];
    render(<DormancyCandidateList candidates={candidates} orgSlug="test-org" />);
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("45")).toBeInTheDocument();
    expect(screen.getByText("120")).toBeInTheDocument();
  });

  it("shows days since activity in red when >90", () => {
    const candidates = [
      {
        customerId: "c1",
        customerName: "Old Corp",
        lastActivityDate: null,
        daysSinceActivity: 100,
      },
    ];
    render(<DormancyCandidateList candidates={candidates} orgSlug="test-org" />);
    const daysEl = screen.getByText("100");
    expect(daysEl.className).toContain("text-red-600");
  });

  it("calls markCustomerDormant on button click", async () => {
    mockMarkDormant.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const candidates = [
      {
        customerId: "c1",
        customerName: "Mark Me Corp",
        lastActivityDate: null,
        daysSinceActivity: 95,
      },
    ];
    render(<DormancyCandidateList candidates={candidates} orgSlug="test-org" />);
    await user.click(screen.getByText("Mark as Dormant"));
    await waitFor(() => expect(mockMarkDormant).toHaveBeenCalledWith("c1", "test-org"));
  });
});
