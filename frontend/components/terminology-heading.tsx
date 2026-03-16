"use client";

import { useTerminology } from "@/lib/terminology";

interface TerminologyHeadingProps {
  term: string;
  className?: string;
  /** When provided, renders "{count} {singularOrPluralTerm}" */
  count?: number;
  /** Singular term used when count === 1 (defaults to term without trailing 's') */
  singularTerm?: string;
}

export function TerminologyHeading({
  term,
  className,
  count,
  singularTerm,
}: TerminologyHeadingProps) {
  const { t } = useTerminology();

  if (count !== undefined) {
    const singular = singularTerm ?? term.replace(/s$/, "");
    const displayTerm = count === 1 ? t(singular) : t(term);
    return (
      <span className={className}>
        {count} {displayTerm}
      </span>
    );
  }

  return <span className={className}>{t(term)}</span>;
}
