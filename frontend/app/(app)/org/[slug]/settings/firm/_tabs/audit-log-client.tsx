"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Check, ChevronDown, ChevronRight, ChevronsUpDown } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@b2mash/ui/input";
import { Button } from "@b2mash/ui/button";
import { cn } from "@/lib/utils";
import { formatComplianceDateWithTime } from "@/lib/format";
import type {
  ActorFacet,
  AuditEventFilter,
  AuditEventResponse,
  AuditEventsPage,
  AuditEventTypeMetadata,
  AuditSeverity,
  EntityTypeFacet,
  EventTypeFacet,
} from "@/lib/api/audit-events";
import { SeverityPill } from "@/components/audit/severity-pill";
import { ActorDisplay } from "@/components/audit/actor-display";
import { EntityCell } from "@/components/audit/entity-cell";
import { entityTypeLabel } from "@/components/audit/entity-type-labels";
import { AuditDetailsViewer } from "@/components/audit/audit-details-viewer";
import {
  MULTI_EVENT_SENTINEL,
  PRESET_OPTIONS,
  resolvePreset,
  type PresetName,
} from "@/components/audit/presets";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Command,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

const ALL_SEVERITIES: AuditSeverity[] = ["INFO", "NOTICE", "WARNING", "CRITICAL"];

const FILTER_KEYS = [
  "from",
  "to",
  "severities",
  "actorId",
  "eventType",
  "entityType",
  "entityId",
  "page",
] as const;

interface AuditLogClientProps {
  slug: string;
  initialEvents: AuditEventsPage;
  initialFilter: AuditEventFilter;
  /**
   * Audit-event metadata catalogue. Used by `applyPreset()` to resolve
   * `group=COMPLIANCE` / `group=SECURITY` to concrete event-type lists.
   * Defaults to `[]` so existing tests that don't supply metadata still work.
   */
  metadata?: AuditEventTypeMetadata[];
  /**
   * 625D: audit facets over the list's range, fetched by the RSC loader. They feed the Actor and
   * Entity type Selects and the Event type combobox. Default `[]` (options then hold only "All …"
   * plus the URL's current value).
   */
  actorFacets?: ActorFacet[];
  entityTypeFacets?: EntityTypeFacet[];
  eventTypeFacets?: EventTypeFacet[];
}

/** Radix Select forbids an empty-string item value; "All …" uses this sentinel. */
const ALL_VALUE = "__all__";

const FILTER_FIELD_CLASS = "min-w-0 flex-[1_1_14rem] space-y-1";
const FILTER_LABEL_CLASS = "text-xs font-medium text-slate-700 dark:text-slate-300";

function shortId(id: string): string {
  return id.slice(0, 8);
}

interface FacetOption {
  value: string;
  label: string;
  count?: number;
}

type ActorGroupName = "Members" | "Portal contacts" | "System";
const ACTOR_GROUPS: ActorGroupName[] = ["Members", "Portal contacts", "System"];

function actorGroupOf(actorType: string): ActorGroupName {
  if (actorType === "USER") return "Members";
  if (actorType === "PORTAL_CONTACT") return "Portal contacts";
  return "System";
}

/**
 * Actor options from the actor facets, grouped by actor type. Labels that repeat (every portal
 * contact is "Portal Contact") gain a short-id suffix, and the backend's
 * "Former member (<uuid>)" is shortened to 8 characters. Rows with a NULL actor (System, webhooks)
 * are not in the facet and cannot be filtered by actor.
 */
function buildActorOptions(facets: ActorFacet[]): Record<ActorGroupName, FacetOption[]> {
  const labelCounts = new Map<string, number>();
  for (const f of facets) {
    labelCounts.set(f.actorDisplayName, (labelCounts.get(f.actorDisplayName) ?? 0) + 1);
  }
  const groups: Record<ActorGroupName, FacetOption[]> = {
    Members: [],
    "Portal contacts": [],
    System: [],
  };
  for (const f of facets) {
    let label = f.actorDisplayName;
    if (label === `Former member (${f.actorId})`) {
      label = `Former member (${shortId(f.actorId)})`;
    } else if ((labelCounts.get(label) ?? 0) > 1) {
      label = `${label} (${shortId(f.actorId)})`;
    }
    groups[actorGroupOf(f.actorType)].push({ value: f.actorId, label, count: f.eventCount });
  }
  return groups;
}

/** Entity type options labelled through the legal label map, alphabetical by label. */
function buildEntityTypeOptions(facets: EntityTypeFacet[], current?: string): FacetOption[] {
  const options: FacetOption[] = facets.map((f) => ({
    value: f.entityType,
    label: entityTypeLabel(f.entityType, f.label),
    count: f.count,
  }));
  if (current && !options.some((o) => o.value === current)) {
    options.push({ value: current, label: entityTypeLabel(current) });
  }
  return options.sort((a, b) => a.label.localeCompare(b.label));
}

function OptionText({ option }: { option: FacetOption }) {
  return (
    <>
      <span className="truncate">{option.label}</span>
      {option.count !== undefined && (
        <span className="text-xs text-slate-400 tabular-nums">{option.count}</span>
      )}
    </>
  );
}

function isoDateInputValue(iso: string | undefined): string {
  if (!iso) return "";
  // Slice down to YYYY-MM-DD for the <input type="date"> bind
  return iso.length >= 10 ? iso.slice(0, 10) : iso;
}

interface FilterTextInputProps {
  id: string;
  label: string;
  placeholder?: string;
  initialValue: string;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onCommit: (value: string) => void;
}

/**
 * Free-text filter that commits on Enter as well as on blur. `lastCommittedRef` stops the blur
 * that follows an Enter from pushing the same value twice (the `initialValue` prop stays stale
 * until the server re-render lands).
 */
function FilterTextInput({
  id,
  label,
  placeholder,
  initialValue,
  inputRef,
  onCommit,
}: FilterTextInputProps) {
  const lastCommittedRef = useRef(initialValue);
  useEffect(() => {
    lastCommittedRef.current = initialValue;
    // Keep the uncontrolled input in step with the URL (e.g. after "Clear filters").
    if (inputRef.current) inputRef.current.value = initialValue;
  }, [initialValue, inputRef]);

  const commit = (raw: string) => {
    const v = raw.trim();
    if (v === lastCommittedRef.current) return;
    lastCommittedRef.current = v;
    onCommit(v);
  };

  return (
    <div className={FILTER_FIELD_CLASS}>
      <label htmlFor={id} className={FILTER_LABEL_CLASS}>
        {label}
      </label>
      <Input
        id={id}
        ref={inputRef}
        type="text"
        placeholder={placeholder}
        defaultValue={initialValue}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit(e.currentTarget.value);
          }
        }}
      />
    </div>
  );
}

interface EventTypeComboboxProps {
  value: string | undefined;
  facets: EventTypeFacet[];
  /** Resolved event-type count of an active group preset (sentinel value), when known. */
  presetEventCount: number | undefined;
  onCommit: (eventType: string | undefined) => void;
}

/**
 * Event type filter: a searchable Popover + Command combobox over the event-type facets. Search
 * matches the legal label or the stored key. Because the backend matches `eventType` as a prefix,
 * the first item for a typed query is "Starts with “…”", which commits the typed text (Enter
 * picks it). The trigger keeps the `audit-filter-event-type` id the e2e specs target.
 */
function EventTypeCombobox({ value, facets, presetEventCount, onCommit }: EventTypeComboboxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const q = query.trim();
  const qLower = q.toLowerCase();

  const exact = q ? facets.find((f) => f.eventType === q) : undefined;
  const matches = q
    ? facets.filter(
        (f) =>
          f !== exact &&
          (f.label.toLowerCase().includes(qLower) || f.eventType.toLowerCase().includes(qLower))
      )
    : facets;
  const items = exact ? [exact, ...matches] : matches;

  const current = value ? facets.find((f) => f.eventType === value) : undefined;
  let triggerText: React.ReactNode;
  if (value === MULTI_EVENT_SENTINEL) {
    triggerText = `Preset (${presetEventCount ?? "multiple"} event types)`;
  } else if (current) {
    triggerText = (
      <>
        {current.label}{" "}
        <span className="font-mono text-[10px] text-slate-500">{current.eventType}</span>
      </>
    );
  } else if (value) {
    triggerText = <span className="font-mono text-xs">{value}</span>;
  } else {
    triggerText = "All event types";
  }

  const select = (next: string | undefined) => {
    setOpen(false);
    setQuery("");
    if ((next ?? "") !== (value ?? "")) onCommit(next);
  };

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setQuery("");
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id="audit-filter-event-type"
          type="button"
          variant="plain"
          role="combobox"
          aria-expanded={open}
          className="h-8 w-full justify-between rounded-md border border-slate-200 bg-white px-3 font-normal dark:border-slate-800 dark:bg-slate-950"
        >
          <span className="truncate">{triggerText}</span>
          <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[22rem] max-w-[calc(100vw-2rem)] p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput placeholder="Search event types…" value={query} onValueChange={setQuery} />
          <CommandList>
            <CommandGroup>
              {q && !exact && (
                <CommandItem value={`__prefix__:${q}`} onSelect={() => select(q)}>
                  Starts with &ldquo;{q}&rdquo;
                </CommandItem>
              )}
              {!q && (
                <CommandItem value={ALL_VALUE} onSelect={() => select(undefined)}>
                  <Check className={cn("size-4", value ? "opacity-0" : "opacity-100")} />
                  All event types
                </CommandItem>
              )}
              {items.map((f) => (
                <CommandItem
                  key={f.eventType}
                  value={f.eventType}
                  onSelect={() => select(f.eventType)}
                >
                  <Check
                    className={cn("size-4", value === f.eventType ? "opacity-100" : "opacity-0")}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate">{f.label}</span>
                    <span className="block truncate font-mono text-[10px] text-slate-500">
                      {f.eventType}
                    </span>
                  </span>
                  <span className="text-xs text-slate-400 tabular-nums">{f.count}</span>
                </CommandItem>
              ))}
              {q && items.length === 0 && (
                <p className="px-2 py-1.5 text-xs text-slate-500">No matching event types.</p>
              )}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export function AuditLogClient({
  slug,
  initialEvents,
  initialFilter,
  metadata = [],
  actorFacets = [],
  entityTypeFacets = [],
  eventTypeFacets = [],
}: AuditLogClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [isPending, startTransition] = useTransition();
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  /**
   * When a group-preset (Compliance/Security/Financial approvals) is active,
   * this holds the resolved event-type list so the banner can list them. The
   * URL param `eventType=__multi__` is the persistence signal — but we keep
   * the resolved list in component state because it's not encoded in the URL.
   * Cleared when the user mutates filters in any way that drops the sentinel.
   */
  const [multiEventBanner, setMultiEventBanner] = useState<{
    presetLabel: string;
    eventTypes: string[];
    metadataMissing: boolean;
  } | null>(null);

  const events = initialEvents;
  const { content, page } = events;
  const currentPage = page.number;
  const totalPages = page.totalPages;

  // Ref for the one remaining text input (Entity ID) so that filter actions which fire WITHOUT
  // blurring an in-progress input first (e.g. clicking a severity chip, paging) can flush the
  // typed value into the URL. The Actor / Entity type Selects and the Event type combobox commit
  // immediately.
  const entityIdRef = useRef<HTMLInputElement | null>(null);

  // Phase 87 S9 (616A, ADR-341): the audit trail is the `audit-trail` tab of the Firm settings
  // family; `updateUrl` pins `tab=audit-trail` so filter changes stay on this tab.
  const buildPath = useCallback(
    (qs: string) => `/org/${slug}/settings/firm${qs ? `?${qs}` : ""}`,
    [slug]
  );

  // Read the latest searchParams inside the call rather than capturing it in
  // closure — this avoids concurrent updates clobbering each other when the
  // user fires multiple filter changes back-to-back inside a transition.
  const searchParamsRef = useRef(searchParams);
  useEffect(() => {
    searchParamsRef.current = searchParams;
  }, [searchParams]);

  const updateUrl = useCallback(
    (mutator: (params: URLSearchParams) => void) => {
      const latest = searchParamsRef.current;
      const params = new URLSearchParams(latest?.toString() ?? "");
      mutator(params);
      params.set("tab", "audit-trail");
      const qs = params.toString();
      startTransition(() => {
        router.push(buildPath(qs), { scroll: false });
      });
    },
    [router, buildPath]
  );

  // Flush any pending in-progress text input into the URLSearchParams before
  // applying further mutations. Called by handlers that aren't already a blur
  // on a text input (severity toggle, clear, pagination).
  const flushPendingInputs = useCallback(
    (params: URLSearchParams) => {
      const flush = (
        key: string,
        ref: React.RefObject<HTMLInputElement | null>,
        prev: string | undefined
      ) => {
        const node = ref.current;
        if (!node) return;
        const v = node.value.trim();
        if (v === (prev ?? "")) return;
        if (v) params.set(key, v);
        else params.delete(key);
      };
      flush("entityId", entityIdRef, initialFilter.entityId);
    },
    [initialFilter.entityId]
  );

  const setParam = useCallback(
    (key: string, value: string | undefined) => {
      updateUrl((params) => {
        if (value === undefined || value === "") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
        // Filter changes reset pagination
        params.delete("page");
      });
    },
    [updateUrl]
  );

  const toggleSeverity = useCallback(
    (sev: AuditSeverity) => {
      const current = new Set(initialFilter.severities ?? []);
      if (current.has(sev)) {
        current.delete(sev);
      } else {
        current.add(sev);
      }
      const arr = ALL_SEVERITIES.filter((s) => current.has(s));
      updateUrl((params) => {
        flushPendingInputs(params);
        if (arr.length > 0) {
          params.set("severities", arr.join(","));
        } else {
          params.delete("severities");
        }
        params.delete("page");
      });
    },
    [initialFilter.severities, updateUrl, flushPendingInputs]
  );

  const goToPage = useCallback(
    (next: number) => {
      updateUrl((params) => {
        flushPendingInputs(params);
        if (next <= 0) params.delete("page");
        else params.set("page", String(next));
      });
    },
    [updateUrl, flushPendingInputs]
  );

  const toggleRow = useCallback((id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  // Clear stale expanded ids when the page (or filter set) changes — the
  // event ids on the new page are different. Deps only change on server
  // refresh, so this can't cause a render loop.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Resets ephemeral UI selection on page change; not a render loop.
    setExpanded(new Set());
  }, [currentPage]);

  // Drop the multi-event banner if the URL no longer carries the sentinel
  // (e.g. the user typed a real event-type, hit Clear filters, or applied a
  // single-eventType preset).
  useEffect(() => {
    if (initialFilter.eventType !== MULTI_EVENT_SENTINEL) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- syncs banner with URL param; bounded by initialFilter changes.
      setMultiEventBanner(null);
    }
  }, [initialFilter.eventType]);

  const clearFilters = useCallback(() => {
    updateUrl((params) => {
      // Don't flush pending inputs — the user explicitly asked to clear.
      FILTER_KEYS.forEach((k) => params.delete(k));
    });
  }, [updateUrl]);

  // Epic 506B / 506.9 — preset application. Presets replace (not stack) all
  // filter keys, then set the preset's deltas. All four are URL mutations only;
  // no backend round-trip.
  //
  // Group presets (Compliance, Security, Financial approvals) resolve to
  // multiple event types but the backend list endpoint only accepts a single
  // `eventType` param. Rather than silently narrowing to the first match
  // (which would mislead the user into thinking they're viewing all preset
  // events), we set `eventType` to a sentinel that yields zero results and
  // surface a banner listing the resolved event types. Single-eventType
  // presets (none currently — Sensitive uses severities only) work as normal.
  // TODO(506B-followup): replace sentinel with a multi-value backend filter.
  const applyPreset = useCallback(
    (preset: PresetName) => {
      const def = resolvePreset(preset, metadata);
      const presetLabel = PRESET_OPTIONS.find((o) => o.value === preset)?.label ?? preset;
      const eventTypes = def.eventTypes ?? [];
      const isGroupPreset = def.isGroupPreset === true;

      updateUrl((params) => {
        FILTER_KEYS.forEach((k) => params.delete(k));
        if (def.from) params.set("from", def.from);
        if (def.severities && def.severities.length > 0) {
          params.set("severities", def.severities.join(","));
        }
        if (isGroupPreset) {
          // Fail-closed: regardless of how many event types resolved (zero
          // when metadata is missing/failed, many when present), use the
          // sentinel so the user never sees a misleadingly narrowed list.
          params.set("eventType", MULTI_EVENT_SENTINEL);
        } else if (eventTypes.length === 1) {
          params.set("eventType", eventTypes[0]);
        }
      });

      if (isGroupPreset) {
        setMultiEventBanner({
          presetLabel,
          eventTypes,
          metadataMissing: eventTypes.length === 0,
        });
      } else {
        setMultiEventBanner(null);
      }
    },
    [updateUrl, metadata]
  );

  const hasActiveFilters = useMemo(
    () =>
      Boolean(
        initialFilter.from ||
        initialFilter.to ||
        (initialFilter.severities && initialFilter.severities.length > 0) ||
        initialFilter.actorId ||
        initialFilter.eventType ||
        initialFilter.entityType ||
        initialFilter.entityId
      ),
    [initialFilter]
  );

  const selectedSeverities = useMemo(
    () => new Set(initialFilter.severities ?? []),
    [initialFilter.severities]
  );

  const actorGroups = useMemo(() => buildActorOptions(actorFacets), [actorFacets]);
  const currentActorId = initialFilter.actorId;
  const currentActorOption = useMemo(() => {
    if (!currentActorId) return undefined;
    const known = ACTOR_GROUPS.flatMap((g) => actorGroups[g]).find(
      (o) => o.value === currentActorId
    );
    return known ?? { value: currentActorId, label: `Unknown actor (${shortId(currentActorId)})` };
  }, [actorGroups, currentActorId]);
  const actorIsUnknown =
    currentActorOption !== undefined &&
    !ACTOR_GROUPS.some((g) => actorGroups[g].some((o) => o.value === currentActorOption.value));

  const entityTypeOptions = useMemo(
    () => buildEntityTypeOptions(entityTypeFacets, initialFilter.entityType),
    [entityTypeFacets, initialFilter.entityType]
  );
  const currentEntityTypeLabel = initialFilter.entityType
    ? entityTypeLabel(initialFilter.entityType)
    : "All entity types";

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Preset</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <label htmlFor="audit-preset-select" className="sr-only">
            Filter preset
          </label>
          {/*
            Shadcn Select keyed by `multiEventBanner` so picking the same
            preset twice in a row still fires onValueChange (the component
            unmounts/remounts with a fresh empty value).
          */}
          <Select
            key={multiEventBanner?.presetLabel ?? "no-preset"}
            value=""
            onValueChange={(v) => {
              if (v) applyPreset(v as PresetName);
            }}
          >
            <SelectTrigger
              id="audit-preset-select"
              data-testid="audit-preset-select"
              size="sm"
              className="w-72"
            >
              <SelectValue placeholder="— Choose a preset —" />
            </SelectTrigger>
            <SelectContent>
              {PRESET_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {multiEventBanner && (
            <div
              role="status"
              data-testid="audit-preset-multi-event-banner"
              className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-200"
            >
              {multiEventBanner.metadataMissing ? (
                <>
                  <span className="font-medium">
                    Preset &ldquo;{multiEventBanner.presetLabel}&rdquo;
                  </span>{" "}
                  could not resolve any event types (metadata unavailable). Results temporarily
                  empty &mdash; backend multi-value filter pending.
                </>
              ) : (
                <>
                  <span className="font-medium">
                    Preset &ldquo;{multiEventBanner.presetLabel}&rdquo;
                  </span>{" "}
                  filters to {multiEventBanner.eventTypes.length} event{" "}
                  {multiEventBanner.eventTypes.length === 1 ? "type" : "types"}:{" "}
                  <span className="font-mono">{multiEventBanner.eventTypes.join(", ")}</span>{" "}
                  &mdash; backend multi-value filter pending; results temporarily empty.
                </>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4" data-testid="audit-filter-row">
            <div className={FILTER_FIELD_CLASS}>
              <label htmlFor="audit-filter-from" className={FILTER_LABEL_CLASS}>
                From
              </label>
              {/* UTC-day semantics: we map the picker's local YYYY-MM-DD to
                  the start of that UTC day (00:00:00.000Z). */}
              <Input
                id="audit-filter-from"
                type="date"
                value={isoDateInputValue(initialFilter.from)}
                onChange={(e) =>
                  setParam(
                    "from",
                    e.target.value
                      ? new Date(e.target.value + "T00:00:00.000Z").toISOString()
                      : undefined
                  )
                }
              />
            </div>
            <div className={FILTER_FIELD_CLASS}>
              <label htmlFor="audit-filter-to" className={FILTER_LABEL_CLASS}>
                To
              </label>
              {/* UTC-day semantics: pin the upper bound to the very last
                  millisecond of the chosen UTC day so events that occur in
                  the final second of the day are not dropped. */}
              <Input
                id="audit-filter-to"
                type="date"
                value={isoDateInputValue(initialFilter.to)}
                onChange={(e) =>
                  setParam(
                    "to",
                    e.target.value
                      ? new Date(e.target.value + "T23:59:59.999Z").toISOString()
                      : undefined
                  )
                }
              />
            </div>
            <div className={FILTER_FIELD_CLASS}>
              <label htmlFor="audit-filter-actor" className={FILTER_LABEL_CLASS}>
                Actor
              </label>
              <Select
                value={currentActorId ?? ALL_VALUE}
                onValueChange={(v) => setParam("actorId", v === ALL_VALUE ? undefined : v)}
              >
                <SelectTrigger id="audit-filter-actor" size="sm" className="w-full">
                  <SelectValue>{currentActorOption?.label ?? "All actors"}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>All actors</SelectItem>
                  {actorIsUnknown && currentActorOption && (
                    <SelectItem value={currentActorOption.value}>
                      {currentActorOption.label}
                    </SelectItem>
                  )}
                  {ACTOR_GROUPS.filter((g) => actorGroups[g].length > 0).map((g) => (
                    <SelectGroup key={g}>
                      <SelectLabel>{g}</SelectLabel>
                      {actorGroups[g].map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          <OptionText option={o} />
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className={FILTER_FIELD_CLASS}>
              <label htmlFor="audit-filter-entity-type" className={FILTER_LABEL_CLASS}>
                Entity type
              </label>
              {/* ADR-342: the value is always the stored literal (`project`); only the label is
                  the legal word ("Matter"). */}
              <Select
                value={initialFilter.entityType ?? ALL_VALUE}
                onValueChange={(v) => setParam("entityType", v === ALL_VALUE ? undefined : v)}
              >
                <SelectTrigger id="audit-filter-entity-type" size="sm" className="w-full">
                  <SelectValue>{currentEntityTypeLabel}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>All entity types</SelectItem>
                  {entityTypeOptions.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      <OptionText option={o} />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className={FILTER_FIELD_CLASS}>
              <label htmlFor="audit-filter-event-type" className={FILTER_LABEL_CLASS}>
                Event type
              </label>
              <EventTypeCombobox
                value={initialFilter.eventType}
                facets={eventTypeFacets}
                presetEventCount={multiEventBanner?.eventTypes.length || undefined}
                onCommit={(v) => setParam("eventType", v)}
              />
            </div>
            <FilterTextInput
              id="audit-filter-entity-id"
              label="Entity ID"
              placeholder="UUID"
              initialValue={initialFilter.entityId ?? ""}
              inputRef={entityIdRef}
              onCommit={(v) => setParam("entityId", v || undefined)}
            />
          </div>

          <div className="mt-4">
            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">Severity</span>
            <div className="mt-2 flex flex-wrap gap-2" role="group" aria-label="Severity filter">
              {ALL_SEVERITIES.map((sev) => {
                const active = selectedSeverities.has(sev);
                return (
                  <button
                    key={sev}
                    type="button"
                    onClick={() => toggleSeverity(sev)}
                    aria-pressed={active}
                    data-testid={`severity-toggle-${sev}`}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs transition",
                      active
                        ? "border-teal-600 bg-teal-50 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                        : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400"
                    )}
                  >
                    {sev}
                  </button>
                );
              })}
            </div>
          </div>

          {hasActiveFilters && (
            <div className="mt-4">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={clearFilters}
                data-testid="audit-clear-filters"
              >
                Clear filters
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            Events
            <span className="ml-2 font-mono text-xs font-normal text-slate-500">
              {page.totalElements.toLocaleString()} total
            </span>
            {isPending && <span className="ml-2 text-xs text-slate-400">Loading…</span>}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {content.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">
              {hasActiveFilters
                ? "No audit events in this range. Try widening the date range or changing filters."
                : "The audit log is empty. Activity is logged automatically once team members start working in Kazi."}
            </p>
          ) : (
            // `contain-inline-size`: the table scrolls inside its own wrapper instead of widening
            // the page — its nowrap min-content otherwise propagated through the app shell and
            // overflowed the page at 1280 px (625D overflow baseline).
            <div className="contain-inline-size">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-10"></TableHead>
                    <TableHead>Time</TableHead>
                    <TableHead>Severity</TableHead>
                    <TableHead>Event</TableHead>
                    <TableHead>Actor</TableHead>
                    <TableHead>Entity</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {content.map((event: AuditEventResponse) => {
                    const isExpanded = expanded.has(event.id);
                    return (
                      <Row
                        key={event.id}
                        event={event}
                        slug={slug}
                        expanded={isExpanded}
                        onToggle={() => toggleRow(event.id)}
                      />
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <nav
        aria-label="Audit log pagination"
        className="flex items-center justify-between gap-3 text-sm"
      >
        <div className="text-slate-500">
          {totalPages > 0 ? (
            <>
              Page <span className="font-medium">{currentPage + 1}</span> of {totalPages}
            </>
          ) : (
            <>No events</>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={currentPage <= 0}
            onClick={() => goToPage(currentPage - 1)}
            className={cn(
              "rounded-md border px-3 py-1.5",
              currentPage > 0
                ? "border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                : "cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-800"
            )}
          >
            Previous
          </button>
          <button
            type="button"
            disabled={currentPage + 1 >= totalPages}
            onClick={() => goToPage(currentPage + 1)}
            className={cn(
              "rounded-md border px-3 py-1.5",
              currentPage + 1 < totalPages
                ? "border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                : "cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-800"
            )}
          >
            Next
          </button>
        </div>
      </nav>
    </div>
  );
}

interface RowProps {
  event: AuditEventResponse;
  slug: string;
  expanded: boolean;
  onToggle: () => void;
}

function Row({ event, slug, expanded, onToggle }: RowProps) {
  return (
    <>
      <TableRow data-testid={`audit-row-${event.id}`}>
        <TableCell>
          <button
            type="button"
            onClick={onToggle}
            aria-label={expanded ? "Collapse details" : "Expand details"}
            aria-expanded={expanded}
            data-testid={`audit-row-toggle-${event.id}`}
            className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
          >
            {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
          </button>
        </TableCell>
        <TableCell className="font-mono text-xs whitespace-nowrap text-slate-700 dark:text-slate-300">
          {formatComplianceDateWithTime(event.occurredAt)}
        </TableCell>
        <TableCell>
          <SeverityPill severity={event.severity} />
        </TableCell>
        <TableCell className="text-xs">
          <div className="font-medium text-slate-900 dark:text-slate-100">{event.label}</div>
          <div className="font-mono text-[10px] text-slate-500">{event.eventType}</div>
        </TableCell>
        <TableCell>
          <ActorDisplay
            actorDisplayName={event.actorDisplayName}
            actorId={event.actorId}
            actorType={event.actorType}
            source={event.source}
            ipAddress={event.ipAddress}
          />
        </TableCell>
        <TableCell>
          <EntityCell
            entityType={event.entityType}
            entityId={event.entityId}
            slug={slug}
            details={event.details}
          />
        </TableCell>
      </TableRow>
      {expanded && (
        <TableRow data-testid={`audit-row-details-${event.id}`}>
          <TableCell></TableCell>
          <TableCell colSpan={5} className="bg-slate-50/50 dark:bg-slate-900/30">
            <div className="space-y-3 py-2">
              <AuditDetailsViewer details={event.details} />
              <dl className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
                {event.source && (
                  <div>
                    <dt className="inline font-semibold">Source: </dt>
                    <dd className="inline font-mono">{event.source}</dd>
                  </div>
                )}
                {event.ipAddress && (
                  <div>
                    <dt className="inline font-semibold">IP: </dt>
                    <dd className="inline font-mono">{event.ipAddress}</dd>
                  </div>
                )}
                {event.userAgent && (
                  <div>
                    <dt className="inline font-semibold">Agent: </dt>
                    <dd className="inline font-mono">{event.userAgent}</dd>
                  </div>
                )}
              </dl>
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
