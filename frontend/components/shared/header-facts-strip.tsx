import { cn } from "@/lib/utils";
import { HEADER_FIELD_PREFIXES, type HeaderField } from "@/lib/constants/header-field-slugs";

interface HeaderFactsStripProps {
  fields: HeaderField[];
  /** `data-testid` of the `dl`, e.g. "matter-header-fields". */
  testId: string;
  /** Each term/value pair gets `${itemTestIdPrefix}-${slug}`, e.g. "matter-header-field". */
  itemTestIdPrefix: string;
}

/**
 * Inline facts strip under a record title (Phase 88 D9), e.g.
 * "Case no. 1234/2026 · Gauteng Local Division · Road Accident Fund".
 *
 * Accessible `dl`: every value has a term — a visible short prefix (HEADER_FIELD_PREFIXES) or a
 * screen-reader-only field name. The "·" separators are decorative (aria-hidden).
 */
export function HeaderFactsStrip({ fields, testId, itemTestIdPrefix }: HeaderFactsStripProps) {
  if (fields.length === 0) return null;

  return (
    <dl
      className="flex flex-wrap items-baseline gap-x-1.5 gap-y-1 text-sm text-slate-600 dark:text-slate-400"
      data-testid={testId}
    >
      {fields.map((field, index) => {
        const prefix = HEADER_FIELD_PREFIXES[field.slug];
        const hasVisibleTerm = index > 0 || prefix !== undefined;
        return (
          <div
            key={field.slug}
            className="flex items-baseline"
            data-testid={`${itemTestIdPrefix}-${field.slug}`}
          >
            <dt className={cn("flex items-baseline gap-1.5", hasVisibleTerm && "mr-1.5")}>
              {index > 0 && (
                <span aria-hidden="true" className="text-slate-400 dark:text-slate-600">
                  ·
                </span>
              )}
              <span className={prefix ? "text-slate-500 dark:text-slate-400" : "sr-only"}>
                {prefix ?? field.label}
              </span>
            </dt>
            <dd className="font-medium text-slate-700 dark:text-slate-300">{field.value}</dd>
          </div>
        );
      })}
    </dl>
  );
}
