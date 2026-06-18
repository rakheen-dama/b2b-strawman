import Link from "next/link";
import { Bot, ExternalLink } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import type { McpStatus } from "@/lib/types";

interface McpIntegrationCardProps {
  slug: string;
  status: McpStatus | null;
}

export function McpIntegrationCard({ slug, status }: McpIntegrationCardProps) {
  const enabled = status?.effectivelyEnabled ?? false;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <Bot className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">Claude / MCP Connector</CardTitle>
          </div>
          {enabled ? (
            <Badge variant="success">Enabled</Badge>
          ) : (
            <Badge variant="neutral">Disabled</Badge>
          )}
        </div>
        <CardDescription>
          Let your firm&apos;s own Claude read your Kazi data over the Model Context Protocol
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" size="sm" asChild>
          <Link href={`/org/${slug}/settings/integrations/mcp`}>
            <ExternalLink className="mr-2 size-4" />
            Configure Connector
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
