"use client";

import { useTerminology } from "@/lib/terminology";

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
 */
export function TerminologyText({ template, className }: TerminologyTextProps) {
  const { t } = useTerminology();
  const result = template.replace(/\{(\w[\w\s]*)\}/g, (_, key: string) => t(key));
  return className ? <span className={className}>{result}</span> : <>{result}</>;
}
