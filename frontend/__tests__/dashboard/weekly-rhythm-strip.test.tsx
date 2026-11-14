import { describe, it, expect, vi, afterEach } from "vitest";
import { todayInSA } from "@b2mash/shared/date";
import { parseIsoDate } from "@/app/(app)/org/[slug]/diary/diary-types";
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
    const todayDayOfWeek = parseIsoDate(todayInSA()).getDay();
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
  // Dark-mode contrast (Phase 90 debt D-1/D-2): theme tokens, not light-only slate
  // shades that render black-on-black or as bright slabs on the dark card.
  it("uses theme tokens for the total, day tracks, divider, hover and selected day", () => {
    render(<WeeklyRhythmStrip {...defaultProps} selectedDayIndex={0} />);

    const total = screen.getByText("30.0h");
    expect(total.className).toContain("text-foreground");
    expect(total.className).not.toContain("text-slate-900");
    expect(total.parentElement!.className).toContain("border-border");

    const day0 = screen.getByTestId("rhythm-day-0");
    expect(day0.className).toContain("hover:bg-muted");
    expect(day0.className).toContain("bg-teal-500/10");
    expect(day0.className).not.toMatch(/\bbg-teal-50\b|hover:bg-slate-50/);

    const track = day0.querySelector(".absolute");
    expect(track!.className).toContain("bg-muted");
    expect(track!.className).not.toContain("bg-slate-200");
  });
});
