import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { HealthBadge } from "@/components/dashboard/health-badge";

describe("HealthBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders green dot for HEALTHY status at sm size", () => {
    render(<HealthBadge status="HEALTHY" size="sm" />);
    const dot = screen.getByLabelText("Healthy");
    expect(dot).toBeInTheDocument();
    expect(dot.className).toContain("bg-green-500");
    expect(dot.className).toContain("size-2");
    expect(dot.className).toContain("rounded-full");
  });

  it("renders amber dot with text for AT_RISK at md size", () => {
    render(<HealthBadge status="AT_RISK" size="md" />);
    expect(screen.getByText("At Risk")).toBeInTheDocument();

    // The dot is aria-hidden, find it by class
    const wrapper = screen.getByText("At Risk").parentElement!;
    const dot = wrapper.querySelector("[aria-hidden]");
    expect(dot!.className).toContain("bg-amber-500");
  });

  it("renders reasons list at lg size", () => {
    const reasons = ["Behind schedule", "Over budget"];
    render(<HealthBadge status="CRITICAL" size="lg" reasons={reasons} />);

    expect(screen.getByText("Critical")).toBeInTheDocument();
    expect(screen.getByText("Behind schedule")).toBeInTheDocument();
    expect(screen.getByText("Over budget")).toBeInTheDocument();
  });

  it("shows tooltip/title on sm size when reasons provided", () => {
    const reasons = ["Low velocity", "Missing milestones"];
    render(
      <HealthBadge status="AT_RISK" size="sm" reasons={reasons} />
    );

    const dot = screen.getByLabelText("At Risk");
    expect(dot).toHaveAttribute("title", "Low velocity; Missing milestones");
  });
});
