import Link from "next/link";
import {
  CreditCard,
  Bell,
  Building2,
  Shield,
  Puzzle,
  ChevronRight,
  DollarSign,
  ListChecks,
  Tag,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { LucideIcon } from "lucide-react";

interface SettingsCard {
  icon: LucideIcon;
  title: string;
  description: string;
  href: string | null;
  comingSoon: boolean;
}

const settingsCards: SettingsCard[] = [
  {
    icon: CreditCard,
    title: "Billing",
    description: "Manage your subscription and view usage",
    href: "billing",
    comingSoon: false,
  },
  {
    icon: Bell,
    title: "Notifications",
    description: "Configure notification preferences",
    href: "notifications",
    comingSoon: false,
  },
  {
    icon: DollarSign,
    title: "Rates & Currency",
    description: "Manage billing rates, cost rates, and default currency",
    href: "rates",
    comingSoon: false,
  },
  {
    icon: ListChecks,
    title: "Custom Fields",
    description: "Define custom fields and groups for projects, tasks, and customers",
    href: "custom-fields",
    comingSoon: false,
  },
  {
    icon: Tag,
    title: "Tags",
    description: "Manage org-wide tags for projects, tasks, and customers",
    href: "tags",
    comingSoon: false,
  },
  {
    icon: Building2,
    title: "Organization",
    description: "Update org name, logo, and details",
    href: null,
    comingSoon: true,
  },
  {
    icon: Shield,
    title: "Security",
    description: "Configure authentication and access policies",
    href: null,
    comingSoon: true,
  },
  {
    icon: Puzzle,
    title: "Integrations",
    description: "Connect third-party tools and services",
    href: null,
    comingSoon: true,
  },
];

export default async function SettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">Settings</h1>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {settingsCards.map((card) => {
          const Icon = card.icon;
          const content = (
            <div
              className={`flex items-center gap-4 rounded-lg border border-olive-200 bg-white p-6 transition-all dark:border-olive-800 dark:bg-olive-950 ${
                card.comingSoon
                  ? "cursor-not-allowed opacity-50"
                  : "hover:border-olive-300 hover:shadow-sm dark:hover:border-olive-700"
              }`}
            >
              <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-olive-100 dark:bg-olive-800">
                <Icon className="size-6 text-olive-600 dark:text-olive-400" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="font-semibold text-olive-950 dark:text-olive-50">{card.title}</p>
                  {card.comingSoon && (
                    <Badge variant="neutral">Coming soon</Badge>
                  )}
                </div>
                <p className="mt-0.5 text-sm text-olive-600 dark:text-olive-400">
                  {card.description}
                </p>
              </div>
              <ChevronRight className="size-5 shrink-0 text-olive-400 dark:text-olive-500" />
            </div>
          );

          if (card.comingSoon || !card.href) {
            return <div key={card.title}>{content}</div>;
          }

          return (
            <Link
              key={card.title}
              href={`/org/${slug}/settings/${card.href}`}
              className="block"
            >
              {content}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
