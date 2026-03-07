import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DefaultCapacitySettings } from "@/components/capacity/default-capacity-settings";

const mockUpdateCapacitySettings = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/settings/capacity/actions",
  () => ({
    updateCapacitySettings: (...args: unknown[]) =>
      mockUpdateCapacitySettings(...args),
  })
);

afterEach(() => {
  cleanup();
  mockUpdateCapacitySettings.mockReset();
});

function renderForm(
  overrides: Partial<React.ComponentProps<typeof DefaultCapacitySettings>> = {}
) {
  const defaultProps: React.ComponentProps<typeof DefaultCapacitySettings> = {
    slug: "acme",
    defaultWeeklyCapacityHours: 40,
    ...overrides,
  };
  return render(<DefaultCapacitySettings {...defaultProps} />);
}

describe("DefaultCapacitySettings", () => {
  it("renders the capacity form with default hours", () => {
    renderForm();
    expect(screen.getByText("Default Weekly Capacity")).toBeInTheDocument();
    expect(screen.getByLabelText("Hours per week")).toHaveValue(40);
  });

  it("calls save action with updated hours", async () => {
    mockUpdateCapacitySettings.mockResolvedValue({ success: true });
    renderForm({ defaultWeeklyCapacityHours: 40 });

    const input = screen.getByLabelText("Hours per week");
    fireEvent.change(input, { target: { value: "32" } });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockUpdateCapacitySettings).toHaveBeenCalledWith("acme", {
        defaultWeeklyCapacityHours: 32,
      });
    });

    expect(screen.getByText("Capacity settings updated.")).toBeInTheDocument();
  });

  it("shows error message on failure", async () => {
    mockUpdateCapacitySettings.mockResolvedValue({
      success: false,
      error: "Only admins and owners can update capacity settings.",
    });
    renderForm();

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Only admins and owners can update capacity settings.")
      ).toBeInTheDocument();
    });
  });

  it("shows error message on unexpected failure", async () => {
    mockUpdateCapacitySettings.mockRejectedValue(new Error("network error"));
    renderForm();

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(
        screen.getByText("An unexpected error occurred. Please try again.")
      ).toBeInTheDocument();
    });
  });
});
