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

  it('renders "Pending" badge for PENDING status', () => {
    render(<AcceptanceStatusBadge status="PENDING" />);
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });
});
