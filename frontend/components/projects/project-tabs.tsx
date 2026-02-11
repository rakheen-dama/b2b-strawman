"use client";

import { useState, type ReactNode } from "react";
import { motion } from "motion/react";

interface ProjectTabsProps {
  documentsPanel: ReactNode;
  membersPanel: ReactNode;
}

const tabs = [
  { id: "documents", label: "Documents" },
  { id: "members", label: "Members" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export function ProjectTabs({ documentsPanel, membersPanel }: ProjectTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>("documents");

  return (
    <div>
      {/* Tab bar */}
      <div className="relative border-b border-olive-200 dark:border-olive-800">
        <div className="flex gap-6">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`relative pb-3 text-sm font-medium transition-colors ${
                activeTab === tab.id
                  ? "text-olive-950 dark:text-olive-50"
                  : "text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-200"
              }`}
            >
              {tab.label}
              {activeTab === tab.id && (
                <motion.div
                  layoutId="project-tab-indicator"
                  className="absolute inset-x-0 bottom-0 h-0.5 bg-indigo-500"
                  transition={{ type: "spring", stiffness: 300, damping: 25 }}
                />
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Tab content */}
      <div className="pt-6">
        {activeTab === "documents" ? documentsPanel : membersPanel}
      </div>
    </div>
  );
}
