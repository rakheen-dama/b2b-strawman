import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

import { StatusBadge } from "@/components/ui/status-badge";

afterEach(() => cleanup());

describe("StatusBadge", () => {
  describe("renders correct text for each status category", () => {
    it("renders slate statuses", () => {
      const { unmount } = render(<StatusBadge status="DRAFT" />);
      expect(screen.getByText("Draft")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="PENDING" />);
      expect(screen.getByText("Pending")).toBeInTheDocument();
    });

    it("renders blue statuses", () => {
      const { unmount } = render(<StatusBadge status="IN_PROGRESS" />);
      expect(screen.getByText("In Progress")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="ONBOARDING" />);
      expect(screen.getByText("Onboarding")).toBeInTheDocument();
    });

    it("renders emerald statuses", () => {
      const { unmount } = render(<StatusBadge status="ACTIVE" />);
      expect(screen.getByText("Active")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="COMPLETED" />);
      expect(screen.getByText("Completed")).toBeInTheDocument();
      cleanup();

      render(<StatusBadge status="ON_TRACK" />);
      expect(screen.getByText("On Track")).toBeInTheDocument();
    });

    it("renders amber statuses", () => {
      const { unmount } = render(<StatusBadge status="AT_RISK" />);
      expect(screen.getByText("At Risk")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="OVERDUE" />);
      expect(screen.getByText("Overdue")).toBeInTheDocument();
    });

    it("renders red statuses", () => {
      const { unmount } = render(<StatusBadge status="OVER_BUDGET" />);
      expect(screen.getByText("Over Budget")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="CANCELLED" />);
      expect(screen.getByText("Cancelled")).toBeInTheDocument();
    });

    it("renders purple statuses", () => {
      const { unmount } = render(<StatusBadge status="ARCHIVED" />);
      expect(screen.getByText("Archived")).toBeInTheDocument();
      unmount();

      render(<StatusBadge status="OFFBOARDED" />);
      expect(screen.getByText("Offboarded")).toBeInTheDocument();
    });
  });

  describe("applies correct color classes for each category", () => {
    it("applies slate classes for DRAFT", () => {
      render(<StatusBadge status="DRAFT" />);
      const badge = screen.getByText("Draft");
      expect(badge.className).toContain("bg-slate-100");
      expect(badge.className).toContain("text-slate-700");
    });

    it("applies blue classes for IN_PROGRESS", () => {
      render(<StatusBadge status="IN_PROGRESS" />);
      const badge = screen.getByText("In Progress");
      expect(badge.className).toContain("bg-blue-100");
      expect(badge.className).toContain("text-blue-700");
    });

    it("applies emerald classes for ACTIVE", () => {
      render(<StatusBadge status="ACTIVE" />);
      const badge = screen.getByText("Active");
      expect(badge.className).toContain("bg-emerald-100");
      expect(badge.className).toContain("text-emerald-700");
    });

    it("applies amber classes for AT_RISK", () => {
      render(<StatusBadge status="AT_RISK" />);
      const badge = screen.getByText("At Risk");
      expect(badge.className).toContain("bg-amber-100");
      expect(badge.className).toContain("text-amber-700");
    });

    it("applies red classes for CANCELLED", () => {
      render(<StatusBadge status="CANCELLED" />);
      const badge = screen.getByText("Cancelled");
      expect(badge.className).toContain("bg-red-100");
      expect(badge.className).toContain("text-red-700");
    });

    it("applies purple classes for ARCHIVED", () => {
      render(<StatusBadge status="ARCHIVED" />);
      const badge = screen.getByText("Archived");
      expect(badge.className).toContain("bg-purple-100");
      expect(badge.className).toContain("text-purple-700");
    });
  });

  describe("handles case-insensitive status strings", () => {
    it("handles lowercase", () => {
      render(<StatusBadge status="active" />);
      const badge = screen.getByText("Active");
      expect(badge.className).toContain("bg-emerald-100");
    });

    it("handles mixed case", () => {
      render(<StatusBadge status="In_Progress" />);
      const badge = screen.getByText("In Progress");
      expect(badge.className).toContain("bg-blue-100");
    });

    it("handles all caps", () => {
      render(<StatusBadge status="PAID" />);
      const badge = screen.getByText("Paid");
      expect(badge.className).toContain("bg-emerald-100");
    });
  });

  describe("handles underscored and spaced variants", () => {
    it("handles space-separated status", () => {
      render(<StatusBadge status="in progress" />);
      const badge = screen.getByText("In Progress");
      expect(badge.className).toContain("bg-blue-100");
    });

    it("handles underscore-separated status", () => {
      render(<StatusBadge status="over_budget" />);
      const badge = screen.getByText("Over Budget");
      expect(badge.className).toContain("bg-red-100");
    });

    it("handles mixed separators and extra whitespace", () => {
      render(<StatusBadge status="  at  risk  " />);
      const badge = screen.getByText("At Risk");
      expect(badge.className).toContain("bg-amber-100");
    });

    it("handles space-separated with mixed case", () => {
      render(<StatusBadge status="On Track" />);
      const badge = screen.getByText("On Track");
      expect(badge.className).toContain("bg-emerald-100");
    });
  });

  describe("unknown statuses", () => {
    it("falls back to slate for unknown status", () => {
      render(<StatusBadge status="UNKNOWN_STATUS" />);
      const badge = screen.getByText("Unknown Status");
      expect(badge.className).toContain("bg-slate-100");
      expect(badge.className).toContain("text-slate-700");
    });
  });

  describe("className prop", () => {
    it("merges additional className", () => {
      render(<StatusBadge status="ACTIVE" className="ml-2" />);
      const badge = screen.getByText("Active");
      expect(badge.className).toContain("ml-2");
      expect(badge.className).toContain("bg-emerald-100");
    });
  });

  describe("pill shape classes", () => {
    it("has the correct base pill classes", () => {
      render(<StatusBadge status="DRAFT" />);
      const badge = screen.getByText("Draft");
      expect(badge.className).toContain("inline-flex");
      expect(badge.className).toContain("items-center");
      expect(badge.className).toContain("rounded-full");
      expect(badge.className).toContain("text-xs");
      expect(badge.className).toContain("font-medium");
    });
  });
});
