import { cn } from "@/lib/utils";

/**
 * The shared tab underline (Phase 90 642C, extracted in Phase 91 664A): a horizontal row of tabs or
 * links in a `ScrollArea`, the active item marked by a 2px `after:` underline in `foreground`, with
 * edge fades over content hidden past either end. Consumers: the grouped tab bar (matter / client
 * detail), the settings family tab strip and the settings family bar.
 *
 * A plain module — no React, no `"use client"` — so a Server Component can import the class strings
 * (e.g. a row of plain `Link`s keyed on `aria-current="page"`). The edge-fade *state* is a client
 * hook (`use-edge-fades.ts`); only the pure DOM helpers live here.
 *
 * Every class string is written out in full: Tailwind only generates the classes it finds literally
 * in the source, so a variant must never be derived by string replacement.
 */

/**
 * Underline keyed on `aria-selected="true"` — for `role="tab"` triggers. Not `data-state`, which
 * Radix's DropdownMenuTrigger sets to `open|closed` on the grouped bar's multi-tab triggers. The
 * focus ring is inset so the scroll viewport can't clip it.
 */
export const TAB_UNDERLINE_SELECTED = cn(
  "relative shrink-0 cursor-pointer rounded-sm px-2 pt-2 pb-3 text-sm font-medium whitespace-nowrap outline-none transition-colors motion-reduce:transition-none",
  "text-muted-foreground hover:text-foreground aria-selected:text-foreground",
  "after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-foreground after:opacity-0 after:transition-opacity aria-selected:after:opacity-100 motion-reduce:after:transition-none",
  "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-inset"
);

/**
 * The same underline keyed on `aria-current="page"` — for navigation links (a row of `Link`s is a
 * list of links, not a tablist).
 */
export const TAB_UNDERLINE_CURRENT = cn(
  "relative shrink-0 cursor-pointer rounded-sm px-2 pt-2 pb-3 text-sm font-medium whitespace-nowrap outline-none transition-colors motion-reduce:transition-none",
  "text-muted-foreground hover:text-foreground aria-[current=page]:text-foreground",
  "after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-foreground after:opacity-0 after:transition-opacity aria-[current=page]:after:opacity-100 motion-reduce:after:transition-none",
  "focus-visible:ring-ring focus-visible:ring-2 focus-visible:ring-inset"
);

/**
 * For the `ScrollArea` around the row, rendered with `type="auto"`: Radix mounts the horizontal
 * scrollbar whenever the row overflows (not only while it is being scrolled), so a mouse with a
 * vertical wheel only can still drag it to reach hidden items. Radix positions the scrollbar
 * absolutely at the bottom of the root. Only while it is mounted, the root reserves a strip of the
 * same height (`pb-1.5` = `h-1.5`), so the scrollbar sits under the row and never covers the 2px
 * active underline.
 */
export const TAB_STRIP_SCROLL_AREA_CLASS =
  "w-full has-[[data-slot=scroll-area-scrollbar][data-orientation=horizontal]]:pb-1.5";
export const TAB_STRIP_SCROLLBAR_CLASS = "h-1.5";

/**
 * Edge fade overlays: shown only while there is content hidden past that edge. They stop above
 * the scrollbar strip (`bottom-1.5`), so they never wash out the thumb. They are only visible
 * while the row overflows, which is also when the scrollbar is mounted.
 */
export const TAB_EDGE_FADE_CLASS =
  "from-background pointer-events-none absolute top-0 bottom-1.5 w-8 opacity-0 transition-opacity data-[visible=true]:opacity-100 motion-reduce:transition-none";

/** Width of an edge fade (`w-8`); keeping an item in view clears it, not just the edge. */
export const TAB_EDGE_FADE_PX = 32;

/** The `ScrollArea` viewport inside `scrollArea` (the element that actually scrolls). */
export function findScrollViewport(scrollArea: HTMLElement | null) {
  return scrollArea?.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]') ?? null;
}

/**
 * Scroll the row horizontally just far enough that `item` sits fully clear of the edge fades.
 * A fade only covers an edge with hidden content past it, so near the row's ends the target clamps
 * (at 0 here, at the maximum by the browser) and that fade is gone. Only `scrollLeft` changes:
 * never `scrollIntoView`, which can also scroll the page vertically. Changing `scrollLeft` makes
 * the viewport fire `scroll`, and the edge-fade listener then refreshes the fades.
 *
 * Positions are measured with `getBoundingClientRect()`, relative to the scroll content (item
 * rect minus viewport rect, plus `scrollLeft`), not with `offsetLeft`/`offsetWidth`/`clientWidth`:
 * those round to whole pixels, which left an item up to a pixel short of clearing a fade. The
 * item's start is rounded down, its end up, and the viewport width down, so the integer target
 * always errs on the side of clearing the fade.
 */
export function scrollItemClearOfFades(viewport: HTMLElement, item: HTMLElement) {
  const { scrollLeft } = viewport;
  const viewportRect = viewport.getBoundingClientRect();
  const itemRect = item.getBoundingClientRect();
  const width = Math.floor(viewportRect.width);
  const start = Math.floor(itemRect.left - viewportRect.left + scrollLeft);
  const end = Math.ceil(itemRect.right - viewportRect.left + scrollLeft);
  if (start < scrollLeft + TAB_EDGE_FADE_PX) {
    const target = Math.max(0, start - TAB_EDGE_FADE_PX);
    if (target < scrollLeft) viewport.scrollLeft = target;
  } else if (end > scrollLeft + width - TAB_EDGE_FADE_PX) {
    const target = Math.min(start, end - width + TAB_EDGE_FADE_PX);
    if (target > scrollLeft) viewport.scrollLeft = target;
  }
}

/** Which edges currently have content hidden past them. */
export interface EdgeFades {
  start: boolean;
  end: boolean;
}
