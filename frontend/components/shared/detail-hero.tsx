import Link from "next/link";
import type { ReactNode } from "react";
import { Card } from "@b2mash/ui/card";
import { StatCard } from "@b2mash/ui/stat-card";
import { cn } from "@/lib/utils";

/**
 * One KPI tile in a detail page's hero strip (ADR-373). Serializable across the RSC boundary
 * (OQ #26): `value` / `hint` are formatted strings or pre-rendered JSX built by the server loader,
 * never component references. A tile whose gate fails is omitted by the caller, never sent as "—".
 */
export interface HeroTile {
  id: string;
  label: string;
  value: ReactNode | null;
  hint?: ReactNode;
  href?: string;
  state: "ready" | "redacted" | "error";
}

interface DetailHeroProps {
  /** Mono chip (matter reference / client number); omitted when absent. */
  identifier?: ReactNode;
  /** The page's single <h1>. */
  title: string;
  /** e.g. "matter-name" / "client-name". */
  titleTestId?: string;
  subtitle?: ReactNode;
  /** The existing status badges, passed in unchanged. */
  status?: ReactNode;
  /** The existing header actions, passed in unchanged. */
  actions?: ReactNode;
  /** The existing <HeaderFactsStrip />, pre-rendered. */
  facts?: ReactNode;
  /** 0–4 tiles, rendered as compact StatCards. */
  tiles?: HeroTile[];
  className?: string;
  /** e.g. "matter-header-card" / "client-header-card". */
  "data-testid"?: string;
}

// Static literals so Tailwind sees every class. Fewer tiles shrink the strip (ADR-373): the
// grid never leaves a hole for an omitted tile. 4 tiles = 2×2 below @3xl, 4×1 at @3xl.
const TILE_COLUMNS: Record<number, string> = {
  1: "grid-cols-1",
  2: "grid-cols-2 @3xl:grid-cols-2",
  3: "grid-cols-2 @3xl:grid-cols-3",
  4: "grid-cols-2 @3xl:grid-cols-4",
};

function hasContent(node: ReactNode): boolean {
  return node !== null && node !== undefined && node !== false && node !== "";
}

/**
 * Shared detail-page hero (Phase 90 §15.2.4 / ADR-373): identity, status, actions, facts and a
 * strip of up to four compact StatCard tiles. No "use client" and no server-only import, so the
 * "use client" header cards can render it.
 */
export function DetailHero({
  identifier,
  title,
  titleTestId,
  subtitle,
  status,
  actions,
  facts,
  tiles = [],
  className,
  "data-testid": testId,
}: DetailHeroProps) {
  const shownTiles = tiles.slice(0, 4);

  return (
    <Card
      data-slot="detail-hero"
      data-testid={testId}
      className={cn("@container gap-4 p-5", className)}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          {hasContent(identifier) ? (
            <code
              data-slot="detail-hero-identifier"
              className="bg-muted text-muted-foreground inline-block rounded px-1.5 py-0.5 font-mono text-xs"
            >
              {identifier}
            </code>
          ) : null}
          <h1
            data-testid={titleTestId}
            className="font-display text-foreground text-2xl font-semibold tracking-tight"
          >
            {title}
          </h1>
          {hasContent(status) ? (
            <div data-slot="detail-hero-status" className="flex flex-wrap items-center gap-2">
              {status}
            </div>
          ) : null}
          {hasContent(subtitle) ? (
            <div data-slot="detail-hero-subtitle" className="text-muted-foreground text-sm">
              {subtitle}
            </div>
          ) : null}
          {facts}
        </div>
        {hasContent(actions) ? (
          <div data-slot="detail-hero-actions" className="flex flex-wrap items-center gap-2">
            {actions}
          </div>
        ) : null}
      </div>

      {shownTiles.length > 0 ? (
        <div
          data-slot="detail-hero-tiles"
          className={cn("grid gap-3", TILE_COLUMNS[shownTiles.length])}
        >
          {shownTiles.map((tile) => {
            const card = (
              <StatCard
                variant="compact"
                label={tile.label}
                value={tile.value}
                hint={tile.hint}
                state={tile.state}
                className="h-full"
              />
            );
            return tile.href ? (
              <Link
                key={tile.id}
                href={tile.href}
                className="focus-visible:ring-ring block rounded-lg outline-none focus-visible:ring-2"
              >
                {card}
              </Link>
            ) : (
              <div key={tile.id}>{card}</div>
            );
          })}
        </div>
      ) : null}
    </Card>
  );
}
