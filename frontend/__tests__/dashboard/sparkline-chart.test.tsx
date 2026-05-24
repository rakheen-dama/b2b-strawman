import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { SparklineChart } from "@/components/dashboard/sparkline-chart";

describe("SparklineChart", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SVG path with data points", () => {
    const { container } = render(<SparklineChart data={[10, 20, 15, 30, 25]} />);

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute("width", "80");
    expect(svg).toHaveAttribute("height", "28");

    // Smooth curve rendered as <path> elements (fill path + line path)
    const paths = container.querySelectorAll("path");
    expect(paths.length).toBeGreaterThanOrEqual(2);

    // Line path should have no fill and a stroke
    const linePath = Array.from(paths).find((p) => p.getAttribute("fill") === "none");
    expect(linePath).toBeTruthy();
    expect(linePath!.getAttribute("d")).toBeTruthy();
  });

  it("handles empty data array gracefully", () => {
    const { container } = render(<SparklineChart data={[]} />);

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();

    // No path elements when data is empty
    const paths = container.querySelectorAll("path");
    expect(paths.length).toBe(0);
  });
});
