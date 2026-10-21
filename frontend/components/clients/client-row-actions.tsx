"use client";

import Link from "next/link";
import { ExternalLink, MoreHorizontal } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface ClientRowActionsProps {
  slug: string;
  clientId: string;
}

/**
 * Trailing row menu (615C F-1 ruling (a), mirrored): navigation only. The list row lacks the data
 * the detail page's lifecycle / mutation menu needs.
 */
export function ClientRowActions({ slug, clientId }: ClientRowActionsProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          data-testid="client-row-actions-trigger"
        >
          <MoreHorizontal className="size-4" />
          {/* Fixed sr-only label, no aria-label: a name-bearing label would match substring
              getByLabel / getByRole locators elsewhere on the page. */}
          <span className="sr-only">Client actions</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem asChild>
          <Link href={`/org/${slug}/clients/${clientId}`}>
            <ExternalLink className="mr-2 size-4" />
            Open client
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
