"use client";

import { useEffect } from "react";
import { useRecentItems } from "@/components/recent-items-provider";

interface RecordRecentItemProps {
  href: string;
  label: string;
}

/**
 * Records the current detail page in the command palette's "Recent" list (Phase 87 610E,
 * §12.6.5). Rendered by the matter and client detail pages with the name they already hold
 * server-side, so the label is the human name rather than a title-cased URL id. Renders nothing.
 */
export function RecordRecentItem({ href, label }: RecordRecentItemProps) {
  const { addItem } = useRecentItems();
  useEffect(() => {
    addItem({ href, label });
  }, [href, label, addItem]);
  return null;
}
