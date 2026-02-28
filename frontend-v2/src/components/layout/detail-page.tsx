"use client";

import type { ReactNode } from "react";
import { useQueryState } from "nuqs";
import { motion } from "motion/react";

import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";

interface DetailTab {
  id: string;
  label: string;
  content: ReactNode;
  count?: number;
}

interface DetailPageProps {
  header: ReactNode;
  tabs: DetailTab[];
  defaultTab?: string;
  className?: string;
}

export function DetailPage({
  header,
  tabs,
  defaultTab,
  className,
}: DetailPageProps) {
  const fallback = defaultTab ?? tabs[0]?.id ?? "";
  const [activeTab, setActiveTab] = useQueryState("tab", {
    defaultValue: fallback,
  });

  const currentTab = tabs.find((t) => t.id === activeTab) ?? tabs[0];

  return (
    <div className={cn("flex flex-col gap-6", className)}>
      {/* Header area */}
      <div>{header}</div>

      {/* Tab bar */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6" role="tablist">
          {tabs.map((tab) => {
            const isActive = tab.id === (currentTab?.id ?? "");
            return (
              <button
                key={tab.id}
                role="tab"
                aria-selected={isActive}
                onClick={() => setActiveTab(tab.id)}
                className={cn(
                  "relative flex items-center gap-1.5 whitespace-nowrap pb-3 text-sm font-medium transition-colors",
                  isActive
                    ? "text-slate-900"
                    : "text-slate-500 hover:text-slate-700"
                )}
              >
                {tab.label}
                {tab.count !== undefined && (
                  <Badge variant="neutral" className="text-[10px] px-1.5 py-0">
                    {tab.count}
                  </Badge>
                )}
                {isActive && (
                  <motion.div
                    layoutId="detail-tab-underline"
                    className="absolute inset-x-0 -bottom-px h-0.5 bg-teal-600"
                    transition={{ type: "spring", stiffness: 500, damping: 30 }}
                  />
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Tab content */}
      <div>{currentTab?.content}</div>
    </div>
  );
}
