import { describe, it, expect, afterEach, vi, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetentionCard } from "@/components/legal/retention-card";

describe("RetentionCard (GAP-OBS-Day60-RetentionShape)", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("renders nothing when status is not CLOSED", () => {
    const { container } = render(
      <RetentionCard
        status="ACTIVE"
        retentionClockStartedAt="2026-01-15T10:00:00Z"
        retentionEndsOn="2031-01-15"
        slug="acme"
      />
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("retention-card")).not.toBeInTheDocument();
  });

  it("renders nothing when retentionClockStartedAt is null", () => {
    const { container } = render(
      <RetentionCard
        status="CLOSED"
        retentionClockStartedAt={null}
        retentionEndsOn="2031-01-15"
        slug="acme"
      />
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("retention-card")).not.toBeInTheDocument();
  });

  it("renders nothing when retentionEndsOn is null (org not configured)", () => {
    const { container } = render(
      <RetentionCard
        status="CLOSED"
        retentionClockStartedAt="2026-01-15T10:00:00Z"
        retentionEndsOn={null}
        slug="acme"
      />
    );
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId("retention-card")).not.toBeInTheDocument();
  });

  describe("when CLOSED with both fields populated", () => {
    beforeEach(() => {
      // Pin "today" to 2026-04-26 UTC so days-remaining math is deterministic.
      vi.useFakeTimers();
      vi.setSystemTime(new Date(Date.UTC(2026, 3, 26, 12, 0, 0)));
    });

    it("renders the formatted end date and days-remaining label", () => {
      // 2031-04-26 is exactly 5 calendar years (1826 days incl. one leap day).
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-04-26T10:00:00Z"
          retentionEndsOn="2031-04-26"
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toBeInTheDocument();
      // en-ZA "26 Apr 2031" — the locale renders short month name.
      expect(card.textContent).toContain("26 Apr 2031");
      expect(screen.getByTestId("retention-card-days-remaining").textContent).toBe(
        "1826 days remaining"
      );
    });

    it("links to the data-protection settings page using the org slug", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-04-26T10:00:00Z"
          retentionEndsOn="2031-04-26"
          slug="my-firm"
        />
      );

      const link = screen.getByRole("link", { name: /view data-protection settings/i });
      expect(link).toHaveAttribute("href", "/org/my-firm/settings/data-protection");
    });

    it('shows "0 days — pending deletion" when the end date is in the past', () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2020-01-01T10:00:00Z"
          retentionEndsOn="2025-01-01"
          slug="acme"
        />
      );

      expect(screen.getByTestId("retention-card-days-remaining").textContent).toBe(
        "0 days — pending deletion"
      );
    });
  });
});
