import { describe, it, expect, afterEach } from "vitest";
import { renderToString } from "react-dom/server";
import { cleanup, render, screen } from "@testing-library/react";
import { ProgressRadial } from "@/components/charts/progress-radial";

afterEach(() => cleanup());

function arc(container: HTMLElement) {
  return container.querySelector('[data-slot="progress-radial-arc"]');
}

function track(container: HTMLElement) {
  return container.querySelector('[data-slot="progress-radial-track"]');
}

describe("ProgressRadial", () => {
  it("has an accessible name and an sr summary stating the percentage", () => {
    render(<ProgressRadial value={75} label="Task completion" />);

    const ring = screen.getByRole("img", { name: "Task completion" });
    expect(ring).toHaveAccessibleDescription("Task completion: 75%");
  });

  it("clamps the value to 0–100 and treats non-finite as 0", () => {
    const cases: Array<[number, string]> = [
      [150, "Ring A: 100%"],
      [-10, "Ring A: 0%"],
      [Number.NaN, "Ring A: 0%"],
      [33.6, "Ring A: 34%"],
    ];
    for (const [value, expected] of cases) {
      render(<ProgressRadial value={value} label="Ring A" />);
      expect(screen.getByRole("img", { name: "Ring A" })).toHaveAccessibleDescription(expected);
      cleanup();
    }
  });

  it("zero draws the neutral track only", () => {
    const { container } = render(<ProgressRadial value={0} label="Ring B" />);

    expect(screen.getByRole("img", { name: "Ring B" })).toHaveAttribute("data-empty", "true");
    expect(arc(container)).toBeNull();
    expect(track(container)).toHaveAttribute("stroke", "var(--muted)");
  });

  it("without a tone uses the accent on a neutral track, never a status colour", () => {
    const { container } = render(<ProgressRadial value={20} label="Ring C" />);

    expect(screen.getByRole("img", { name: "Ring C" })).not.toHaveAttribute("data-empty");
    expect(arc(container)).toHaveAttribute("stroke", "var(--chart-1)");
    expect(track(container)).toHaveAttribute("stroke", "var(--muted)");
  });

  it("a tone colours the arc with the status and the track with its soft step", () => {
    const { container } = render(<ProgressRadial value={90} label="Ring D" tone="danger" />);

    expect(arc(container)).toHaveAttribute("stroke", "var(--danger)");
    expect(track(container)).toHaveAttribute("stroke", "var(--danger-soft)");
  });

  it("shows the centre percentage only at size ≥ 40", () => {
    const { container, unmount } = render(<ProgressRadial value={75} label="Ring E" size={48} />);
    expect(container.querySelector("text")).toHaveTextContent("75%");
    unmount();

    const small = render(<ProgressRadial value={75} label="Ring E" size={32} />);
    expect(small.container.querySelector("text")).toBeNull();
  });

  it("renders no table toggle", () => {
    render(<ProgressRadial value={50} label="Ring F" />);

    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("renders on the server", () => {
    const html = renderToString(<ProgressRadial value={40} label="Ring G" />);

    expect(html).toContain('role="img"');
    expect(html).toContain("Ring G: 40%");
  });
});
