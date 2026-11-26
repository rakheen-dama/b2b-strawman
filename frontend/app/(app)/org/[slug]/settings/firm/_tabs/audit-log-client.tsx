"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Check, ChevronDown, ChevronRight, ChevronsUpDown, ScrollText } from "lucide-react";
import { Input } from "@b2mash/ui/input";
import { Button } from "@b2mash/ui/button";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/lib/format";
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
import { EntityCell, shortId } from "@/components/audit/entity-cell";
import { entityTypeLabel } from "@/components/audit/entity-type-labels";
import { isUuid } from "@/components/audit/filter-params";
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
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";

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
/** Stretch the SelectItem's ItemText span so an option's count can sit at the right edge (OD-6). */
const COUNTED_OPTION_CLASS = "*:[span]:last:min-w-0 *:[span]:last:flex-1";

/** Entity ID input check: the backend `entityId` filter is a UUID (R-6). */
function entityIdError(value: string): string | null {
  return isUuid(value) ? null : "Enter a full ID";
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
 * Actor options from the actor facets, grouped by actor type. Portal contacts carry only the
 * backend's static "Portal Contact" label, so each is named "Portal contact (<short id>)"; other
 * labels that repeat gain the same short-id suffix. The backend's "Former member (<uuid>)" is
 * shortened to 8 characters (the facet has no name-missing flag, so that one is a string match).
 * Rows with a NULL actor (System, webhooks) are not in the facet and cannot be filtered by actor.
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
    if (f.actorType === "PORTAL_CONTACT") {
      label = `Portal contact (${shortId(f.actorId)})`;
    } else if (label === `Former member (${f.actorId})`) {
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
      <span className="min-w-0 truncate">{option.label}</span>
      {option.count !== undefined && (
        <span className="ml-auto shrink-0 text-xs text-slate-400 tabular-nums">{option.count}</span>
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
  /** Error message for a non-empty value that must not be committed, or `null` when it is valid. */
  validate?: (value: string) => string | null;
}

/**
 * Free-text filter that commits on Enter as well as on blur. `lastCommittedRef` stops the blur
 * that follows an Enter from pushing the same value twice (the `initialValue` prop stays stale
 * until the server re-render lands). A non-empty value that fails `validate` is not committed: the
 * input shows an inline error instead (empty always commits, clearing the filter).
 */
function FilterTextInput({
  id,
  label,
  placeholder,
  initialValue,
  inputRef,
  onCommit,
  validate,
}: FilterTextInputProps) {
  const [error, setError] = useState<string | null>(null);
  const errorId = `${id}-error`;
  const lastCommittedRef = useRef(initialValue);
  useEffect(() => {
    lastCommittedRef.current = initialValue;
    // Keep the uncontrolled input in step with the URL (e.g. after "Clear filters").
    if (inputRef.current) inputRef.current.value = initialValue;
    // The input now shows the URL value, so an error about the previously typed value is stale.
    // eslint-disable-next-line react-hooks/set-state-in-effect -- syncs the error with the URL value; bounded by initialValue changes.
    setError(null);
  }, [initialValue, inputRef]);

  const commit = (raw: string) => {
    const v = raw.trim();
    const problem = v && validate ? validate(v) : null;
    setError(problem);
    if (problem) return;
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
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={() => {
          if (error) setError(null);
        }}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            commit(e.currentTarget.value);
          }
        }}
      />
      {error && (
        <p id={errorId} role="alert" className="text-xs text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
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

/** A query that looks like an event key (`matter_closure`, `invoice.sent`) rather than a word. */
function isKeyShaped(query: string): boolean {
  return query.includes(".") || query.includes("_");
}

/**
 * Event type filter: a searchable Popover + Command combobox over the event-type facets. Search
 * matches the legal label or the stored key (the key itself is never shown). Because the backend
 * matches `eventType` as a prefix, a typed query also offers "Event key starts with “…”", which
 * commits the typed text. Enter picks the first item, ordered so it is the one the user most
 * likely means:
 *  (a) the query is exactly a facet key → that option first (no prefix item);
 *  (b) a plain word (no `.` / `_`) with label matches → label matches first, then the prefix item;
 *  (c) otherwise (key-shaped, or no label match) → the prefix item first.
 * The trigger keeps the `audit-filter-event-type` id the e2e specs target.
 */
function EventTypeCombobox({ value, facets, presetEventCount, onCommit }: EventTypeComboboxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const q = query.trim();
  const qLower = q.toLowerCase();

  const exact = q ? facets.find((f) => f.eventType === q) : undefined;
  const labelHit = (f: EventTypeFacet) => f.label.toLowerCase().includes(qLower);
  const matches = q
    ? facets.filter(
        (f) => f !== exact && (labelHit(f) || f.eventType.toLowerCase().includes(qLower))
      )
    : [];
  const showPrefix = q !== "" && !exact;
  // Facet items rendered before / after the prefix item, per rules (a)–(c) above.
  let beforePrefix: EventTypeFacet[];
  let afterPrefix: EventTypeFacet[] = [];
  if (!q) {
    beforePrefix = facets;
  } else if (exact) {
    beforePrefix = [exact, ...matches];
  } else if (!isKeyShaped(q) && matches.some(labelHit)) {
    beforePrefix = matches.filter(labelHit);
    afterPrefix = matches.filter((f) => !labelHit(f));
  } else {
    beforePrefix = [];
    afterPrefix = matches;
  }

  const current = value ? facets.find((f) => f.eventType === value) : undefined;
  let triggerText: React.ReactNode;
  if (value === MULTI_EVENT_SENTINEL) {
    triggerText = `Preset (${presetEventCount ?? "multiple"} event types)`;
  } else if (current) {
    // Label only: stored event keys are searchable but never shown (G-D10b, ADR-357)
    triggerText = current.label;
  } else if (value) {
    // No facet for the active filter (a typed prefix, or the date range dropped it): describe the
    // filter the way the prefix option does — the backend matches eventType as a prefix.
    triggerText = `Event key starts with “${value}”`;
  } else {
    triggerText = "All event types";
  }

  const select = (next: string | undefined) => {
    setOpen(false);
    setQuery("");
    if ((next ?? "") !== (value ?? "")) onCommit(next);
  };

  const renderFacet = (f: EventTypeFacet) => (
    <CommandItem key={f.eventType} value={f.eventType} onSelect={() => select(f.eventType)}>
      <Check className={cn("size-4", value === f.eventType ? "opacity-100" : "opacity-0")} />
      <span className="min-w-0 flex-1 truncate">{f.label}</span>
      <span className="text-xs text-slate-400 tabular-nums">{f.count}</span>
    </CommandItem>
  );

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
        {/* cmdk names its input via aria-labelledby → a visually hidden <label> holding `label`
            (an aria-label on the input would be overridden). */}
        <Command shouldFilter={false} label="Search event types">
          <CommandInput placeholder="Search event types…" value={query} onValueChange={setQuery} />
          <CommandList>
            <CommandGroup>
              {!q && (
                <CommandItem value={ALL_VALUE} onSelect={() => select(undefined)}>
                  <Check className={cn("size-4", value ? "opacity-0" : "opacity-100")} />
                  All event types
                </CommandItem>
              )}
              {beforePrefix.map(renderFacet)}
              {showPrefix && (
                <CommandItem value={`__prefix__:${q}`} onSelect={() => select(q)}>
                  Event key starts with &ldquo;{q}&rdquo;
                </CommandItem>
              )}
              {afterPrefix.map(renderFacet)}
              {q && !exact && matches.length === 0 && (
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
        // Never flush a value the input refused to commit (FilterTextInput shows its error).
        if (v && !isUuid(v)) return;
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
  const currentActor = useMemo(() => {
    if (!currentActorId) return undefined;
    const known = ACTOR_GROUPS.flatMap((g) => actorGroups[g]).find(
      (o) => o.value === currentActorId
    );
    return known
      ? { option: known, unknown: false }
      : {
          option: { value: currentActorId, label: `Unknown actor (${shortId(currentActorId)})` },
          unknown: true,
        };
  }, [actorGroups, currentActorId]);

  const entityTypeOptions = useMemo(
    () => buildEntityTypeOptions(entityTypeFacets, initialFilter.entityType),
    [entityTypeFacets, initialFilter.entityType]
  );
  const currentEntityTypeLabel = initialFilter.entityType
    ? entityTypeLabel(initialFilter.entityType)
    : "All entity types";

  const columns = useMemo(
    () =>
      defineColumns<AuditEventResponse>([
        {
          id: "toggle",
          header: () => null,
          meta: { label: "", headerClassName: "w-10" },
          cell: ({ row }) => {
            const isExpanded = row.getIsExpanded();
            return (
              <button
                type="button"
                onClick={row.getToggleExpandedHandler()}
                aria-label={isExpanded ? "Collapse details" : "Expand details"}
                aria-expanded={isExpanded}
                data-testid={`audit-row-toggle-${row.original.id}`}
                className="text-muted-foreground hover:text-foreground"
              >
                {isExpanded ? (
                  <ChevronDown className="size-4" />
                ) : (
                  <ChevronRight className="size-4" />
                )}
              </button>
            );
          },
        },
        {
          id: "time",
          meta: {
            label: "Time",
            cellClassName: "font-mono text-xs whitespace-nowrap text-slate-700 dark:text-slate-300",
          },
          // SSR (UTC server) and hydration (browser) must print the same string → SA-pinned.
          cell: ({ row }) => formatDateTime(row.original.occurredAt),
        },
        {
          id: "severity",
          meta: { label: "Severity" },
          cell: ({ row }) => <SeverityPill severity={row.original.severity} />,
        },
        {
          id: "event",
          meta: { label: "Event", cellClassName: "text-xs" },
          // The label only: the stored key is never user-visible (ADR-357, G-D10a); it stays
          // searchable through the Event-type filter (G-D10b ruling).
          cell: ({ row }) => (
            <div className="text-foreground font-medium">{row.original.label}</div>
          ),
        },
        {
          id: "actor",
          meta: { label: "Actor" },
          cell: ({ row }) => (
            <ActorDisplay
              actorDisplayName={row.original.actorDisplayName}
              actorId={row.original.actorId}
              actorType={row.original.actorType}
              source={row.original.source}
              ipAddress={row.original.ipAddress}
            />
          ),
        },
        {
          id: "entity",
          meta: { label: "Entity" },
          cell: ({ row }) => (
            <EntityCell
              entityType={row.original.entityType}
              entityId={row.original.entityId}
              slug={slug}
              details={row.original.details}
            />
          ),
        },
      ]),
    [slug]
  );

  return (
    <div className="space-y-6">
      <SettingsSection title="Preset">
        <div className="space-y-3">
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
        </div>
      </SettingsSection>

      <SettingsSection title="Filters">
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
                <SelectValue>{currentActor?.option.label ?? "All actors"}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All actors</SelectItem>
                {currentActor?.unknown && (
                  <SelectItem value={currentActor.option.value}>
                    {currentActor.option.label}
                  </SelectItem>
                )}
                {ACTOR_GROUPS.filter((g) => actorGroups[g].length > 0).map((g) => (
                  <SelectGroup key={g}>
                    <SelectLabel>{g}</SelectLabel>
                    {actorGroups[g].map((o) => (
                      <SelectItem key={o.value} value={o.value} className={COUNTED_OPTION_CLASS}>
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
                  <SelectItem key={o.value} value={o.value} className={COUNTED_OPTION_CLASS}>
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
            validate={entityIdError}
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
      </SettingsSection>

      <SettingsSection
        title={
          <>
            {/* `{" "}`: word breaks in the h2's accessible name ("Events 289 total Loading…"). */}
            Events{" "}
            <span className="ml-2 font-mono text-xs font-normal text-slate-500">
              {page.totalElements.toLocaleString("en-ZA")} total
            </span>
            {isPending && (
              <>
                {" "}
                <span className="ml-2 text-xs text-slate-400">Loading…</span>
              </>
            )}
          </>
        }
      >
        {/* `expandedResetKey`: a new page collapses every open detail row, as before (the kit
            keeps expanded rows across a new `data` array, so a filter change on the same page
            keeps them), while the pager stays mounted and keeps its focus. The kit's `@container`
            frame keeps the wide table scrolling inside the section instead of widening the page
            (625D overflow baseline). */}
        <DataTable
          expandedResetKey={currentPage}
          columns={columns}
          data={content}
          getRowId={getEventId}
          rowTestId={auditRowTestId}
          getRowCanExpand={canExpand}
          renderExpandedRow={renderDetails}
          pagination={{ pageIndex: currentPage, pageCount: totalPages, onPageChange: goToPage }}
          paginationLabel="Audit log pagination"
          paginationShowSingle
          paginationEmptyLabel="No events"
          stickyHeader={false}
          frame={false}
          empty={
            hasActiveFilters ? (
              <EmptyState
                variant="compact"
                icon={ScrollText}
                title="No audit events in this range."
                description="Try widening the date range or changing filters."
              />
            ) : (
              <EmptyState
                variant="compact"
                icon={ScrollText}
                title="The audit log is empty."
                description="Activity is logged automatically once team members start working in Kazi."
              />
            )
          }
        />
      </SettingsSection>
    </div>
  );
}

const getEventId = (event: AuditEventResponse) => event.id;
const auditRowTestId = (event: AuditEventResponse) => `audit-row-${event.id}`;
const canExpand = () => true;

/** The detail row under an expanded event; `pl-10` aligns it under "Time" (past the toggle). */
function renderDetails(event: AuditEventResponse) {
  return (
    <div data-testid={`audit-row-details-${event.id}`} className="space-y-3 py-2 pl-10">
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
  );
}
