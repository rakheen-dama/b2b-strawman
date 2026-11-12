import { describe, it, expect, afterEach } from "vitest";
import { renderToString } from "react-dom/server";
import { cleanup, render, screen } from "@testing-library/react";
import { SparklineTrend } from "@/components/charts/sparkline-trend";

afterEach(() => cleanup());

const SUMMARY = "Six weeks of hours: first 12.0h, last 18.5h, peak 22.0h in week 4.";

function linePath(container: HTMLElement) {
  return container.querySelector('[data-slot="sparkline-trend-line"]');
}

describe("SparklineTrend", () => {
  it("has an accessible name and states the caller's summary verbatim", () => {
    render(<SparklineTrend values={[12, 15, 14, 22, 20, 18.5]} label="Hours" summary={SUMMARY} />);

    expect(screen.getByRole("img", { name: "Hours" })).toHaveAccessibleDescription(SUMMARY);
  });

  it("no values render the neutral track, no series", () => {
    const { container } = render(
      <SparklineTrend values={[]} label="No hours" summary="No hours logged." />
    );

    expect(screen.getByRole("img", { name: "No hours" })).toHaveAttribute("data-empty", "true");
    expect(linePath(container)).toBeNull();
    expect(container.querySelector('[data-slot="sparkline-trend-track"]')).toHaveAttribute(
      "stroke",
      "var(--border)"
    );
  });

  it("one value or a flat series draws a flat line with finite coordinates", () => {
    for (const values of [[5], [3, 3, 3], [4, Number.NaN, Number.POSITIVE_INFINITY]]) {
      const { container } = render(
        <SparklineTrend values={values} label="Flat" summary="Flat series." />
      );
      const d = linePath(container)?.getAttribute("d") ?? "";
      expect(d).toMatch(/^M/);
      expect(d).not.toMatch(/NaN|Infinity/);
      cleanup();
    }
  });

  it("draws the peak highest and the series in the accent colour", () => {
    const { container } = render(
      <SparklineTrend values={[2, 9, 4]} label="Peak" summary="Peak 9." />
    );

    const line = linePath(container)!;
    expect(line).toHaveAttribute("stroke", "var(--chart-1)");
    const ys = (line.getAttribute("d") ?? "")
      .split(" ")
      .map((cmd) => Number(cmd.slice(1).split(",")[1]));
    expect(Math.min(...ys)).toBe(ys[1]);
  });

  it("renders no table toggle", () => {
    render(<SparklineTrend values={[1, 2]} label="No toggle" summary="Two points." />);

    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("renders on the server", () => {
    const html = renderToString(
      <SparklineTrend values={[1, 3, 2]} label="Server spark" summary="Server summary." />
    );

    expect(html).toContain('role="img"');
    expect(html).toContain("Server summary.");
  });
});
