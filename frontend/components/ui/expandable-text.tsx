"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface ExpandableTextProps {
  text: string | null | undefined;
  lineClamp?: number;
  className?: string;
}

export function ExpandableText({
  text,
  lineClamp = 2,
  className,
}: ExpandableTextProps) {
  const [expanded, setExpanded] = useState(false);

  if (!text) return null;

  // Heuristic: only show toggle when text is likely long enough to overflow
  const showToggle = expanded || text.length >= lineClamp * 60;

  return (
    <div data-testid="expandable-text" className={cn(className)}>
      <p
        style={
          !expanded
            ? {
                display: "-webkit-box",
                WebkitLineClamp: lineClamp,
                WebkitBoxOrient: "vertical",
                overflow: "hidden",
              }
            : undefined
        }
      >
        {text}
      </p>
      {showToggle && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setExpanded((prev) => !prev)}
          data-testid="expandable-text-toggle"
          className="mt-1 h-auto p-0 text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-500 dark:hover:text-teal-400"
        >
          {expanded ? "Show less" : "Show more"}
        </Button>
      )}
    </div>
  );
}
