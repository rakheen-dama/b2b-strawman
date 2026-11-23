"use client";

import { useMemo, useState } from "react";
import useSWR from "swr";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  fetchTakeOnCustomFields,
  saveMapping,
  type TakeOnCustomFieldOption,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { takeOnMappingSchema } from "@/lib/schemas";
import {
  TAKE_ON_DATE_FORMATS,
  type TakeOnBatch,
  type TakeOnCustomFieldEntityType,
  type TakeOnDecimalSeparator,
  type TakeOnField,
  type TakeOnPreview,
  type TakeOnSource,
} from "@/lib/types";
import { useTakeOnPreview } from "./sheet-picker";
import { TakeOnError } from "./take-on-feedback";

const NOT_MAPPED = "__none__";
const IGNORE = "ignore";
const PARK = "park";
const CUSTOM = "custom:";

interface MappingStepProps {
  batchId: string;
  source: TakeOnSource;
  /** The catalogue fields of the source's section, in catalogue order. */
  fields: TakeOnField[];
  customFieldEntityType: TakeOnCustomFieldEntityType | null;
  onSaved: (batch: TakeOnBatch) => void;
}

/**
 * Column mapping of one file (Phase 93 653A, §17.3.2): each field takes a column or a default;
 * leftover columns fill a custom field, are kept as a note on the record, or are ignored; the
 * date format and decimal separator say how the dry run reads the cells.
 */
export function MappingStep(props: MappingStepProps) {
  const preview = useTakeOnPreview(props.batchId, props.source);
  if (preview.error) {
    return <TakeOnError message={String(preview.error.message)} testId="take-on-preview-error" />;
  }
  if (!preview.data) {
    return <p className="text-sm text-slate-600 dark:text-slate-400">Reading the file…</p>;
  }
  // Keyed: a re-read file (new sheet or header row) starts a fresh form.
  return (
    <MappingForm
      key={`${props.source.id}-${props.source.sheetName ?? ""}-${props.source.headerRow}`}
      {...props}
      preview={preview.data}
    />
  );
}

function initialDispositions(source: TakeOnSource): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [slug, header] of Object.entries(source.mapping?.customFields ?? {})) {
    result[header] = `${CUSTOM}${slug}`;
  }
  for (const header of source.mapping?.parked ?? []) {
    result[header] = PARK;
  }
  return result;
}

function MappingForm({
  batchId,
  source,
  fields,
  customFieldEntityType,
  onSaved,
  preview,
}: MappingStepProps & { preview: TakeOnPreview }) {
  const headers = preview.headers;
  const [targets, setTargets] = useState<Record<string, string>>(() => {
    const start = source.mapping?.targets ?? preview.suggestedTargets ?? {};
    return Object.fromEntries(Object.entries(start).filter(([, h]) => headers.includes(h)));
  });
  const [defaults, setDefaults] = useState<Record<string, string>>(
    () => source.mapping?.defaults ?? {}
  );
  const [dispositions, setDispositions] = useState<Record<string, string>>(() =>
    initialDispositions(source)
  );
  const [dateFormat, setDateFormat] = useState(source.parseOptions.dateFormat);
  const [decimalSeparator, setDecimalSeparator] = useState<TakeOnDecimalSeparator>(
    source.parseOptions.decimalSeparator
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const customFields = useSWR(
    customFieldEntityType ? `take-on-custom-fields-${customFieldEntityType}` : null,
    async () => {
      const result = await fetchTakeOnCustomFields(customFieldEntityType!);
      return result.success && result.data ? result.data : [];
    }
  );
  const customFieldOptions: TakeOnCustomFieldOption[] = customFields.data ?? [];

  const mappedHeaders = new Set(Object.values(targets));
  const leftovers = headers.filter((h) => !mappedHeaders.has(h));

  const customFieldMap: Record<string, string> = {};
  const parked: string[] = [];
  for (const header of leftovers) {
    const choice = dispositions[header] ?? IGNORE;
    if (choice === PARK) parked.push(header);
    else if (choice.startsWith(CUSTOM)) customFieldMap[choice.slice(CUSTOM.length)] = header;
  }
  const request = {
    targets,
    customFields: customFieldMap,
    parked,
    defaults: Object.fromEntries(Object.entries(defaults).filter(([, v]) => v.trim() !== "")),
  };

  const schema = useMemo(
    () =>
      takeOnMappingSchema(
        fields.filter((f) => f.required).map((f) => ({ key: f.key, label: f.label }))
      ),
    [fields]
  );
  const check = schema.safeParse(request);
  const issues = check.success ? [] : check.error.issues.map((i) => i.message);

  function setTarget(key: string, header: string) {
    setTargets((prev) => {
      const next = { ...prev };
      if (header === NOT_MAPPED) delete next[key];
      else next[key] = header;
      return next;
    });
  }

  async function save() {
    if (!check.success) return;
    setError(null);
    setBusy(true);
    try {
      const result = await saveMapping(batchId, source.id, {
        ...request,
        parseOptions: { dateFormat, decimalSeparator },
      });
      if (!result.success || !result.data) {
        setError(result.error ?? "The mapping could not be saved.");
        return;
      }
      onSaved(result.data);
    } finally {
      setBusy(false);
    }
  }

  const chosenSlugs = new Set(
    Object.values(dispositions)
      .filter((d) => d.startsWith(CUSTOM))
      .map((d) => d.slice(CUSTOM.length))
  );

  return (
    <div className="space-y-8" data-testid="take-on-mapping">
      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Fields</h3>
        <div className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {fields.map((field) => (
            <div
              key={field.key}
              className="grid gap-3 px-4 py-3 md:grid-cols-[minmax(0,14rem)_minmax(0,1fr)_minmax(0,14rem)] md:items-center"
            >
              <div className="flex items-center gap-2 text-sm text-slate-950 dark:text-slate-50">
                <span id={`take-on-field-${field.key}`}>{field.label}</span>
                {field.required && (
                  <Badge variant="warning" data-testid={`take-on-required-${field.key}`}>
                    Required
                  </Badge>
                )}
              </div>
              <Select
                value={targets[field.key] ?? NOT_MAPPED}
                onValueChange={(v) => setTarget(field.key, v)}
              >
                <SelectTrigger
                  aria-labelledby={`take-on-field-${field.key}`}
                  data-testid={`take-on-map-${field.key}`}
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NOT_MAPPED}>Not mapped</SelectItem>
                  {headers.map((h) => (
                    <SelectItem key={h} value={h}>
                      {h}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                aria-label={`Default value for ${field.label}`}
                placeholder="Default value"
                value={defaults[field.key] ?? ""}
                maxLength={500}
                onChange={(e) => setDefaults((prev) => ({ ...prev, [field.key]: e.target.value }))}
                data-testid={`take-on-default-${field.key}`}
              />
            </div>
          ))}
        </div>
      </section>

      {leftovers.length > 0 && (
        <section className="space-y-3" data-testid="take-on-leftovers">
          <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            Other columns in the file
          </h3>
          <div className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
            {leftovers.map((header) => {
              const index = headers.indexOf(header);
              return (
                <div
                  key={header}
                  className="grid gap-3 px-4 py-3 md:grid-cols-[minmax(0,14rem)_minmax(0,1fr)] md:items-center"
                >
                  <span id={`take-on-leftover-label-${index}`} className="text-sm">
                    {header}
                  </span>
                  <Select
                    value={dispositions[header] ?? IGNORE}
                    onValueChange={(v) => setDispositions((prev) => ({ ...prev, [header]: v }))}
                  >
                    <SelectTrigger
                      aria-labelledby={`take-on-leftover-label-${index}`}
                      data-testid={`take-on-leftover-${index}`}
                    >
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={IGNORE}>Ignore</SelectItem>
                      <SelectItem value={PARK}>Keep as a note on the record</SelectItem>
                      {customFieldOptions.map((cf) => (
                        <SelectItem
                          key={cf.slug}
                          value={`${CUSTOM}${cf.slug}`}
                          disabled={
                            chosenSlugs.has(cf.slug) &&
                            dispositions[header] !== `${CUSTOM}${cf.slug}`
                          }
                        >
                          Map to custom field: {cf.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="grid gap-6 md:grid-cols-2">
        <div className="space-y-2">
          <p
            id="take-on-date-format-label"
            className="text-sm font-medium text-slate-950 dark:text-slate-50"
          >
            Dates in the file look like
          </p>
          <Select value={dateFormat} onValueChange={setDateFormat}>
            <SelectTrigger
              aria-labelledby="take-on-date-format-label"
              data-testid="take-on-date-format"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TAKE_ON_DATE_FORMATS.map((f) => (
                <SelectItem key={f.value} value={f.value}>
                  {f.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-slate-950 dark:text-slate-50">
            Decimal separator
          </legend>
          <RadioGroup
            value={decimalSeparator}
            onValueChange={(v) => setDecimalSeparator(v as TakeOnDecimalSeparator)}
            aria-label="Decimal separator"
          >
            <div className="flex items-center gap-2">
              <RadioGroupItem
                value="POINT"
                id="take-on-decimal-point"
                data-testid="take-on-decimal-point"
              />
              <label htmlFor="take-on-decimal-point" className="text-sm">
                Point (1 234.56)
              </label>
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem
                value="COMMA"
                id="take-on-decimal-comma"
                data-testid="take-on-decimal-comma"
              />
              <label htmlFor="take-on-decimal-comma" className="text-sm">
                Comma (1 234,56)
              </label>
            </div>
          </RadioGroup>
        </fieldset>
      </section>

      {issues.length > 0 && (
        <ul
          data-testid="take-on-mapping-issues"
          className="list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-300"
        >
          {issues.map((m, i) => (
            <li key={i}>{m}</li>
          ))}
        </ul>
      )}
      {error && <TakeOnError message={error} testId="take-on-mapping-error" />}

      <Button
        type="button"
        variant="accent"
        disabled={busy || !check.success}
        onClick={save}
        data-testid="take-on-save-mapping"
      >
        {busy ? "Saving…" : "Save mapping"}
      </Button>
    </div>
  );
}
