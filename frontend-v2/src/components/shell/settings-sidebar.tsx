"use client";

import {
  Building2,
  Palette,
  DollarSign,
  Calculator,
  Receipt,
  FileText,
  ScrollText,
  CreditCard,
  Puzzle,
} from "lucide-react";
import { SettingsLayout } from "@/components/layout/settings-layout";
import type { ReactNode } from "react";

interface SettingsSidebarProps {
  slug: string;
  children: ReactNode;
}

export function SettingsSidebar({ slug, children }: SettingsSidebarProps) {
  const base = `/org/${slug}/settings`;

  const groups = [
    {
      title: "Organization",
      items: [
        {
          label: "General",
          href: `${base}/general`,
          icon: <Building2 className="h-4 w-4" />,
        },
        {
          label: "Branding",
          href: `${base}/branding`,
          icon: <Palette className="h-4 w-4" />,
        },
      ],
    },
    {
      title: "Financial",
      items: [
        {
          label: "Billing Rates",
          href: `${base}/rates`,
          icon: <DollarSign className="h-4 w-4" />,
        },
        {
          label: "Cost Rates",
          href: `${base}/cost-rates`,
          icon: <Calculator className="h-4 w-4" />,
        },
        {
          label: "Tax",
          href: `${base}/tax`,
          icon: <Receipt className="h-4 w-4" />,
        },
      ],
    },
    {
      title: "Documents",
      items: [
        {
          label: "Templates",
          href: `${base}/templates`,
          icon: <FileText className="h-4 w-4" />,
        },
        {
          label: "Clauses",
          href: `${base}/clauses`,
          icon: <ScrollText className="h-4 w-4" />,
        },
      ],
    },
    {
      title: "Account",
      items: [
        {
          label: "Plan & Billing",
          href: `${base}/billing`,
          icon: <CreditCard className="h-4 w-4" />,
        },
        {
          label: "Integrations",
          href: `${base}/integrations`,
          icon: <Puzzle className="h-4 w-4" />,
        },
      ],
    },
  ];

  return <SettingsLayout groups={groups}>{children}</SettingsLayout>;
}
