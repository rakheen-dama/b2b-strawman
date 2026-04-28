import { describe, it, expect, afterEach, vi, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetentionCard } from "@/components/legal/retention-card";

describe("RetentionCard (GAP-L-101 — 3-state surface)", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  describe("returns null for non-COMPLETED/CLOSED statuses", () => {
    it("renders nothing when status is ACTIVE", () => {
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

    it("renders nothing when status is DRAFT", () => {
      const { container } = render(
        <RetentionCard
          status="DRAFT"
          retentionClockStartedAt={null}
          retentionEndsOn={null}
          slug="acme"
        />
      );
      expect(container.firstChild).toBeNull();
      expect(screen.queryByTestId("retention-card")).not.toBeInTheDocument();
    });
  });

  describe("State A — pre-clock", () => {
    it("renders pre-clock copy when status is CLOSED but clock is null", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt={null}
          retentionEndsOn="2031-01-15"
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toBeInTheDocument();
      expect(card).toHaveAttribute("data-state", "pre-clock");
      expect(card.textContent).toMatch(/not yet stamped/i);
      // No settings deep-link in State A.
      expect(
        screen.queryByRole("link", { name: /configure retention period/i })
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("link", { name: /view data-protection settings/i })
      ).not.toBeInTheDocument();
      // No days-remaining counter (no end date computed).
      expect(screen.queryByTestId("retention-card-days-remaining")).not.toBeInTheDocument();
    });
  });

  describe("State A.2 — completed-pending-close", () => {
    it("renders pending-close copy when status is COMPLETED with clock set", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt="2026-04-26T10:00:00Z"
          retentionEndsOn={null}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toBeInTheDocument();
      expect(card).toHaveAttribute("data-state", "completed-pending-close");
      expect(card.textContent).toMatch(/begin when this matter moves to/i);
      // Clock-start anchor preview is shown using en-ZA locale.
      expect(card.textContent).toContain("26 Apr 2026");
      // No settings deep-link in State A.2.
      expect(
        screen.queryByRole("link", { name: /configure retention period/i })
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("link", { name: /view data-protection settings/i })
      ).not.toBeInTheDocument();
      // No days-remaining counter.
      expect(screen.queryByTestId("retention-card-days-remaining")).not.toBeInTheDocument();
    });
  });

  describe("State B — unconfigured", () => {
    it("renders warning + settings link when CLOSED with clock but null end-date", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-04-27T10:00:00Z"
          retentionEndsOn={null}
          slug="mathebula-partners"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toBeInTheDocument();
      expect(card).toHaveAttribute("data-state", "unconfigured");
      // Copy nudges admin toward the settings page.
      expect(card.textContent).toMatch(/isn't configured yet/i);
      // Clock-start date rendered (en-ZA locale).
      expect(card.textContent).toContain("27 Apr 2026");
      // Settings deep-link is present.
      const link = screen.getByRole("link", { name: /configure retention period/i });
      expect(link).toHaveAttribute(
        "href",
        "/org/mathebula-partners/settings/data-protection"
      );
      // No days-remaining counter — end date can't be computed yet.
      expect(screen.queryByTestId("retention-card-days-remaining")).not.toBeInTheDocument();
    });
  });

  describe("State C — active (existing happy path)", () => {
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
      expect(card).toHaveAttribute("data-state", "active");
      // en-ZA "26 Apr 2031" — the locale renders short month name.
      expect(card.textContent).toContain("26 Apr 2031");
      // Clock-start prefix is now additive in State C.
      expect(card.textContent).toContain("26 Apr 2026");
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
