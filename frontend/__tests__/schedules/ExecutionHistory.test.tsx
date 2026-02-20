import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ExecutionHistory } from "@/components/schedules/ExecutionHistory";
import type { ScheduleExecutionResponse } from "@/lib/api/schedules";

const EXECUTIONS: ScheduleExecutionResponse[] = [
  {
    id: "exec-1",
    projectId: "proj-1",
    projectName: "Acme Corp — Jan 2026",
    periodStart: "2026-01-01",
    periodEnd: "2026-01-31",
    executedAt: "2025-12-29T09:00:00Z",
  },
  {
    id: "exec-2",
    projectId: "proj-2",
    projectName: "Acme Corp — Feb 2026",
    periodStart: "2026-02-01",
    periodEnd: "2026-02-28",
    executedAt: "2026-01-29T09:00:00Z",
  },
];

describe("ExecutionHistory", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows empty state message when no executions", () => {
    render(<ExecutionHistory executions={[]} slug="acme" />);
    expect(
      screen.getByText(
        /No executions yet — projects will appear here after the first automated run/,
      ),
    ).toBeInTheDocument();
  });

  it("renders execution rows with project names", () => {
    render(<ExecutionHistory executions={EXECUTIONS} slug="acme" />);
    expect(screen.getByText("Acme Corp — Jan 2026")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp — Feb 2026")).toBeInTheDocument();
  });

  it("project name is a link to project detail", () => {
    render(<ExecutionHistory executions={EXECUTIONS} slug="acme" />);
    const link = screen.getByRole("link", { name: "Acme Corp — Jan 2026" });
    expect(link).toHaveAttribute("href", "/org/acme/projects/proj-1");
  });

  it("period dates are displayed", () => {
    render(<ExecutionHistory executions={EXECUTIONS} slug="acme" />);
    // formatDate("2026-01-01") => "Jan 1, 2026"
    expect(screen.getByText(/Jan 1, 2026/)).toBeInTheDocument();
  });
});
