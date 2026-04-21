import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Sticky bottom-action bar for mobile primary actions.
 *
 * On mobile (< md): fixed to bottom of viewport, safe-area padding included.
 * On desktop (>= md): hidden (desktop keeps inline action buttons).
 *
 * Usage — wrap a page's content with `pb-24 md:pb-0` so the bar doesn't
 * cover content, then render <StickyActionBar> at the end of the page.
 *
 * @example
 * <div className="space-y-8 pb-24 md:pb-0">
 *   ...page content...
 *   <StickyActionBar>
 *     <Button className="flex-1 min-h-11">Accept Proposal</Button>
 *     <Button variant="outline" className="flex-1 min-h-11">Decline</Button>
 *   </StickyActionBar>
 * </div>
 */
function StickyActionBar({
  className,
  children,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="sticky-action-bar"
      data-testid="sticky-action-bar"
      className={cn(
        // Mobile: fixed to bottom, elevated above content
        "fixed inset-x-0 bottom-0 z-30 flex items-center gap-2 border-t border-slate-200 bg-white p-4 shadow-[0_-2px_8px_rgba(15,23,42,0.06)]",
        // iOS safe-area
        "pb-[calc(1rem+env(safe-area-inset-bottom))]",
        // Hide on md+ (desktop uses inline actions)
        "md:hidden",
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

export { StickyActionBar };
