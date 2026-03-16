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
    setText(formatRelativeDate(iso));
  }, [iso]);

  return (
    <span className={className} suppressHydrationWarning>
      {text}
    </span>
  );
}
