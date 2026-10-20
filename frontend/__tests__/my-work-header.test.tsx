import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MyWorkHeader } from "@/app/(app)/org/[slug]/my-work/my-work-header";

// DateRangeSelector uses the app router.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  cleanup();
});

describe("MyWorkHeader", () => {
  it("subtitle reads tasks (not action items) across all matters", () => {
    render(<MyWorkHeader from="2026-07-01" to="2026-07-31" />);
    expect(screen.getByText("Your tasks and time tracking across all matters")).toBeInTheDocument();
  });
});
