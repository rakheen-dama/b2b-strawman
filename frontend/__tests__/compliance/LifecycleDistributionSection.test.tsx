import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LifecycleDistributionSection } from "@/components/compliance/LifecycleDistributionSection";

describe("LifecycleDistributionSection", () => {
  afterEach(() => cleanup());

  const counts = {
    PROSPECT: 3,
    ONBOARDING: 5,
    ACTIVE: 12,
    DORMANT: 2,
    OFFBOARDED: 1,
  };

  it("renders five stat cards", () => {
    render(<LifecycleDistributionSection counts={counts} orgSlug="test-org" />);
    expect(screen.getByText("Prospect")).toBeInTheDocument();
    expect(screen.getByText("Onboarding")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Dormant")).toBeInTheDocument();
    expect(screen.getByText("Offboarded")).toBeInTheDocument();
  });

  it("displays correct counts for each status", () => {
    render(<LifecycleDistributionSection counts={counts} orgSlug="test-org" />);
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("renders zero when a status is missing from counts", () => {
    render(<LifecycleDistributionSection counts={{ ACTIVE: 7 }} orgSlug="test-org" />);
    expect(screen.getByText("7")).toBeInTheDocument();
    // Other statuses should show 0
    const zeros = screen.getAllByText("0");
    expect(zeros.length).toBe(4);
  });

  it("each card links to the correct filtered customer list", () => {
    render(<LifecycleDistributionSection counts={counts} orgSlug="test-org" />);
    const links = screen.getAllByRole("link");
    expect(links.length).toBe(5);
    expect(links[0]).toHaveAttribute(
      "href",
      "/org/test-org/customers?lifecycleStatus=PROSPECT",
    );
    expect(links[1]).toHaveAttribute(
      "href",
      "/org/test-org/customers?lifecycleStatus=ONBOARDING",
    );
    expect(links[2]).toHaveAttribute(
      "href",
      "/org/test-org/customers?lifecycleStatus=ACTIVE",
    );
    expect(links[3]).toHaveAttribute(
      "href",
      "/org/test-org/customers?lifecycleStatus=DORMANT",
    );
    expect(links[4]).toHaveAttribute(
      "href",
      "/org/test-org/customers?lifecycleStatus=OFFBOARDED",
    );
  });
});
