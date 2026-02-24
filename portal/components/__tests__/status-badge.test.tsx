import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { StatusBadge } from "@/components/status-badge";

describe("StatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders project status with formatted text", () => {
    render(<StatusBadge status="ON_HOLD" variant="project" />);
    expect(screen.getByText("ON HOLD")).toBeInTheDocument();
  });

  it("renders task status with correct color classes", () => {
    const { container } = render(
      <StatusBadge status="IN_PROGRESS" variant="task" />,
    );
    expect(screen.getByText("IN PROGRESS")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-blue-100");
  });
});
