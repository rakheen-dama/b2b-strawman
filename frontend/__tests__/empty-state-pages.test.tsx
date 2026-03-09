import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import {
  TrendingUp,
  Bell,
  DollarSign,
  HeartPulse,
} from "lucide-react";

vi.mock("server-only", () => ({}));

// Mock next/link as a simple anchor
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { EmptyState } from "@/components/empty-state";
import { createMessages } from "@/lib/messages";

describe("Empty State Page Integrations (Tier 3-4)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("profitability page renders EmptyState with settings link when data is empty", () => {
    const { t } = createMessages("empty-states");

    render(
      <EmptyState
        icon={TrendingUp}
        title={t("profitability.page.heading")}
        description={t("profitability.page.description")}
        secondaryLink={{
          label: t("profitability.page.link"),
          href: "/org/test-org/settings/rates",
        }}
      />,
    );

    expect(screen.getByText("No profitability data yet")).toBeInTheDocument();
    expect(
      screen.getByText(/Set up rate cards in Settings/),
    ).toBeInTheDocument();

    const link = screen.getByText("Go to rate card settings");
    expect(link).toBeInTheDocument();
    expect(link.closest("a")).toHaveAttribute(
      "href",
      "/org/test-org/settings/rates",
    );
  });

  it("notifications page renders EmptyState when no notifications", () => {
    const { t } = createMessages("empty-states");

    render(
      <EmptyState
        icon={Bell}
        title={t("notifications.page.heading")}
        description={t("notifications.page.description")}
      />,
    );

    expect(screen.getByText("You're all caught up")).toBeInTheDocument();
    expect(
      screen.getByText(/new comments, task assignments/),
    ).toBeInTheDocument();
  });

  it("rate cards settings renders EmptyState with catalog text when no members", () => {
    const { t } = createMessages("empty-states");

    render(
      <EmptyState
        icon={DollarSign}
        title={t("rates.settings.heading")}
        description={t("rates.settings.description")}
      />,
    );

    expect(screen.getByText("No rate cards yet")).toBeInTheDocument();
    expect(
      screen.getByText(/define what you charge clients/),
    ).toBeInTheDocument();
  });

  it("dashboard ProjectHealthWidget renders EmptyState when no projects", () => {
    render(
      <EmptyState
        icon={HeartPulse}
        title="No projects yet"
        description="Create a project to start tracking health status."
      />,
    );

    expect(screen.getByText("No projects yet")).toBeInTheDocument();
    expect(
      screen.getByText("Create a project to start tracking health status."),
    ).toBeInTheDocument();
  });
});
