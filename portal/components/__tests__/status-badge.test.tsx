import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { StatusBadge } from "@/components/status-badge";

describe("StatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders project status with a sentence-case label", () => {
    render(<StatusBadge status="ON_HOLD" variant="project" />);
    expect(screen.getByText("On hold")).toBeInTheDocument();
  });

  it("renders task status in sentence case with the colour classes", () => {
    const { container } = render(
      <StatusBadge status="IN_PROGRESS" variant="task" />,
    );
    expect(screen.getByText("In progress")).toBeInTheDocument();
    expect(screen.queryByText("IN PROGRESS")).toBeNull();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-blue-100");
  });

  it("renders OPEN and DONE task statuses in sentence case (G88-F20)", () => {
    render(<StatusBadge status="OPEN" variant="task" />);
    expect(screen.getByText("Open")).toBeInTheDocument();
    cleanup();
    render(<StatusBadge status="DONE" variant="task" />);
    expect(screen.getByText("Done")).toBeInTheDocument();
  });

  it("renders CLOSED project status with the muted slate variant", () => {
    const { container } = render(<StatusBadge status="CLOSED" />);
    expect(screen.getByText("Closed")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-slate-200");
    expect(badge?.className).toContain("text-slate-600");
  });

  it("renders ARCHIVED and COMPLETED project statuses in sentence case", () => {
    render(<StatusBadge status="ARCHIVED" />);
    expect(screen.getByText("Archived")).toBeInTheDocument();
    cleanup();
    render(<StatusBadge status="COMPLETED" />);
    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("humanises an unknown project status with neutral slate classes", () => {
    const { container } = render(<StatusBadge status="FOO_BAR" />);
    expect(screen.getByText("Foo bar")).toBeInTheDocument();
    const badge = container.querySelector("[data-slot='badge']");
    expect(badge?.className).toContain("bg-slate-100");
    expect(badge?.className).toContain("text-slate-600");
  });

  it("renders nothing (does not crash) when the status is missing", () => {
    const { container } = render(
      <StatusBadge status={undefined as unknown as string} />,
    );
    expect(container.querySelector("[data-slot='badge']")).toBeNull();
  });
});
