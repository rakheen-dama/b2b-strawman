"use client";

import Link from "next/link";
import { ExternalLink, MoreHorizontal, UserRound } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface MatterRowActionsProps {
  slug: string;
  matterId: string;
  clientId: string | null;
}

/**
 * Trailing row menu (615C F-1 ruling (a)): navigation only. The cards it replaces had no overflow
 * menu, and a list row lacks the data the detail page's mutation menu needs.
 */
export function MatterRowActions({ slug, matterId, clientId }: MatterRowActionsProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          data-testid="matter-row-actions-trigger"
        >
          <MoreHorizontal className="size-4" />
          {/* Fixed sr-only label, no aria-label: a name-bearing label would match substring
              getByLabel / getByRole locators elsewhere on the page (e.g. "Name"). */}
          <span className="sr-only">Matter actions</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem asChild>
          <Link href={`/org/${slug}/matters/${matterId}`}>
            <ExternalLink className="mr-2 size-4" />
            Open matter
          </Link>
        </DropdownMenuItem>
        {clientId && (
          <DropdownMenuItem asChild>
            <Link href={`/org/${slug}/clients/${clientId}`}>
              <UserRound className="mr-2 size-4" />
              Open client
            </Link>
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
