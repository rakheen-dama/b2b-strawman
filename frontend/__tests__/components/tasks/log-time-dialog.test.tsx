import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LogTimeDialog } from "@/components/tasks/log-time-dialog";

const mockCreateTimeEntry = vi.fn();
const mockResolveRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  createTimeEntry: (...args: unknown[]) => mockCreateTimeEntry(...args),
  resolveRate: (...args: unknown[]) => mockResolveRate(...args),
}));

describe("LogTimeDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog and renders form fields", async () => {
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1">
        <button>Open Log Time Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Dialog"));

    expect(screen.getByText("Duration")).toBeInTheDocument();
    expect(screen.getByLabelText("Date")).toBeInTheDocument();
    expect(screen.getByText("Billable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log Time" })).toBeInTheDocument();
  });

  it("validates that duration must be greater than 0", async () => {
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1">
        <button>Open Log Time Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Dialog"));

    // Leave hours and minutes at 0 (default)
    await user.click(screen.getByRole("button", { name: "Log Time" }));

    await waitFor(() => {
      expect(
        screen.getByText("Duration must be greater than 0."),
      ).toBeInTheDocument();
    });

    // Server action should NOT have been called
    expect(mockCreateTimeEntry).not.toHaveBeenCalled();
  });

  it("calls createTimeEntry with correct data on valid submit", async () => {
    mockCreateTimeEntry.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1">
        <button>Open Log Time Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Dialog"));

    // There are two "0" placeholders: hours (first) and minutes (second)
    const inputs = screen.getAllByPlaceholderText("0");
    await user.clear(inputs[0]);
    await user.type(inputs[0], "1");
    await user.clear(inputs[1]);
    await user.type(inputs[1], "30");

    await user.click(screen.getByRole("button", { name: "Log Time" }));

    await waitFor(() => {
      expect(mockCreateTimeEntry).toHaveBeenCalledWith(
        "acme",
        "p1",
        "t1",
        expect.any(FormData),
      );
    });
  });

  it("resets form and closes dialog on successful submit", async () => {
    mockCreateTimeEntry.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1">
        <button>Open Log Time Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Dialog"));

    // Set hours to 2
    const inputs = screen.getAllByPlaceholderText("0");
    await user.clear(inputs[0]);
    await user.type(inputs[0], "2");

    await user.click(screen.getByRole("button", { name: "Log Time" }));

    await waitFor(() => {
      expect(mockCreateTimeEntry).toHaveBeenCalled();
    });

    // Dialog should close (title should no longer be visible)
    await waitFor(() => {
      expect(screen.queryByText("Log Time", { selector: "h2" })).not.toBeInTheDocument();
    });
  });

  it("shows rate warning banner when billable and no rate found", async () => {
    mockResolveRate.mockResolvedValue(null);
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1" memberId="m1">
        <button>Open Rate Warning Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Rate Warning Dialog"));

    // Wait for rate resolution (billable is true by default, memberId is provided)
    await waitFor(() => {
      expect(screen.getByTestId("rate-warning-banner")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/No rate card found for this combination/),
    ).toBeInTheDocument();
  });

  it("hides rate warning banner when rate exists", async () => {
    mockResolveRate.mockResolvedValue({
      hourlyRate: 150,
      currency: "USD",
      source: "MEMBER_DEFAULT",
    });
    const user = userEvent.setup();

    render(
      <LogTimeDialog slug="acme" projectId="p1" taskId="t1" memberId="m1">
        <button>Open Rate Exists Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Rate Exists Dialog"));

    await waitFor(() => {
      expect(screen.getByTestId("rate-preview")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("rate-warning-banner")).not.toBeInTheDocument();
  });
});
