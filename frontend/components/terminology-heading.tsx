"use client";

import { useTerminology } from "@/lib/terminology";

interface TerminologyHeadingProps {
  term: string;
  className?: string;
}

export function TerminologyHeading({ term, className }: TerminologyHeadingProps) {
  const { t } = useTerminology();
  return <span className={className}>{t(term)}</span>;
}
