"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";

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
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        data-testid="expandable-text-toggle"
        className="mt-1 text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-500 dark:hover:text-teal-400"
      >
        {expanded ? "Show less" : "Show more"}
      </button>
    </div>
  );
}
