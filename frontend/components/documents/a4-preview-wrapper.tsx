"use client";

import { useRef, useEffect, useState } from "react";
import { cn } from "@/lib/utils";

interface A4PreviewWrapperProps {
  html: string;
  className?: string;
}

const A4_WIDTH = 794; // px at 96 DPI
const A4_HEIGHT = 1123; // px at 96 DPI

export function A4PreviewWrapper({ html, className }: A4PreviewWrapperProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    // G-C4 (Phase 88 626F): size the page from the CONTENT box — contentRect excludes the p-6
    // padding. Reading the padding-inclusive clientWidth made the page 48px wider than the box it
    // sits in, which widened any host whose width follows its min-content (the SoA dialog's <form>
    // grid item), re-fired this observer, and grew without bound. ResizeObserver delivers an
    // initial notification on observe(), so there is no separate first read.
    const observer = new ResizeObserver((entries) => {
      const width = entries[entries.length - 1]?.contentRect.width ?? 0;
      if (width > 0) setScale(width / A4_WIDTH);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={containerRef}
      className={cn(
        // Dark surround — PDF viewer feel. min-w-0 + overflow-hidden: never widen the host (G-C4).
        "min-w-0 overflow-hidden rounded-lg bg-slate-800 p-6 dark:bg-slate-900",
        className
      )}
      data-testid="a4-preview-wrapper"
    >
      {/* Outer container sized to the scaled dimensions — prevents overlap with content below */}
      <div
        className="relative mx-auto"
        style={{
          width: A4_WIDTH * scale,
          height: A4_HEIGHT * scale,
        }}
      >
        {/* Inner container at full A4 size, scaled down via transform */}
        <div
          style={{
            width: A4_WIDTH,
            height: A4_HEIGHT,
            transform: `scale(${scale})`,
            transformOrigin: "top center",
            position: "absolute",
            top: 0,
            left: "50%",
            marginLeft: -(A4_WIDTH / 2),
          }}
        >
          {/* Paper effect */}
          <div className="h-full w-full bg-white shadow-xl ring-1 ring-slate-200/20">
            <iframe
              sandbox=""
              srcDoc={html}
              className="h-full w-full border-0"
              title="Document Preview"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
