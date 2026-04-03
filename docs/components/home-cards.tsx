"use client";

import Link from "next/link";

const cards = [
  {
    title: "Getting Started",
    description:
      "Set up your account, invite your team, and create your first project.",
    href: "/getting-started/quick-setup",
    icon: "\u{1F680}",
  },
  {
    title: "Features",
    description:
      "Explore projects, time tracking, invoicing, documents, and more.",
    href: "/features/projects",
    icon: "\u{1F4E6}",
  },
  {
    title: "Administration",
    description:
      "Manage your team, organization settings, integrations, and billing.",
    href: "/admin/team-permissions",
    icon: "\u2699\uFE0F",
  },
  {
    title: "For Accounting Firms",
    description:
      "SARS deadlines, recurring engagements, and compliance packs.",
    href: "/verticals/accounting/sars-deadlines",
    icon: "\u{1F4CA}",
  },
  {
    title: "For Legal Firms",
    description:
      "Court calendar, conflict checks, and LSSA tariff billing (coming soon).",
    href: "/verticals/legal/court-calendar",
    icon: "\u2696\uFE0F",
  },
];

export function HomeCards() {
  return (
    <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      {cards.map((card) => (
        <Link
          key={card.href}
          href={card.href}
          className="group rounded-lg border border-slate-200/80 bg-white p-6 shadow-sm transition-all hover:border-teal-500/50 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-500/60 focus-visible:ring-offset-2 dark:border-slate-800/80 dark:bg-slate-900 dark:hover:border-teal-500/50 dark:focus-visible:ring-offset-slate-900"
        >
          <div className="mb-3 text-2xl">{card.icon}</div>
          <h3 className="font-semibold text-slate-900 dark:text-slate-100">
            {card.title}
          </h3>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            {card.description}
          </p>
        </Link>
      ))}
    </div>
  );
}
