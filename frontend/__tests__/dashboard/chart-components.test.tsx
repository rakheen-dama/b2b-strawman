import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MicroStackedBar } from "@/components/dashboard/micro-stacked-bar";

// OQ #23: 639B moved DonutChart/ChartTooltip onto the chart kit (__tests__/charts/*). 639D drops
// MicroStackedBar and, as the last of 637B/639B/639D to merge, deletes this file.

describe("MicroStackedBar", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders segments proportionally", () => {
    render(
      <MicroStackedBar
        segments={[
          { value: 30, color: "red", label: "A" },
          { value: 70, color: "blue", label: "B" },
        ]}
      />
    );

    const bar = screen.getByTestId("micro-stacked-bar");
    expect(bar).toBeInTheDocument();

    const segments = screen.getAllByTestId("segment");
    expect(segments).toHaveLength(2);
    expect(segments[0]).toHaveAttribute("title", "A: 30");
    expect(segments[1]).toHaveAttribute("title", "B: 70");
  });

  it("handles single segment", () => {
    render(<MicroStackedBar segments={[{ value: 100, color: "green", label: "Only" }]} />);

    const segments = screen.getAllByTestId("segment");
    expect(segments).toHaveLength(1);
    expect(segments[0]).toHaveAttribute("title", "Only: 100");
  });
});
