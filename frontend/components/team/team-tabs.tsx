"use client";

import { useState } from "react";
import { Tabs as TabsPrimitive } from "radix-ui";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";
import { MemberList } from "@/components/team/member-list";
import { PendingInvitations } from "@/components/team/pending-invitations";

const tabs = [
  { id: "members", label: "Members" },
  { id: "invitations", label: "Pending Invitations" },
] as const;

type TabId = (typeof tabs)[number]["id"];

interface TeamTabsProps {
  isAdmin: boolean;
}

export function TeamTabs({ isAdmin }: TeamTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("members");

  return (
    <TabsPrimitive.Root value={activeTab} onValueChange={(v) => setActiveTab(v as TabId)}>
      <TabsPrimitive.List className="relative flex gap-6 border-b border-olive-200 dark:border-olive-800">
        {tabs.map((tab) => (
          <TabsPrimitive.Trigger
            key={tab.id}
            value={tab.id}
            className={cn(
              "relative pb-3 text-sm font-medium transition-colors outline-none",
              "text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-200",
              "data-[state=active]:text-olive-950 dark:data-[state=active]:text-olive-50",
              "focus-visible:outline-2 focus-visible:outline-indigo-500 focus-visible:outline-offset-2"
            )}
          >
            {tab.label}
            {activeTab === tab.id && (
              <motion.span
                className="absolute inset-x-0 bottom-0 h-0.5 bg-indigo-500"
                layoutId="team-tab-indicator"
                transition={{ type: "spring", stiffness: 300, damping: 25 }}
                aria-hidden="true"
              />
            )}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>

      <TabsPrimitive.Content value="members" className="pt-6 outline-none">
        <MemberList />
      </TabsPrimitive.Content>
      <TabsPrimitive.Content value="invitations" className="pt-6 outline-none">
        <PendingInvitations isAdmin={isAdmin} />
      </TabsPrimitive.Content>
    </TabsPrimitive.Root>
  );
}
