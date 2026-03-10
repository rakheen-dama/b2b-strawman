"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";

const CAPABILITY_DESCRIPTIONS = [
  {
    name: "Financial Visibility",
    description:
      "View financial data including rates, budgets, and profitability reports",
  },
  {
    name: "Invoicing",
    description: "Create, edit, and send invoices to customers",
  },
  {
    name: "Project Management",
    description: "Create and manage projects, tasks, and documents",
  },
  {
    name: "Team Oversight",
    description:
      "View team members' work, time entries, and assignments",
  },
  {
    name: "Customer Management",
    description: "Create and manage customer records and relationships",
  },
  {
    name: "Automations",
    description: "Configure workflow automations and scheduling rules",
  },
  {
    name: "Resource Planning",
    description:
      "View and manage resource allocation and capacity planning",
  },
];

export function CapabilityReference() {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5">
          <ChevronDown
            className={`size-4 transition-transform ${isOpen ? "rotate-180" : ""}`}
          />
          Capability Reference
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-3 rounded-lg border border-slate-200 dark:border-slate-800">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
                <th className="px-4 py-2.5 text-left font-medium text-slate-700 dark:text-slate-300">
                  Capability
                </th>
                <th className="px-4 py-2.5 text-left font-medium text-slate-700 dark:text-slate-300">
                  Description
                </th>
              </tr>
            </thead>
            <tbody>
              {CAPABILITY_DESCRIPTIONS.map((cap, i) => (
                <tr
                  key={cap.name}
                  className={
                    i < CAPABILITY_DESCRIPTIONS.length - 1
                      ? "border-b border-slate-200 dark:border-slate-800"
                      : ""
                  }
                >
                  <td className="px-4 py-2.5 font-medium text-slate-950 dark:text-slate-50">
                    {cap.name}
                  </td>
                  <td className="px-4 py-2.5 text-slate-600 dark:text-slate-400">
                    {cap.description}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
