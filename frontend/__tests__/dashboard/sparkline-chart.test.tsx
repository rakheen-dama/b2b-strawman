import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { SparklineChart } from "@/components/dashboard/sparkline-chart";

describe("SparklineChart", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SVG polyline with data points", () => {
    const { container } = render(
      <SparklineChart data={[10, 20, 15, 30, 25]} />
    );

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute("width", "80");
    expect(svg).toHaveAttribute("height", "24");

    const polyline = container.querySelector("polyline");
    expect(polyline).toBeInTheDocument();
    expect(polyline).toHaveAttribute("points");
    expect(polyline!.getAttribute("points")!.split(" ").length).toBe(5);

    // Gradient fill polygon should also be present
    const polygon = container.querySelector("polygon");
    expect(polygon).toBeInTheDocument();
  });

  it("handles empty data array gracefully", () => {
    const { container } = render(<SparklineChart data={[]} />);

    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();

    // No polyline or polygon when data is empty
    const polyline = container.querySelector("polyline");
    expect(polyline).not.toBeInTheDocument();
  });
});
