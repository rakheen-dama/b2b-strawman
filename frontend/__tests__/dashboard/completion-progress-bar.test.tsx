import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";

describe("CompletionProgressBar", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders percentage label", () => {
    render(<CompletionProgressBar percent={75} />);
    expect(screen.getByText("75%")).toBeInTheDocument();
  });

  it("renders 0% for zero value", () => {
    render(<CompletionProgressBar percent={0} />);
    expect(screen.getByText("0%")).toBeInTheDocument();
  });

  it("renders 100% for full value", () => {
    render(<CompletionProgressBar percent={100} />);
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("clamps values above 100", () => {
    render(<CompletionProgressBar percent={150} />);
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("clamps values below 0", () => {
    render(<CompletionProgressBar percent={-10} />);
    expect(screen.getByText("0%")).toBeInTheDocument();
  });

  it("rounds non-integer percentages", () => {
    render(<CompletionProgressBar percent={33.7} />);
    expect(screen.getByText("34%")).toBeInTheDocument();
  });

  it("applies green color for percent > 66", () => {
    const { container } = render(<CompletionProgressBar percent={80} />);
    const bar = container.querySelector(".bg-green-500");
    expect(bar).toBeTruthy();
  });

  it("applies amber color for percent > 33 and <= 66", () => {
    const { container } = render(<CompletionProgressBar percent={50} />);
    const bar = container.querySelector(".bg-amber-500");
    expect(bar).toBeTruthy();
  });

  it("applies red color for percent <= 33", () => {
    const { container } = render(<CompletionProgressBar percent={20} />);
    const bar = container.querySelector(".bg-red-500");
    expect(bar).toBeTruthy();
  });
});
