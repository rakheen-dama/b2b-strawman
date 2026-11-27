import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
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

function renderHeader() {
  return render(<MyWorkHeader slug="test-org" from="2026-07-01" to="2026-07-31" />);
}

describe("MyWorkHeader", () => {
  it("subtitle reads tasks (not action items) across all matters", () => {
    renderHeader();
    expect(screen.getByText("Your tasks and time tracking across all matters")).toBeInTheDocument();
  });

  it("renders exactly one h1 'My Work' through the kit PageHeader", () => {
    const { container } = renderHeader();
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "My Work" })).toBeInTheDocument();
    expect(container.querySelector('[data-slot="page-header"]')).toBeInTheDocument();
  });

  it("puts the Timesheet link in the header actions (K-96)", () => {
    const { container } = renderHeader();
    const actions = container.querySelector<HTMLElement>('[data-slot="page-header-actions"]');
    expect(actions).not.toBeNull();
    const link = within(actions!).getByRole("link", { name: "Timesheet" });
    expect(link).toHaveAttribute("href", "/org/test-org/my-work/timesheet");
    // The date range is not in the actions box (it squeezed the title to 80 px at 1024).
    expect(within(actions!).queryByRole("button", { name: "This Week" })).toBeNull();
  });

  it("puts the date range in the toolbar row under the title (K-96)", () => {
    const { container } = renderHeader();
    const toolbar = container.querySelector<HTMLElement>('[data-slot="page-header-toolbar"]');
    expect(toolbar).not.toBeNull();
    expect(within(toolbar!).getByRole("button", { name: "This Week" })).toBeInTheDocument();
    expect(within(toolbar!).getByRole("button", { name: "Refresh data" })).toBeInTheDocument();
  });
});
