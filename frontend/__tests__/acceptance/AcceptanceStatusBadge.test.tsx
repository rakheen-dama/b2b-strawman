import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { AcceptanceStatusBadge } from "@/components/acceptance/AcceptanceStatusBadge";

describe("AcceptanceStatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it('renders "Awaiting Acceptance" badge for SENT status', () => {
    render(<AcceptanceStatusBadge status="SENT" />);
    expect(screen.getByText("Awaiting Acceptance")).toBeInTheDocument();
  });

  it('renders "Viewed" badge for VIEWED status', () => {
    render(<AcceptanceStatusBadge status="VIEWED" />);
    expect(screen.getByText("Viewed")).toBeInTheDocument();
  });

  it('renders "Accepted" badge with check icon for ACCEPTED status', () => {
    render(<AcceptanceStatusBadge status="ACCEPTED" />);
    expect(screen.getByText("Accepted")).toBeInTheDocument();
    // CheckCircle2 icon is rendered as an SVG inside the badge
    const badge = screen.getByText("Accepted").closest("div, span");
    expect(badge?.querySelector("svg")).toBeInTheDocument();
  });

  it('renders "Expired" badge for EXPIRED status', () => {
    render(<AcceptanceStatusBadge status="EXPIRED" />);
    expect(screen.getByText("Expired")).toBeInTheDocument();
  });

  it('renders "Revoked" badge for REVOKED status', () => {
    render(<AcceptanceStatusBadge status="REVOKED" />);
    expect(screen.getByText("Revoked")).toBeInTheDocument();
  });

  it('renders "Declined" badge without check icon for DECLINED status', () => {
    render(<AcceptanceStatusBadge status="DECLINED" />);
    expect(screen.getByText("Declined")).toBeInTheDocument();
    const badge = screen.getByText("Declined").closest("div, span");
    expect(badge?.querySelector("svg")).not.toBeInTheDocument();
  });

  it('renders "Declined — {reason}" with the full reason as a title for DECLINED with a reason', () => {
    render(<AcceptanceStatusBadge status="DECLINED" declineReason="Price too high" />);
    const label = screen.getByText("Declined — Price too high");
    expect(label).toBeInTheDocument();
    const badge = label.closest("[data-slot='badge']");
    expect(badge).toHaveAttribute("title", "Declined — Price too high");
    expect(badge).toHaveAttribute("data-variant", "destructive");
    // Long reasons are truncated by CSS, never cut in the DOM.
    expect(label).toHaveClass("truncate");
  });

  it('renders plain "Declined" (destructive) when the reason is null or blank', () => {
    render(<AcceptanceStatusBadge status="DECLINED" declineReason="   " />);
    const badge = screen.getByText("Declined").closest("[data-slot='badge']");
    expect(badge).toHaveAttribute("data-variant", "destructive");
    expect(screen.queryByText(/Declined —/)).not.toBeInTheDocument();
  });

  it("ignores declineReason for non-DECLINED statuses", () => {
    render(<AcceptanceStatusBadge status="SENT" declineReason="stale" />);
    expect(screen.getByText("Awaiting Acceptance")).toBeInTheDocument();
    expect(screen.queryByText(/stale/)).not.toBeInTheDocument();
  });

  it('renders "Pending" badge for PENDING status', () => {
    render(<AcceptanceStatusBadge status="PENDING" />);
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });
});
