/**
 * Client-side replica of backend NameTokenResolver.java
 * Resolves name tokens in project template name patterns.
 * No {period_start}/{period_end} — scheduler-only tokens.
 */
export function resolveNameTokens(
  pattern: string,
  customerName?: string,
  referenceDate?: Date,
): string {
  let result = pattern;
  const date = referenceDate ?? new Date();

  if (customerName !== undefined) {
    result = result.replace(/\{customer\}/g, customerName);
  }

  // Full month name in English: "January", "February", etc.
  const monthFull = date.toLocaleDateString("en-US", { month: "long" });
  result = result.replace(/\{month\}/g, monthFull);

  // Short month name: "Jan", "Feb", etc.
  const monthShort = date.toLocaleDateString("en-US", { month: "short" });
  result = result.replace(/\{month_short\}/g, monthShort);

  // 4-digit year
  const year = String(date.getFullYear());
  result = result.replace(/\{year\}/g, year);

  return result;
}
