"use client";

import { useTerminology, withIndefiniteArticle } from "@/lib/terminology";

interface TerminologyTextProps {
  /** Template string with {TermKey} placeholders, e.g. "No {proposals} yet" */
  template: string;
  className?: string;
}

/**
 * Client component that replaces `{TermKey}` placeholders with terminology-mapped values.
 * Placeholders must match keys in the terminology map (case-sensitive).
 *
 * Example: `<TerminologyText template="No {proposals} yet" />`
 * With accounting-za: "No engagement letters yet"
 * Without profile:    "No proposals yet"
 *
 * An article token `{a TermKey}` (or `{an TermKey}`) resolves the term first and
 * then emits the matching indefinite article, so vowel-initial substitutions read
 * correctly (LZKC-003): `{a proposal}` → "a proposal" / "an engagement letter".
 */
export function TerminologyText({ template, className }: TerminologyTextProps) {
  const { t } = useTerminology();
  const result = template.replace(
    /\{(?:(a|an) )?(\w[\w\s]*)\}/g,
    (_, article: string | undefined, key: string) => {
      const term = t(key);
      return article ? withIndefiniteArticle(term) : term;
    }
  );
  return className ? <span className={className}>{result}</span> : <>{result}</>;
}
