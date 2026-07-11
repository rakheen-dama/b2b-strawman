import React from "react";
import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TriageBadges } from "../triage-badges";

afterEach(() => {
  cleanup();
});

describe("<TriageBadges>", () => {
  it("renders human labels with per-signal variants", () => {
    render(<TriageBadges signals={["DRIFTING", "SERIAL_LATE", "GONE_QUIET", "ESCALATED"]} />);
    expect(screen.getByText("Drifting")).toBeInTheDocument();
    expect(screen.getByText("Serial late")).toBeInTheDocument();
    expect(screen.getByText("Gone quiet")).toBeInTheDocument();
    expect(screen.getByText("Escalated")).toBeInTheDocument();
    // Raw enum names must not leak into the UI.
    expect(screen.queryByText("SERIAL_LATE")).not.toBeInTheDocument();
    expect(screen.getByTestId("triage-badge-DRIFTING")).toHaveAttribute("data-variant", "warning");
    expect(screen.getByTestId("triage-badge-SERIAL_LATE")).toHaveAttribute(
      "data-variant",
      "neutral"
    );
    expect(screen.getByTestId("triage-badge-GONE_QUIET")).toHaveAttribute(
      "data-variant",
      "warning"
    );
    expect(screen.getByTestId("triage-badge-ESCALATED")).toHaveAttribute(
      "data-variant",
      "destructive"
    );
  });

  it("styles TRUST_FUNDS_AVAILABLE as informational teal (lead), not destructive", () => {
    render(
      <TriageBadges
        signals={["TRUST_FUNDS_AVAILABLE"]}
        signalDetails={{ TRUST_FUNDS_AVAILABLE: "R 84 200,00 held in trust" }}
      />
    );
    const badge = screen.getByTestId("triage-badge-TRUST_FUNDS_AVAILABLE");
    expect(badge).toHaveAttribute("data-variant", "lead");
    expect(screen.getByText("Trust funds available")).toBeInTheDocument();
    // Tooltip content is portalled and hover-only — assert the cursor-help trigger
    // wraps the badge (actor-display precedent: no hover simulation in happy-dom).
    const trigger = badge.closest("span.cursor-help");
    expect(trigger).not.toBeNull();
    // Keyboard users must be able to reach the ADR-329 detail — the trigger is focusable.
    expect(trigger).toHaveAttribute("tabindex", "0");
  });

  it("humanizes unknown advisor signals and defaults to the warning variant", () => {
    render(<TriageBadges signals={["SARS_REFUND_PENDING"]} />);
    expect(screen.getByText("Sars refund pending")).toBeInTheDocument();
    expect(screen.getByTestId("triage-badge-SARS_REFUND_PENDING")).toHaveAttribute(
      "data-variant",
      "warning"
    );
  });

  it("renders nothing for an empty signal list", () => {
    const { container } = render(<TriageBadges signals={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
