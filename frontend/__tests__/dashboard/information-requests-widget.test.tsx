import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InformationRequestsWidget } from "@/components/dashboard/information-requests-widget";
import type { InformationRequestSummary } from "@/lib/api/information-requests";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/dashboard",
}));

const baseSummary: InformationRequestSummary = {
  totalRequests: 20,
  draftCount: 2,
  sentCount: 5,
  inProgressCount: 6,
  completedCount: 5,
  cancelledCount: 2,
  itemsPendingReview: 8,
  overdueRequests: 3,
  completionRateLast30Days: 0.72,
};

describe("InformationRequestsWidget", () => {
  afterEach(() => {
    cleanup();
    mockPush.mockReset();
  });

  it("renders summary data with pending review, overdue count, and completion rate", () => {
    render(
      <InformationRequestsWidget data={baseSummary} orgSlug="acme" />,
    );

    expect(screen.getByText("Information Requests")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument(); // pending review
    expect(screen.getByText("72%")).toBeInTheDocument(); // completion rate
    expect(screen.getByText("Pending Review")).toBeInTheDocument();
    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("navigates to customers page on click-through", async () => {
    const user = userEvent.setup();
    render(
      <InformationRequestsWidget data={baseSummary} orgSlug="acme" />,
    );

    const link = screen.getByRole("button", {
      name: /view customer requests/i,
    });
    await user.click(link);

    expect(mockPush).toHaveBeenCalledWith("/org/acme/customers");
  });

  it("shows overdue warning indicator when overdue count is positive", () => {
    render(
      <InformationRequestsWidget data={baseSummary} orgSlug="acme" />,
    );

    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText(/requests are overdue/)).toBeInTheDocument();
  });

  it("handles null data state gracefully", () => {
    render(
      <InformationRequestsWidget data={null} orgSlug="acme" />,
    );

    expect(screen.getByText("Information Requests")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load request data."),
    ).toBeInTheDocument();
  });

  it("falls back to count-based metrics when dashboard fields are absent", () => {
    const summaryWithoutDashboardFields: InformationRequestSummary = {
      totalRequests: 10,
      draftCount: 1,
      sentCount: 3,
      inProgressCount: 2,
      completedCount: 4,
      cancelledCount: 0,
    };

    render(
      <InformationRequestsWidget
        data={summaryWithoutDashboardFields}
        orgSlug="acme"
      />,
    );

    // Fallback: sentCount + inProgressCount = 5
    expect(screen.getByText("5")).toBeInTheDocument();
    // Fallback: completedCount / totalRequests = 40%
    expect(screen.getByText("40%")).toBeInTheDocument();
  });
});
