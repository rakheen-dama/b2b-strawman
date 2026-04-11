import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { WeeklyRhythmStrip } from "@/components/dashboard/weekly-rhythm-strip";

describe("WeeklyRhythmStrip", () => {
  afterEach(() => {
    cleanup();
  });

  const defaultProps = {
    dailyHours: [6, 7, 8, 5, 4, 0, 0],
    dailyCapacity: 8,
    selectedDayIndex: null as number | null,
    onDaySelect: vi.fn(),
  };

  it("renders 7 day columns", () => {
    render(<WeeklyRhythmStrip {...defaultProps} />);

    for (let i = 0; i < 7; i++) {
      expect(screen.getByTestId(`rhythm-day-${i}`)).toBeInTheDocument();
    }
    expect(screen.getByTestId("weekly-rhythm-strip")).toBeInTheDocument();
  });

  it("highlights current day with ring class", () => {
    render(<WeeklyRhythmStrip {...defaultProps} />);

    // The current day should have the ring-teal-500 class
    const todayDayOfWeek = new Date().getDay();
    const todayIndex = todayDayOfWeek === 0 ? 6 : todayDayOfWeek - 1;

    const todayButton = screen.getByTestId(`rhythm-day-${todayIndex}`);
    expect(todayButton.className).toContain("ring-teal-500");
  });

  it("calls onDaySelect on click", () => {
    const onDaySelect = vi.fn();
    render(<WeeklyRhythmStrip {...defaultProps} onDaySelect={onDaySelect} />);

    fireEvent.click(screen.getByTestId("rhythm-day-2"));
    expect(onDaySelect).toHaveBeenCalledWith(2);
  });

  it("displays weekly total in font-mono", () => {
    render(<WeeklyRhythmStrip {...defaultProps} />);

    // Total = 6+7+8+5+4+0+0 = 30
    expect(screen.getByText("30.0h")).toBeInTheDocument();
  });
});
