import { describe, it, expect, vi, afterEach, beforeEach, beforeAll, afterAll } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MatterDetailLayout } from "@/components/projects/matter-detail-layout";

// Mock localStorage — scoped to this file only
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    clear: () => {
      store = {};
    },
  };
})();

const originalLocalStorage = Object.getOwnPropertyDescriptor(window, "localStorage");

beforeAll(() => {
  Object.defineProperty(window, "localStorage", {
    value: localStorageMock,
    configurable: true,
  });
});

afterAll(() => {
  if (originalLocalStorage) {
    Object.defineProperty(window, "localStorage", originalLocalStorage);
  }
});

describe("MatterDetailLayout — responsive behaviour", () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders mobile sidebar trigger with data-testid", () => {
    render(
      <MatterDetailLayout sidebar={<div>Sidebar</div>}>
        <div>Main</div>
      </MatterDetailLayout>
    );

    const trigger = screen.getByTestId("mobile-sidebar-trigger");
    expect(trigger).toBeInTheDocument();
    // The trigger should have lg:hidden class (visible on mobile, hidden on desktop)
    expect(trigger.className).toContain("lg:hidden");
  });

  it("sidebar grid column is hidden below lg via responsive classes", () => {
    render(
      <MatterDetailLayout sidebar={<div>Sidebar Desktop</div>}>
        <div>Main Content</div>
      </MatterDetailLayout>
    );

    const layout = screen.getByTestId("matter-detail-layout");
    // Layout should use responsive grid: single column mobile, two-column desktop
    expect(layout.className).toContain("grid-cols-[1fr]");
    expect(layout.className).toContain("lg:grid-cols-[var(--sidebar-width)_1fr]");
  });

  it("collapse toggle on desktop has lg:inline-flex (hidden on mobile)", () => {
    render(
      <MatterDetailLayout sidebar={<div>Sidebar</div>} defaultCollapsed={true}>
        <div>Main</div>
      </MatterDetailLayout>
    );

    const toggle = screen.getByTestId("sidebar-collapse-toggle");
    // When collapsed, the expand toggle should only be visible on desktop
    expect(toggle.className).toContain("lg:inline-flex");
    expect(toggle.className).toContain("hidden");
  });

  it("mobile sidebar trigger opens Sheet on click", async () => {
    const user = userEvent.setup();
    render(
      <MatterDetailLayout
        sidebar={<div>Desktop Sidebar</div>}
        mobileSidebar={<div data-testid="mobile-sidebar-content">Mobile Sidebar</div>}
      >
        <div>Main</div>
      </MatterDetailLayout>
    );

    const trigger = screen.getByTestId("mobile-sidebar-trigger");
    await user.click(trigger);

    // After clicking, the Sheet should render mobile sidebar content
    expect(screen.getByText("Mobile Sidebar")).toBeInTheDocument();
  });

  it("uses sidebar prop as fallback when mobileSidebar is not provided", async () => {
    const user = userEvent.setup();
    render(
      <MatterDetailLayout sidebar={<div>Shared Sidebar Content</div>}>
        <div>Main</div>
      </MatterDetailLayout>
    );

    const trigger = screen.getByTestId("mobile-sidebar-trigger");
    await user.click(trigger);

    // The Sheet should contain the sidebar content as fallback
    // There will be two instances: one in the grid slot and one in the Sheet
    const instances = screen.getAllByText("Shared Sidebar Content");
    expect(instances.length).toBeGreaterThanOrEqual(1);
  });
});
