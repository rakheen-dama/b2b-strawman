"use client";

import { useCallback, useEffect, useState, type FocusEvent, type RefObject } from "react";
import {
  findScrollViewport,
  scrollItemClearOfFades,
  type EdgeFades,
} from "@/components/shared/tab-underline";

interface UseEdgeFadesOptions {
  /** The active item inside the scroll viewport, e.g. `a[aria-current="page"]`. */
  activeSelector: string;
  /** The row's focusable items, e.g. `[role="tab"]` — focus on anything else is ignored. */
  itemSelector: string;
  /** Changes whenever the active item changes; the new active item is then kept in view. */
  activeKey: string | null;
}

/**
 * Edge-fade state for a horizontal tab/link row in a `ScrollArea` (Phase 91 664A; the 642C
 * behaviour of grouped-tab-bar.tsx, for the settings family tab strip and family bar). Returns the
 * fade booleans for the `TAB_EDGE_FADE_CLASS` overlays and an `onFocus` handler for the row.
 *
 * - The fades follow the viewport (scroll + resize of the viewport or the row). Only an actual
 *   viewport width change (window narrowed, device rotated) also keeps an item clear of the fades —
 *   the focused item in the row if there is one, else the active item — so the user's own
 *   horizontal scroll is never fought.
 * - The active item is kept clear of the fades on mount and whenever `activeKey` changes (a deep
 *   link at a narrow width), by `scrollLeft` only — never `scrollIntoView`.
 * - A keyboard-focused item is kept clear of the fades too, so its focus ring is never hidden under
 *   a fade (WCAG 2.4.11). Focus from a mouse click (not `:focus-visible`) is left alone.
 */
export function useEdgeFades(
  scrollAreaRef: RefObject<HTMLElement | null>,
  { activeSelector, itemSelector, activeKey }: UseEdgeFadesOptions
) {
  const [fades, setFades] = useState<EdgeFades>({ start: false, end: false });

  useEffect(() => {
    const viewport = findScrollViewport(scrollAreaRef.current);
    if (!viewport) return;

    const update = () => {
      const { scrollLeft, scrollWidth, clientWidth } = viewport;
      const next = {
        start: scrollLeft > 0,
        end: scrollLeft + clientWidth < scrollWidth - 1,
      };
      setFades((prev) => (prev.start === next.start && prev.end === next.end ? prev : next));
    };

    let lastViewportWidth = viewport.clientWidth;
    const handleResize = () => {
      const width = viewport.clientWidth;
      if (width !== lastViewportWidth) {
        lastViewportWidth = width;
        // A keyboard-focused item in the row wins over the active one, so its focus ring stays
        // clear of the fades; the active item is the fallback when nothing in the row has focus.
        const focused = document.activeElement;
        const item =
          focused instanceof HTMLElement &&
          focused.matches(itemSelector) &&
          viewport.contains(focused)
            ? focused
            : viewport.querySelector<HTMLElement>(activeSelector);
        if (item) scrollItemClearOfFades(viewport, item);
      }
      update();
    };

    update();
    viewport.addEventListener("scroll", update, { passive: true });
    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(handleResize);
      observer.observe(viewport);
      // Radix wraps the row in one content element; it resizes with the row.
      const content = viewport.firstElementChild;
      if (content) observer.observe(content);
    }
    return () => {
      viewport.removeEventListener("scroll", update);
      observer?.disconnect();
    };
  }, [scrollAreaRef, activeSelector, itemSelector]);

  useEffect(() => {
    const viewport = findScrollViewport(scrollAreaRef.current);
    const active = viewport?.querySelector<HTMLElement>(activeSelector);
    if (viewport && active) scrollItemClearOfFades(viewport, active);
  }, [scrollAreaRef, activeSelector, activeKey]);

  const onFocus = useCallback(
    (e: FocusEvent<HTMLElement>) => {
      const item = e.target;
      if (!item.matches(itemSelector) || !item.matches(":focus-visible")) return;
      const viewport = findScrollViewport(scrollAreaRef.current);
      if (viewport?.contains(item)) scrollItemClearOfFades(viewport, item);
    },
    [scrollAreaRef, itemSelector]
  );

  return { fades, onFocus };
}
