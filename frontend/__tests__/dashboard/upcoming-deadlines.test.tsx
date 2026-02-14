import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { UpcomingDeadlines } from "@/components/my-work/upcoming-deadlines";
import type { PersonalDeadline } from "@/lib/dashboard-types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

function futureDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

const mockDeadlines: PersonalDeadline[] = [
  {
    taskId: "t1",
    taskName: "Fix login bug",
    projectName: "Website Redesign",
    dueDate: futureDate(5),
  },
  {
    taskId: "t2",
    taskName: "Design review",
    projectName: "Mobile App",
    dueDate: futureDate(1),
  },
  {
    taskId: "t3",
    taskName: "API docs update",
    projectName: "API Migration",
    dueDate: futureDate(-2),
  },
];

describe("UpcomingDeadlines", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders deadline list with task names and project badges", () => {
    render(<UpcomingDeadlines deadlines={mockDeadlines} />);

    expect(screen.getByText("Upcoming Deadlines")).toBeInTheDocument();
    expect(screen.getByText("Fix login bug")).toBeInTheDocument();
    expect(screen.getByText("Design review")).toBeInTheDocument();
    expect(screen.getByText("API docs update")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();
  });

  it("shows Overdue label for past-due items and day count for upcoming", () => {
    render(<UpcomingDeadlines deadlines={mockDeadlines} />);

    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("5 days")).toBeInTheDocument();
    expect(screen.getByText("1 day")).toBeInTheDocument();
  });

  it("renders empty state when deadlines array is empty", () => {
    render(<UpcomingDeadlines deadlines={[]} />);

    expect(screen.getByText("Upcoming Deadlines")).toBeInTheDocument();
    expect(screen.getByText("No upcoming deadlines")).toBeInTheDocument();
  });

  it("renders error state when deadlines is null", () => {
    render(<UpcomingDeadlines deadlines={null} />);

    expect(screen.getByText("Upcoming Deadlines")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load deadlines. Please try again.")
    ).toBeInTheDocument();
  });
});
