import { describe, it, expect, vi, afterEach, beforeEach, beforeAll, afterAll } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MatterDetailLayout } from "@/components/projects/matter-detail-layout";
import { SidebarCollapseToggle } from "@/components/projects/sidebar-collapse-toggle";
import { ExpandableText } from "@/components/ui/expandable-text";

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

describe("MatterDetailLayout", () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders sidebar and main slots", () => {
    render(
      <MatterDetailLayout sidebar={<div>Sidebar Content</div>}>
        <div>Main Content</div>
      </MatterDetailLayout>
    );

    expect(screen.getByText("Sidebar Content")).toBeInTheDocument();
    expect(screen.getByText("Main Content")).toBeInTheDocument();
    expect(screen.getByTestId("matter-detail-layout")).toBeInTheDocument();
  });

  it("respects defaultCollapsed", () => {
    render(
      <MatterDetailLayout
        sidebar={<div>Sidebar</div>}
        defaultCollapsed={true}
      >
        <div>Main</div>
      </MatterDetailLayout>
    );

    // defaultCollapsed={true} sets initial state to true; useEffect finds nothing
    // in localStorage so collapsed stays true — assertion is synchronous
    const layout = screen.getByTestId("matter-detail-layout");
    expect(layout.className).toContain("grid-cols-[0_1fr]");

    // When collapsed, the expand toggle should be visible in the main area
    const toggle = screen.getByTestId("sidebar-collapse-toggle");
    expect(toggle).toHaveAttribute("aria-label", "Expand sidebar");
  });

  it("toggles sidebar and persists to localStorage", async () => {
    const user = userEvent.setup();
    render(
      <MatterDetailLayout sidebar={<div>Sidebar</div>}>
        <div>Main</div>
      </MatterDetailLayout>
    );

    // Initially expanded (defaultCollapsed defaults to false)
    const layout = screen.getByTestId("matter-detail-layout");
    expect(layout.className).toContain("grid-cols-[var(--sidebar-width)_1fr]");

    // Click collapse toggle
    const toggle = screen.getByTestId("sidebar-collapse-toggle");
    expect(toggle).toHaveAttribute("aria-label", "Collapse sidebar");
    await user.click(toggle);

    // State propagation may be async — wrap in waitFor
    await waitFor(() => {
      expect(layout.className).toContain("grid-cols-[0_1fr]");
      expect(localStorageMock.setItem).toHaveBeenCalledWith(
        "kazi-matter-sidebar-collapsed",
        "true"
      );
    });
  });
});

describe("SidebarCollapseToggle", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders correct icon for expanded state", () => {
    const onToggle = vi.fn();
    render(<SidebarCollapseToggle collapsed={false} onToggle={onToggle} />);

    const toggle = screen.getByTestId("sidebar-collapse-toggle");
    expect(toggle).toHaveAttribute("aria-label", "Collapse sidebar");
  });

  it("renders correct icon for collapsed state", () => {
    const onToggle = vi.fn();
    render(<SidebarCollapseToggle collapsed={true} onToggle={onToggle} />);

    const toggle = screen.getByTestId("sidebar-collapse-toggle");
    expect(toggle).toHaveAttribute("aria-label", "Expand sidebar");
  });
});

describe("ExpandableText", () => {
  afterEach(() => {
    cleanup();
  });

  it("truncates long text", () => {
    const longText =
      "This is a very long text that should be truncated by the expandable text component when it exceeds the line clamp limit set by the props.";
    render(<ExpandableText text={longText} />);

    const container = screen.getByTestId("expandable-text");
    expect(container).toBeInTheDocument();

    // The paragraph should have the CSS clamp style applied
    const paragraph = container.querySelector("p");
    expect(paragraph).not.toBeNull();
    expect(paragraph!.getAttribute("style")).toContain("overflow: hidden");
  });

  it('shows "Show more" toggle and toggles text', async () => {
    const user = userEvent.setup();
    const longText =
      "This is a very long text that should be truncated by the expandable text component when it exceeds the line clamp limit.";
    render(<ExpandableText text={longText} />);

    const toggle = screen.getByTestId("expandable-text-toggle");
    expect(toggle).toHaveTextContent("Show more");

    await user.click(toggle);
    expect(toggle).toHaveTextContent("Show less");

    // The paragraph should no longer have the clamp styles
    const paragraph = screen.getByTestId("expandable-text").querySelector("p");
    const style = paragraph!.getAttribute("style") ?? "";
    expect(style).not.toContain("overflow: hidden");
  });
});
