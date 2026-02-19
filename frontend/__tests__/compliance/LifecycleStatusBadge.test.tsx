import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LifecycleStatusBadge } from "@/components/compliance/LifecycleStatusBadge";

describe("LifecycleStatusBadge", () => {
  afterEach(() => cleanup());

  it("renders 'Active' with success variant", () => {
    render(<LifecycleStatusBadge status="ACTIVE" />);
    const badge = screen.getByText("Active");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("success");
  });

  it("renders 'Prospect' with neutral variant", () => {
    render(<LifecycleStatusBadge status="PROSPECT" />);
    const badge = screen.getByText("Prospect");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("neutral");
  });

  it("renders 'Offboarded' with destructive variant", () => {
    render(<LifecycleStatusBadge status="OFFBOARDED" />);
    const badge = screen.getByText("Offboarded");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("destructive");
  });

  it("renders 'Dormant' with warning variant", () => {
    render(<LifecycleStatusBadge status="DORMANT" />);
    const badge = screen.getByText("Dormant");
    expect(badge).toBeInTheDocument();
    expect(badge.getAttribute("data-variant")).toBe("warning");
  });

  it("renders 'Onboarding' as blue badge (neutral variant with custom class)", () => {
    render(<LifecycleStatusBadge status="ONBOARDING" />);
    const badge = screen.getByText("Onboarding");
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain("bg-blue-100");
  });

  it("falls back to the raw status string for unknown status", () => {
    render(<LifecycleStatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText("UNKNOWN_STATUS")).toBeInTheDocument();
  });
});
