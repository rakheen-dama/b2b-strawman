import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/calendar",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/app/(app)/org/[slug]/calendar/calendar-actions", () => ({
  getCalendarItems: vi.fn(() => Promise.resolve({ items: [], overdueCount: 0 })),
  getCalendarProjects: vi.fn(() => Promise.resolve([])),
  getCalendarMembers: vi.fn(() => Promise.resolve([])),
}));

import { CalendarPageClient } from "@/app/(app)/org/[slug]/calendar/calendar-page-client";

afterEach(() => {
  cleanup();
});

describe("CalendarPageClient — literal legal filter labels (Phase 87 611C, ADR-338)", () => {
  it("renders 'All Matters' and the Tasks / Matters type chips", async () => {
    render(
      <CalendarPageClient
        initialItems={[]}
        initialOverdueCount={0}
        initialYear={2026}
        initialMonth={7}
        slug="test-org"
      />
    );
    expect((await screen.findAllByText("All Matters")).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Tasks" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Matters" })).toBeInTheDocument();
    expect(screen.queryByText("Action Items")).toBeNull();
    expect(screen.queryByText("All Projects")).toBeNull();
  });
});
