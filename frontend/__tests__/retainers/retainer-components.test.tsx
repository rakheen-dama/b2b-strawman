import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetainerStatusBadge } from "@/components/retainers/retainer-status-badge";
import { RetainerProgress } from "@/components/retainers/retainer-progress";

describe("RetainerStatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders correct variant for each status", () => {
    const { unmount: u1 } = render(<RetainerStatusBadge status="ACTIVE" />);
    expect(screen.getByText("Active")).toHaveAttribute(
      "data-variant",
      "success",
    );
    u1();

    const { unmount: u2 } = render(<RetainerStatusBadge status="PAUSED" />);
    expect(screen.getByText("Paused")).toHaveAttribute(
      "data-variant",
      "warning",
    );
    u2();

    render(<RetainerStatusBadge status="TERMINATED" />);
    expect(screen.getByText("Terminated")).toHaveAttribute(
      "data-variant",
      "neutral",
    );
  });
});

describe("RetainerProgress", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows correct percentage and color changes at thresholds", () => {
    // Green (< 80%)
    const { unmount: u1 } = render(
      <RetainerProgress
        type="HOUR_BANK"
        consumedHours={20}
        allocatedHours={40}
      />,
    );
    expect(screen.getByText("20.0 of 40.0 hrs")).toBeInTheDocument();
    expect(screen.getByTestId("progress-bar")).toHaveClass("bg-green-500");
    u1();

    // Amber (80-99%)
    const { unmount: u2 } = render(
      <RetainerProgress
        type="HOUR_BANK"
        consumedHours={35}
        allocatedHours={40}
      />,
    );
    expect(screen.getByTestId("progress-bar")).toHaveClass("bg-amber-500");
    u2();

    // Red (>= 100%)
    const { unmount: u3 } = render(
      <RetainerProgress
        type="HOUR_BANK"
        consumedHours={45}
        allocatedHours={40}
      />,
    );
    expect(screen.getByTestId("progress-bar")).toHaveClass("bg-red-500");
    u3();

    // FIXED_FEE shows consumed only, no bar
    render(
      <RetainerProgress
        type="FIXED_FEE"
        consumedHours={10}
        allocatedHours={null}
      />,
    );
    expect(screen.getByText("10.0 hrs consumed")).toBeInTheDocument();
    expect(screen.queryByTestId("progress-bar")).not.toBeInTheDocument();
  });
});
