"use client";

import { useEffect, useState } from "react";
import { formatRelativeDate } from "@/lib/format";

interface RelativeDateProps {
  iso: string;
  className?: string;
}

export function RelativeDate({ iso, className }: RelativeDateProps) {
  const [text, setText] = useState("");

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: relative dates depend on the current time which differs between server render and client mount; computing on the client via effect (with suppressHydrationWarning) avoids hydration mismatch.
    setText(formatRelativeDate(iso));
  }, [iso]);

  return (
    <span className={className} suppressHydrationWarning>
      {text}
    </span>
  );
}
