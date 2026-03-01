import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { TimeTrackingSettingsForm } from "@/components/settings/TimeTrackingSettingsForm";

const mockUpdateTimeTrackingSettings = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/settings/time-tracking/actions",
  () => ({
    updateTimeTrackingSettings: (...args: unknown[]) =>
      mockUpdateTimeTrackingSettings(...args),
  })
);

afterEach(() => {
  cleanup();
  mockUpdateTimeTrackingSettings.mockReset();
});

function renderForm(overrides: Partial<React.ComponentProps<typeof TimeTrackingSettingsForm>> = {}) {
  const defaultProps: React.ComponentProps<typeof TimeTrackingSettingsForm> = {
    slug: "acme",
    timeReminderEnabled: false,
    timeReminderDays: "MON,TUE,WED,THU,FRI",
    timeReminderTime: "17:00",
    timeReminderMinHours: 4.0,
    defaultExpenseMarkupPercent: null,
    ...overrides,
  };
  return render(<TimeTrackingSettingsForm {...defaultProps} />);
}

describe("TimeTrackingSettingsForm", () => {
  it("renders time reminder section with toggle, day checkboxes, time input, and min hours input", () => {
    renderForm();

    // Toggle
    expect(screen.getByLabelText("Enable time reminders")).toBeInTheDocument();

    // Day checkboxes
    expect(screen.getByLabelText("Mon")).toBeInTheDocument();
    expect(screen.getByLabelText("Tue")).toBeInTheDocument();
    expect(screen.getByLabelText("Wed")).toBeInTheDocument();
    expect(screen.getByLabelText("Thu")).toBeInTheDocument();
    expect(screen.getByLabelText("Fri")).toBeInTheDocument();
    expect(screen.getByLabelText("Sat")).toBeInTheDocument();
    expect(screen.getByLabelText("Sun")).toBeInTheDocument();

    // Time input
    expect(screen.getByLabelText("Reminder Time (UTC)")).toBeInTheDocument();

    // Min hours input
    expect(screen.getByLabelText("Minimum Hours")).toBeInTheDocument();

    // Helper text
    expect(
      screen.getByText(/Time is interpreted as UTC/)
    ).toBeInTheDocument();

    // Expense markup
    expect(
      screen.getByLabelText("Default Expense Markup (%)")
    ).toBeInTheDocument();
  });

  it("working day checkboxes default to weekdays (Mon-Fri checked, Sat-Sun unchecked)", () => {
    renderForm();

    // Weekdays should be checked
    const monCheckbox = screen.getByLabelText("Mon");
    const tueCheckbox = screen.getByLabelText("Tue");
    const wedCheckbox = screen.getByLabelText("Wed");
    const thuCheckbox = screen.getByLabelText("Thu");
    const friCheckbox = screen.getByLabelText("Fri");
    expect(monCheckbox).toHaveAttribute("data-state", "checked");
    expect(tueCheckbox).toHaveAttribute("data-state", "checked");
    expect(wedCheckbox).toHaveAttribute("data-state", "checked");
    expect(thuCheckbox).toHaveAttribute("data-state", "checked");
    expect(friCheckbox).toHaveAttribute("data-state", "checked");

    // Weekend should be unchecked
    const satCheckbox = screen.getByLabelText("Sat");
    const sunCheckbox = screen.getByLabelText("Sun");
    expect(satCheckbox).toHaveAttribute("data-state", "unchecked");
    expect(sunCheckbox).toHaveAttribute("data-state", "unchecked");
  });

  it("min hours converts to correct value on save", async () => {
    mockUpdateTimeTrackingSettings.mockResolvedValue({ success: true });
    renderForm({ timeReminderMinHours: 4.0 });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockUpdateTimeTrackingSettings).toHaveBeenCalledWith("acme", {
        timeReminderEnabled: false,
        timeReminderDays: "MON,TUE,WED,THU,FRI",
        timeReminderTime: "17:00",
        timeReminderMinHours: 4.0,
        defaultExpenseMarkupPercent: null,
      });
    });
  });
});
