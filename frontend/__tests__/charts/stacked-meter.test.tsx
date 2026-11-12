import { describe, it, expect, afterEach } from "vitest";
import { renderToString } from "react-dom/server";
import { cleanup, render, screen } from "@testing-library/react";
import { StackedMeter, type StackedMeterSegment } from "@/components/charts/stacked-meter";

afterEach(() => cleanup());

const AGED: StackedMeterSegment[] = [
  { key: "current", label: "Current", value: 2000, valueText: "R 2 000" },
  { key: "d30", label: "30 days", value: 3000, valueText: "R 3 000" },
  { key: "d60", label: "60 days", value: 5000, valueText: "R 5 000" },
  { key: "d90plus", label: "90+ days", value: 0, valueText: "R 0" },
];

function drawn(container: HTMLElement) {
  return Array.from(container.querySelectorAll("rect[data-segment]"));
}

describe("StackedMeter", () => {
  it("has an accessible name and an sr summary stating every segment, zero ones included", () => {
    render(<StackedMeter segments={AGED} palette="ordinal-aging" label="Aged fees" />);

    expect(screen.getByRole("img", { name: "Aged fees" })).toHaveAccessibleDescription(
      "Current R 2 000, 30 days R 3 000, 60 days R 5 000, 90+ days R 0"
    );
  });

  it("ordinal-aging draws the one-hue aging ramp in bucket order, never status colours", () => {
    const { container } = render(
      <StackedMeter
        segments={AGED.map((s) => ({ ...s, value: 1 }))}
        palette="ordinal-aging"
        label="Aged ramp"
      />
    );

    const fills = drawn(container).map((r) => r.getAttribute("fill"));
    expect(fills).toEqual([
      "var(--aging-current)",
      "var(--aging-d30)",
      "var(--aging-d60)",
      "var(--aging-d90plus)",
    ]);
    expect(container.innerHTML).not.toMatch(/var\(--(success|warning|danger)/);
  });

  it("a zero segment draws nothing and the others keep their slot colours", () => {
    const { container } = render(
      <StackedMeter
        segments={[{ ...AGED[0], value: 0 }, AGED[1], AGED[2]]}
        palette="ordinal-aging"
        label="Aged skip"
      />
    );

    const rects = drawn(container);
    expect(rects.map((r) => r.getAttribute("data-segment"))).toEqual(["d30", "d60"]);
    expect(rects.map((r) => r.getAttribute("fill"))).toEqual([
      "var(--aging-d30)",
      "var(--aging-d60)",
    ]);
  });

  it("categorical takes the chart slots in order", () => {
    const { container } = render(
      <StackedMeter
        segments={[
          { key: "billable", label: "Billable", value: 6, valueText: "6h" },
          { key: "non", label: "Non-billable", value: 2, valueText: "2h" },
        ]}
        palette="categorical"
        label="Billable split"
      />
    );

    expect(drawn(container).map((r) => r.getAttribute("fill"))).toEqual([
      "var(--chart-1)",
      "var(--chart-2)",
    ]);
  });

  it("widths are proportional, with one surface gap between each pair of touching parts", () => {
    const { container } = render(
      <StackedMeter
        segments={[
          { key: "a", label: "Part A", value: 30, valueText: "30" },
          { key: "b", label: "Part B", value: 70, valueText: "70" },
        ]}
        palette="categorical"
        label="Proportions"
      />
    );

    const [a, b] = drawn(container);
    expect(a).toHaveAttribute("x", "0%");
    expect(a).toHaveAttribute("width", "30%");
    expect(b).toHaveAttribute("x", "30%");
    expect(b).toHaveAttribute("width", "70%");
    expect(container.querySelectorAll('[data-slot="stacked-meter-gap"]')).toHaveLength(1);
  });

  it("all-zero, negative, non-finite or no segments render the neutral track only", () => {
    const cases: StackedMeterSegment[][] = [
      AGED.map((s) => ({ ...s, value: 0 })),
      [
        { key: "neg", label: "Negative", value: -5, valueText: "-5" },
        { key: "nan", label: "Unknown", value: Number.NaN, valueText: "—" },
      ],
      [],
    ];
    for (const segments of cases) {
      const { container } = render(
        <StackedMeter segments={segments} palette="ordinal-aging" label="Empty meter" />
      );
      expect(screen.getByRole("img", { name: "Empty meter" })).toHaveAttribute(
        "data-empty",
        "true"
      );
      expect(drawn(container)).toHaveLength(0);
      expect(container.querySelector('[data-slot="stacked-meter-track"]')).toHaveAttribute(
        "fill",
        "var(--muted)"
      );
      cleanup();
    }
  });

  it("renders no table toggle", () => {
    render(<StackedMeter segments={AGED} palette="ordinal-aging" label="No toggle meter" />);

    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("renders on the server", () => {
    const html = renderToString(
      <StackedMeter segments={AGED} palette="ordinal-aging" label="Server meter" />
    );

    expect(html).toContain('role="img"');
    expect(html).toContain("Current R 2 000, 30 days R 3 000");
  });
});
