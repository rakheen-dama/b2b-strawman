import { describe, it, expect, afterEach, vi, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RetentionCard, deriveRetentionYears } from "@/components/legal/retention-card";

// Phase 88 U17 (ADR-354): the card reads the persisted per-matter `retentionEndsOn` (stamped at
// close, cleared on reopen, null unless CLOSED). COMPLETED-not-closed matters never show a date.

describe("deriveRetentionYears", () => {
  it("returns the year span when the clock's UTC date and the end date share month-day", () => {
    expect(deriveRetentionYears("2026-10-22T06:44:25.034385Z", "2031-10-22")).toBe(5);
    expect(deriveRetentionYears("2026-10-22T06:44:25Z", "2033-10-22")).toBe(7);
    expect(deriveRetentionYears("2026-10-22T06:44:25Z", "2027-10-22")).toBe(1);
    // `closedAt` can carry nanoseconds.
    expect(deriveRetentionYears("2026-10-28T06:44:25.034385166Z", "2031-10-28")).toBe(5);
  });

  it("uses the clock's UTC calendar date, not the local one", () => {
    // 23:30 UTC on 27 Oct is already 28 Oct in SAST — the stamp is the UTC date (27 Oct).
    expect(deriveRetentionYears("2026-10-27T23:30:00Z", "2031-10-27")).toBe(5);
    expect(deriveRetentionYears("2026-10-27T23:30:00Z", "2031-10-28")).toBeNull();
  });

  it("maps a 29 Feb close to 28 Feb in a non-leap end year", () => {
    expect(deriveRetentionYears("2028-02-29T09:00:00Z", "2033-02-28")).toBe(5);
    // Leap end year keeps 29 Feb.
    expect(deriveRetentionYears("2028-02-29T09:00:00Z", "2032-02-29")).toBe(4);
  });

  it("is not derivable when month-day differs (e.g. a legacy completion-time clock)", () => {
    expect(deriveRetentionYears("2026-04-26T10:00:00Z", "2031-05-01")).toBeNull();
    expect(deriveRetentionYears("2026-03-01T10:00:00Z", "2031-02-28")).toBeNull();
  });

  it("is not derivable without a clock, an end date, or a positive span", () => {
    expect(deriveRetentionYears(null, "2031-10-22")).toBeNull();
    expect(deriveRetentionYears("2026-10-22T06:44:25Z", null)).toBeNull();
    expect(deriveRetentionYears("2026-10-22T06:44:25Z", "2026-10-22")).toBeNull();
    expect(deriveRetentionYears("not-a-date", "2031-10-22")).toBeNull();
    expect(deriveRetentionYears("2026-10-22T06:44:25Z", "22 Oct 2031")).toBeNull();
  });
});

describe("RetentionCard", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  describe("renders nothing outside COMPLETED / CLOSED", () => {
    it.each(["ACTIVE", "DRAFT", "ARCHIVED"])("%s → no card", (status) => {
      // A reopened (ACTIVE) matter keeps its last clock; still no card.
      const { container } = render(
        <RetentionCard
          status={status}
          retentionClockStartedAt="2026-01-15T10:00:00Z"
          retentionEndsOn={null}
          firmRetentionYears={5}
          slug="acme"
        />
      );
      expect(container.firstChild).toBeNull();
      expect(screen.queryByTestId("retention-card")).not.toBeInTheDocument();
    });
  });

  describe("CLOSED with a persisted date — active", () => {
    beforeEach(() => {
      // Pin "today" so the days-remaining count is deterministic.
      vi.useFakeTimers();
      vi.setSystemTime(new Date(Date.UTC(2026, 9, 22, 12, 0, 0)));
    });

    it("shows the scheduled deletion date with the firm-default note", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25.034385Z"
          retentionEndsOn="2031-10-22"
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-state", "active");
      expect(screen.getByTestId("retention-card-scheduled-deletion")).toHaveTextContent(
        "Scheduled deletion: 22 Oct 2031 (firm default 5 years)"
      );
      // The E2E chain pins these sentences (day-75-85-closure).
      expect(card.textContent).toMatch(/clock started on/i);
      expect(card.textContent).toMatch(/permanently deleted on/);
      expect(card.textContent).toContain("Clock started on 22 Oct 2026.");
      expect(card.textContent).toContain("permanently deleted on 22 Oct 2031");
      expect(screen.getByTestId("retention-card-days-remaining")).toHaveTextContent(
        "1826 days remaining"
      );
    });

    it("names the matter's own period when it was stamped under a non-default setting", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25Z"
          retentionEndsOn="2033-10-22"
          firmRetentionYears={7}
          slug="acme"
        />
      );

      const line = screen.getByTestId("retention-card-scheduled-deletion");
      expect(line).toHaveTextContent("Scheduled deletion: 22 Oct 2033 (7-year retention period)");
      expect(line.textContent).not.toMatch(/firm default/);
    });

    it("keeps the frozen period when the firm setting changed after the close", () => {
      // Closed under 5 years; the firm has since moved to 7 — the date stays, and so does the
      // period it was stamped with.
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25Z"
          retentionEndsOn="2031-10-22"
          firmRetentionYears={7}
          slug="acme"
        />
      );

      const line = screen.getByTestId("retention-card-scheduled-deletion");
      expect(line).toHaveTextContent("Scheduled deletion: 22 Oct 2031 (5-year retention period)");
      expect(line.textContent).not.toMatch(/firm default/);
    });

    it("does not claim the firm default when the firm setting is unknown", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25Z"
          retentionEndsOn="2031-10-22"
          firmRetentionYears={null}
          slug="acme"
        />
      );

      expect(screen.getByTestId("retention-card-scheduled-deletion")).toHaveTextContent(
        "Scheduled deletion: 22 Oct 2031 (5-year retention period)"
      );
    });

    it("shows no period note and no clock sentence for a legacy row without a clock", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt={null}
          retentionEndsOn="2031-10-22"
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-state", "active");
      expect(screen.getByTestId("retention-card-scheduled-deletion").textContent).toBe(
        "Scheduled deletion: 22 Oct 2031"
      );
      expect(card.textContent).not.toMatch(/clock started on/i);
      expect(card.textContent).toContain("This closed matter will be permanently deleted on");
    });

    it("links to Firm › Compliance retention settings", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25Z"
          retentionEndsOn="2031-10-22"
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const link = screen.getByRole("link", { name: "View retention settings" });
      expect(link).toHaveAttribute("href", "/org/acme/settings/firm?tab=compliance");
    });

    it('shows "0 days — pending deletion" once the date has passed', () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2020-01-01T10:00:00Z"
          retentionEndsOn="2025-01-01"
          firmRetentionYears={5}
          slug="acme"
        />
      );

      expect(screen.getByTestId("retention-card-days-remaining").textContent).toBe(
        "0 days — pending deletion"
      );
    });
  });

  describe("CLOSED without a date — unscheduled", () => {
    it("says no date is recorded and links to the retention settings", () => {
      render(
        <RetentionCard
          status="CLOSED"
          retentionClockStartedAt="2026-10-22T06:44:25Z"
          retentionEndsOn={null}
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-state", "unscheduled");
      expect(card.textContent).toContain(
        "No scheduled deletion date is recorded for this closed matter."
      );
      expect(card.textContent).not.toMatch(/isn.t configured/i);
      expect(screen.queryByTestId("retention-card-days-remaining")).not.toBeInTheDocument();
      expect(screen.getByRole("link", { name: "View retention settings" })).toHaveAttribute(
        "href",
        "/org/acme/settings/firm?tab=compliance"
      );
    });
  });

  describe("COMPLETED (not closed) — completed-pending-close, never a date", () => {
    it("first completion (no clock): says retention starts at close, firm default 5 years", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt={null}
          retentionEndsOn={null}
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-state", "completed-pending-close");
      expect(card.textContent).toContain(
        "Retention starts when this matter is closed. Closed matters are kept for 5 years (firm default) and then permanently deleted."
      );
      expect(screen.queryByTestId("retention-card-scheduled-deletion")).not.toBeInTheDocument();
      expect(screen.queryByTestId("retention-card-days-remaining")).not.toBeInTheDocument();
      expect(screen.queryByRole("link")).not.toBeInTheDocument();
      expect(card.textContent).not.toMatch(/actions menu|not yet stamped/i);
    });

    it("reopen → complete (stale clock): shows no date and never the old clock", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt="2026-10-28T06:44:25Z"
          retentionEndsOn={null}
          firmRetentionYears={5}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-state", "completed-pending-close");
      expect(card.textContent).not.toContain("28 Oct 2026");
      expect(card.textContent).not.toMatch(/anchor preview|clock started/i);
      expect(card.textContent).not.toMatch(/\d{1,2} [A-Z][a-z]{2} \d{4}/);
    });

    it("states the current firm setting when it is not the default", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt={null}
          retentionEndsOn={null}
          firmRetentionYears={7}
          slug="acme"
        />
      );

      const card = screen.getByTestId("retention-card");
      expect(card.textContent).toContain(
        "Closed matters are kept for 7 years and then permanently deleted."
      );
      expect(card.textContent).not.toMatch(/firm default/);
    });

    it("uses the singular for a one-year setting", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt={null}
          retentionEndsOn={null}
          firmRetentionYears={1}
          slug="acme"
        />
      );

      expect(screen.getByTestId("retention-card").textContent).toContain(
        "Closed matters are kept for 1 year and then permanently deleted."
      );
    });

    it("falls back to generic copy when the firm setting is unknown", () => {
      render(
        <RetentionCard
          status="COMPLETED"
          retentionClockStartedAt={null}
          retentionEndsOn={null}
          firmRetentionYears={null}
          slug="acme"
        />
      );

      expect(screen.getByTestId("retention-card").textContent).toContain(
        "Closed matters are kept for your firm's retention period and then permanently deleted."
      );
    });
  });
});
