import "server-only";

import { createElement, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import type { HeroTile } from "@/components/shared/detail-hero";
import type { WidgetResult } from "@/lib/dashboard-home";

// Detail-page hero helpers shared by the matter and client hero loaders (Phase 90 641B F5 → 642B,
// ADR-373 rendering rule). Server-only like both loaders. Tiles cross the RSC boundary into the
// "use client" header cards, so values / hints are strings or pre-rendered elements (OQ #26).

/** absent → null (tile omitted, the strip reflows); error → state "error"; ok → ready + render. */
export function tileFrom<T>(
  id: string,
  label: string,
  result: WidgetResult<T>,
  render: (data: T) => { value: ReactNode | null; hint?: ReactNode }
): HeroTile | null {
  if (result.state === "absent") return null;
  if (result.state === "error") return { id, label, value: null, state: "error" };
  return { id, label, state: "ready", ...render(result.data) };
}

/** "N overdue"; with a --danger icon when N > 0 (colour is not the only signal — the count is). */
export function overdueHint(count: number): ReactNode {
  const text = `${count} overdue`;
  if (count <= 0) return text;
  return createElement(
    "span",
    { className: "inline-flex items-center gap-1" },
    createElement(AlertTriangle, {
      "aria-hidden": true,
      className: "text-danger size-3.5 shrink-0",
    }),
    text
  );
}
