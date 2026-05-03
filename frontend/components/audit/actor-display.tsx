"use client";

import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

/**
 * Prefix used by the backend (`AuditService.resolveActorDisplay`) when the
 * acting member has been deleted/anonymised. We mirror the literal here for
 * strikethrough styling — keep in sync with the backend resolver.
 */
export const FORMER_MEMBER_PREFIX = "Former member";

export interface ActorDisplayProps {
  actorDisplayName: string;
  actorId: string | null;
  actorType: string | null;
  source: string | null;
  ipAddress: string | null;
}

export function ActorDisplay({
  actorDisplayName,
  actorId,
  actorType,
  source,
  ipAddress,
}: ActorDisplayProps) {
  const isFormer = actorDisplayName.startsWith(FORMER_MEMBER_PREFIX);
  const rows: Array<[string, string]> = [];
  if (actorId) rows.push(["Actor ID", actorId]);
  if (actorType) rows.push(["Type", actorType]);
  if (source) rows.push(["Source", source]);
  if (ipAddress) rows.push(["IP", ipAddress]);

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            data-testid="actor-display"
            className={cn(
              "inline-block cursor-help text-xs",
              isFormer && "text-slate-500 line-through",
            )}
          >
            {actorDisplayName}
          </span>
        </TooltipTrigger>
        {rows.length > 0 && (
          <TooltipContent className="max-w-xs">
            <dl className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-0.5 text-[11px]">
              {rows.map(([label, value]) => (
                <div key={label} className="contents">
                  <dt className="font-semibold opacity-70">{label}:</dt>
                  <dd className="font-mono break-all">{value}</dd>
                </div>
              ))}
            </dl>
          </TooltipContent>
        )}
      </Tooltip>
    </TooltipProvider>
  );
}
