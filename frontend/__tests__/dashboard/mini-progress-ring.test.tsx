import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MiniProgressRing } from "@/components/dashboard/mini-progress-ring";

describe("MiniProgressRing", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders SVG at boundary values (0, 50, 100)", () => {
    const { unmount: u1 } = render(<MiniProgressRing value={0} size={48} />);
    expect(screen.getByLabelText("0%")).toBeInTheDocument();
    u1();

    const { unmount: u2 } = render(<MiniProgressRing value={50} size={48} />);
    expect(screen.getByLabelText("50%")).toBeInTheDocument();
    u2();

    render(<MiniProgressRing value={100} size={48} />);
    expect(screen.getByLabelText("100%")).toBeInTheDocument();
  });

  it("applies auto-color correctly based on value thresholds", () => {
    // value > 66 = green
    const { container: c1, unmount: u1 } = render(
      <MiniProgressRing value={80} />
    );
    const greenCircles = c1.querySelectorAll("circle");
    const greenArc = greenCircles[1];
    expect(greenArc.getAttribute("stroke")).toContain("green");
    u1();

    // value > 33 = amber
    const { container: c2, unmount: u2 } = render(
      <MiniProgressRing value={50} />
    );
    const amberCircles = c2.querySelectorAll("circle");
    const amberArc = amberCircles[1];
    expect(amberArc.getAttribute("stroke")).toContain("amber");
    u2();

    // value <= 33 = red
    const { container: c3 } = render(<MiniProgressRing value={20} />);
    const redCircles = c3.querySelectorAll("circle");
    const redArc = redCircles[1];
    expect(redArc.getAttribute("stroke")).toContain("red");
  });

  it("shows percentage text when size >= 40", () => {
    const { container } = render(<MiniProgressRing value={75} size={48} />);
    const text = container.querySelector("text");
    expect(text).toBeInTheDocument();
    expect(text!.textContent).toBe("75%");
  });

  it("hides center text when size < 40", () => {
    const { container } = render(<MiniProgressRing value={75} size={32} />);
    const text = container.querySelector("text");
    expect(text).not.toBeInTheDocument();
  });
});
