import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { LifecycleStatusBadge } from "./LifecycleStatusBadge";

describe("LifecycleStatusBadge", () => {
  it("renders PROSPECT with neutral variant", () => {
    render(<LifecycleStatusBadge lifecycleStatus="PROSPECT" />);
    const badge = screen.getByText("Prospect");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders ONBOARDING with lead variant", () => {
    render(<LifecycleStatusBadge lifecycleStatus="ONBOARDING" />);
    const badge = screen.getByText("Onboarding");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "lead");
  });

  it("renders ACTIVE with success variant", () => {
    render(<LifecycleStatusBadge lifecycleStatus="ACTIVE" />);
    const badge = screen.getByText("Active");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders DORMANT with warning variant", () => {
    render(<LifecycleStatusBadge lifecycleStatus="DORMANT" />);
    const badge = screen.getByText("Dormant");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("renders OFFBOARDED with destructive variant", () => {
    render(<LifecycleStatusBadge lifecycleStatus="OFFBOARDED" />);
    const badge = screen.getByText("Offboarded");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });
});
