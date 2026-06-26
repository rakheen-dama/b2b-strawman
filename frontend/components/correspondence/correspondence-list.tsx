import Link from "next/link";
import { Mail, Paperclip, ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";
import type { CorrespondenceListItem } from "@/lib/api/correspondence";

interface CorrespondenceListProps {
  items: CorrespondenceListItem[];
  slug: string;
  projectId: string;
}

/**
 * Read-only correspondence list for the matter-detail "Correspondence" tab.
 *
 * Filed correspondence (newest-first, server-ordered) with subject, sender, received date,
 * direction and attachment count. Attachments link to the matter's Documents tab (the docs are
 * stamped with the correspondence id server-side). This is a presentational Server Component — no
 * inbox, threading or compose. The list DTO deliberately carries no {@code bodyHtml}, so the
 * attacker-influenced email body can never reach the DOM here (stored-XSS guard).
 */
export function CorrespondenceList({ items, slug, projectId }: CorrespondenceListProps) {
  if (items.length === 0) {
    return (
      <EmptyState
        icon={Mail}
        title="No correspondence yet"
        description="Inbound correspondence filed against this matter will appear here. Email is filed via your firm's own Claude using the Kazi MCP tools — Kazi never reads your mailbox."
      />
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Subject</TableHead>
          <TableHead>From</TableHead>
          <TableHead>Direction</TableHead>
          <TableHead>Received</TableHead>
          <TableHead className="text-right">Attachments</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.id}>
            <TableCell className="font-medium text-slate-900 dark:text-slate-100">
              {item.subject?.trim() ? item.subject : "(no subject)"}
            </TableCell>
            <TableCell className="text-slate-600 dark:text-slate-400">{item.fromAddress}</TableCell>
            <TableCell>
              {item.direction === "INBOUND" ? (
                <Badge variant="secondary" className="gap-1">
                  <ArrowDownLeft className="size-3" /> Inbound
                </Badge>
              ) : (
                <Badge variant="outline" className="gap-1">
                  <ArrowUpRight className="size-3" /> Outbound
                </Badge>
              )}
            </TableCell>
            <TableCell className="text-slate-600 dark:text-slate-400">
              {item.receivedAt ? formatDateTime(item.receivedAt) : "—"}
            </TableCell>
            <TableCell className="text-right">
              {item.attachmentCount > 0 ? (
                <Link
                  href={`/org/${slug}/projects/${projectId}?tab=documents`}
                  className="inline-flex items-center gap-1 text-teal-600 hover:text-teal-700"
                >
                  <Paperclip className="size-3" />
                  {item.attachmentCount}
                </Link>
              ) : (
                <span className="text-slate-400">0</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
