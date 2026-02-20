import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ScheduleList } from "@/components/schedules/ScheduleList";

vi.mock("@/app/(app)/org/[slug]/schedules/actions", () => ({
  pauseScheduleAction: vi.fn(),
  resumeScheduleAction: vi.fn(),
  deleteScheduleAction: vi.fn(),
  createScheduleAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

describe("Schedules Page", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no schedules", () => {
    render(<ScheduleList slug="acme" schedules={[]} />);
    expect(screen.getByText(/no.*schedules found/i)).toBeInTheDocument();
  });

  it("renders schedule list heading tabs", () => {
    render(<ScheduleList slug="acme" schedules={[]} />);
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Paused")).toBeInTheDocument();
    expect(screen.getByText("Completed")).toBeInTheDocument();
    expect(screen.getByText("All")).toBeInTheDocument();
  });
});
