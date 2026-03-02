"use client";

import { useState } from "react";
import Link from "next/link";
import {
  CheckCircle2,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  ChevronRight,
  ShieldAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import type { SetupProgressCardProps, ContextGroup } from "./types";

function ContextGroupRow({ group }: { group: ContextGroup }) {
  const [expanded, setExpanded] = useState(false);
  const isComplete = group.filled === group.total && group.total > 0;
  const missingFields = group.fields.filter((f) => !f.filled);

  return (
    <li className="space-y-1">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center gap-2 text-sm text-left"
        aria-label={`Toggle ${group.contextLabel} details`}
      >
        {isComplete ? (
          <CheckCircle2 className="h-4 w-4 shrink-0 text-green-600 dark:text-green-400" />
        ) : (
          <AlertCircle className="h-4 w-4 shrink-0 text-amber-500 dark:text-amber-400" />
        )}
        <span
          className={cn(
            "flex-1",
            isComplete && "text-muted-foreground line-through",
          )}
        >
          {group.contextLabel}: {group.filled}/{group.total} fields
        </span>
        {missingFields.length > 0 && (
          <ChevronRight
            className={cn(
              "h-3.5 w-3.5 shrink-0 text-slate-400 transition-transform",
              expanded && "rotate-90",
            )}
          />
        )}
      </button>

      {expanded && missingFields.length > 0 && (
        <ul className="ml-6 space-y-0.5 border-l border-slate-200 pl-3 dark:border-slate-700">
          {missingFields.map((field) => (
            <li key={field.slug} className="text-xs text-slate-500 dark:text-slate-400">
              <Link
                href="#custom-fields"
                className="hover:text-teal-600 hover:underline dark:hover:text-teal-400"
              >
                {field.name}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

export function SetupProgressCard({
  title,
  completionPercentage,
  overallComplete,
  steps,
  defaultCollapsed,
  canManage = false,
  activationBlockers,
  contextGroups,
}: SetupProgressCardProps) {
  const [collapsed, setCollapsed] = useState(
    defaultCollapsed ?? overallComplete,
  );

  const hasBlockers = activationBlockers && activationBlockers.length > 0;
  const hasContextGroups = contextGroups && contextGroups.length > 0;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">{title}</CardTitle>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setCollapsed((prev) => !prev)}
            aria-label={collapsed ? "Expand setup steps" : "Collapse setup steps"}
          >
            {collapsed ? (
              <ChevronDown className="h-4 w-4" />
            ) : (
              <ChevronUp className="h-4 w-4" />
            )}
          </Button>
        </div>
        {overallComplete && collapsed && (
          <div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
            <CheckCircle2 className="h-4 w-4" />
            <span>Setup complete</span>
          </div>
        )}
      </CardHeader>

      {!collapsed && (
        <CardContent className="space-y-4">
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>Progress</span>
              <span>{completionPercentage}%</span>
            </div>
            <Progress value={completionPercentage} />
          </div>

          <ul className="space-y-2">
            {steps.map((step) => (
              <li key={step.label} className="flex items-center gap-2 text-sm">
                {step.complete ? (
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-green-600 dark:text-green-400" />
                ) : (
                  <AlertCircle className="h-4 w-4 shrink-0 text-amber-500 dark:text-amber-400" />
                )}
                <span
                  className={cn(
                    step.complete && "text-muted-foreground line-through",
                  )}
                >
                  {step.label}
                </span>
                {step.actionHref &&
                  (!step.permissionRequired || canManage) && (
                    <Link
                      href={step.actionHref}
                      className="ml-auto text-xs text-teal-600 hover:underline dark:text-teal-400"
                    >
                      {step.complete ? "View" : "Set up"}
                    </Link>
                  )}
              </li>
            ))}
          </ul>

          {hasContextGroups && (
            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Field Completeness
              </p>
              <ul className="space-y-1.5">
                {contextGroups.map((group) => (
                  <ContextGroupRow key={group.contextLabel} group={group} />
                ))}
              </ul>
            </div>
          )}

          {hasBlockers && (
            <div className="space-y-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/50">
              <div className="flex items-center gap-2 text-sm font-medium text-red-700 dark:text-red-400">
                <ShieldAlert className="h-4 w-4 shrink-0" />
                <span>Blocking activation</span>
              </div>
              <ul className="space-y-1 pl-6">
                {activationBlockers.map((message) => (
                  <li
                    key={message}
                    className="text-sm text-red-600 dark:text-red-400"
                  >
                    {message}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </CardContent>
      )}
    </Card>
  );
}
