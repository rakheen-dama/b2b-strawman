"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";
import { Button } from "@b2mash/ui/button";

interface ExpandableTextProps {
  text: string | null | undefined;
  lineClamp?: number;
  className?: string;
}

export function ExpandableText({ text, lineClamp = 2, className }: ExpandableTextProps) {
  const [expanded, setExpanded] = useState(false);

  if (!text) return null;

  // v1: always show toggle when text is provided. A ResizeObserver-based
  // overflow check (scrollHeight > clientHeight) would be a v2 improvement.

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
      <Button
        variant="ghost"
        size="sm"
        onClick={() => setExpanded((prev) => !prev)}
        data-testid="expandable-text-toggle"
        className="mt-1 h-auto p-0 text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-500 dark:hover:text-teal-400"
      >
        {expanded ? "Show less" : "Show more"}
      </Button>
    </div>
  );
}
