import Link from "next/link";
import {
  CreditCard,
  Building2,
  Palette,
  DollarSign,
  Calculator,
  Receipt,
  FileText,
  ScrollText,
  Puzzle,
  ChevronRight,
} from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import type { LucideIcon } from "lucide-react";

interface SettingsCard {
  icon: LucideIcon;
  title: string;
  description: string;
  href: string;
  group: string;
  adminOnly?: boolean;
}

const settingsCards: SettingsCard[] = [
  {
    icon: Building2,
    title: "General",
    description: "Organization name and basic settings",
    href: "general",
    group: "Organization",
  },
  {
    icon: Palette,
    title: "Branding",
    description: "Logo, brand color, and footer text",
    href: "branding",
    group: "Organization",
  },
  {
    icon: DollarSign,
    title: "Billing Rates",
    description: "Manage billing rates for team members",
    href: "rates",
    group: "Financial",
    adminOnly: true,
  },
  {
    icon: Calculator,
    title: "Cost Rates",
    description: "Manage internal cost rates for profitability tracking",
    href: "cost-rates",
    group: "Financial",
    adminOnly: true,
  },
  {
    icon: Receipt,
    title: "Tax",
    description: "Configure tax registration, labels, and inclusive pricing",
    href: "tax",
    group: "Financial",
    adminOnly: true,
  },
  {
    icon: FileText,
    title: "Templates",
    description: "Manage document templates for engagement letters and SOWs",
    href: "templates",
    group: "Documents",
  },
  {
    icon: ScrollText,
    title: "Clauses",
    description: "Manage reusable clause library for document generation",
    href: "clauses",
    group: "Documents",
  },
  {
    icon: CreditCard,
    title: "Plan & Billing",
    description: "Manage your subscription and view usage",
    href: "billing",
    group: "Account",
  },
  {
    icon: Puzzle,
    title: "Integrations",
    description: "Connect third-party tools and services",
    href: "integrations",
    group: "Account",
  },
];

const GROUP_ORDER = ["Organization", "Financial", "Documents", "Account"];

export default async function SettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const visibleCards = settingsCards.filter(
    (card) => !card.adminOnly || isAdmin,
  );

  const grouped = GROUP_ORDER.map((group) => ({
    group,
    cards: visibleCards.filter((c) => c.group === group),
  })).filter((g) => g.cards.length > 0);

  return (
    <div className="space-y-10">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900">
          Settings
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Manage your organization settings and preferences.
        </p>
      </div>

      {grouped.map(({ group, cards }) => (
        <div key={group} className="space-y-4">
          <h2 className="text-xs font-medium uppercase tracking-wider text-slate-400">
            {group}
          </h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {cards.map((card) => {
              const Icon = card.icon;
              return (
                <Link
                  key={card.title}
                  href={`/org/${slug}/settings/${card.href}`}
                  className="group flex items-center gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition-all hover:border-slate-300 hover:shadow-md"
                >
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 transition-colors group-hover:bg-slate-200">
                    <Icon className="size-5 text-slate-600" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-900">{card.title}</p>
                    <p className="mt-0.5 text-sm text-slate-500">
                      {card.description}
                    </p>
                  </div>
                  <ChevronRight className="size-4 shrink-0 text-slate-300 transition-colors group-hover:text-slate-500" />
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
