import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

import { StickyActionBar } from "@/components/ui/sticky-action-bar";

describe("StickyActionBar", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders children", () => {
    render(
      <StickyActionBar>
        <button type="button">Accept</button>
        <button type="button">Decline</button>
      </StickyActionBar>,
    );

    expect(screen.getByRole("button", { name: "Accept" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Decline" })).toBeInTheDocument();
  });

  it("is hidden on desktop via md:hidden class", () => {
    render(
      <StickyActionBar>
        <button type="button">Accept</button>
      </StickyActionBar>,
    );

    const bar = screen.getByTestId("sticky-action-bar");
    expect(bar.className).toContain("md:hidden");
  });

  it("applies fixed/bottom positioning classes for mobile viewport", () => {
    render(
      <StickyActionBar>
        <span>child</span>
      </StickyActionBar>,
    );

    const bar = screen.getByTestId("sticky-action-bar");
    expect(bar.className).toContain("fixed");
    expect(bar.className).toContain("bottom-0");
    expect(bar.className).toContain("inset-x-0");
  });

  it("includes safe-area inset padding for iOS", () => {
    render(
      <StickyActionBar>
        <span>child</span>
      </StickyActionBar>,
    );

    const bar = screen.getByTestId("sticky-action-bar");
    expect(bar.className).toContain("safe-area-inset-bottom");
  });

  it("merges additional className prop", () => {
    render(
      <StickyActionBar className="custom-extra-class">
        <span>child</span>
      </StickyActionBar>,
    );

    const bar = screen.getByTestId("sticky-action-bar");
    expect(bar.className).toContain("custom-extra-class");
    // Still has the base classes
    expect(bar.className).toContain("md:hidden");
  });
});
