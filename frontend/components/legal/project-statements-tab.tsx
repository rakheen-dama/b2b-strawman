"use client";

import { useState } from "react";
import useSWR from "swr";
import { Download, FileText } from "lucide-react";
import { ModuleGate } from "@/components/module-gate";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { StatementOfAccountDialog } from "@/components/legal/statement-of-account-dialog";
import { listStatementsAction } from "@/app/(app)/org/[slug]/projects/[id]/statement-actions";
import { downloadGeneratedDocumentAction } from "@/app/(app)/org/[slug]/settings/templates/template-generation-actions";
import { useCapabilities } from "@/lib/capabilities";
import { formatDate, formatCurrency } from "@/lib/format";
import { toast } from "sonner";
import type { StatementResponse } from "@/lib/api/statement-of-account";

interface ProjectStatementsTabProps {
  projectId: string;
  slug: string;
  projectName?: string;
}

export function ProjectStatementsTab(props: ProjectStatementsTabProps) {
  return (
    <ModuleGate module="disbursements">
      <ProjectStatementsTabInner {...props} />
    </ModuleGate>
  );
}

function ProjectStatementsTabInner({
  projectId,
  slug,
  projectName,
}: ProjectStatementsTabProps) {
  const { hasCapability } = useCapabilities();
  const canGenerate = hasCapability("GENERATE_STATEMENT_OF_ACCOUNT");

  const [generateOpen, setGenerateOpen] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const { data, error, isLoading, mutate } = useSWR(
    `project-statements-${projectId}`,
    async () => {
      const result = await listStatementsAction(projectId);
      if (!result.success) {
        throw new Error(result.error);
      }
      return result.data;
    },
    { dedupingInterval: 2000 }
  );

  const statements = data?.content ?? [];
  const isEmpty = !isLoading && !error && statements.length === 0;

  async function handleDownload(statement: StatementResponse) {
    setDownloadingId(statement.id);
    try {
      const result = await downloadGeneratedDocumentAction(statement.id);
      if (result.success && result.pdfBase64) {
        const byteCharacters = atob(result.pdfBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const blob = new Blob([new Uint8Array(byteNumbers)], {
          type: "application/pdf",
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = result.fileName ?? `statement-${statement.id}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      } else {
        toast.error(result.error ?? "Failed to download statement.");
      }
    } catch {
      toast.error("Failed to download statement.");
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <div data-testid="project-statements-tab" className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">
          Statements of Account
        </h3>
        {canGenerate && (
          <Button
            size="sm"
            variant="accent"
            onClick={() => setGenerateOpen(true)}
            data-testid="open-generate-statement-btn"
          >
            <FileText className="mr-1.5 size-4" />
            Generate Statement of Account
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-xs text-slate-500 italic">Loading statements&hellip;</p>
      ) : error ? (
        <div
          role="alert"
          data-testid="statements-error"
          className="rounded-md border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300"
        >
          Failed to load statements.{" "}
          {error instanceof Error && error.message ? error.message : ""}
        </div>
      ) : isEmpty ? (
        <div
          data-testid="statements-empty"
          className="rounded-md border border-dashed border-slate-300 bg-slate-50 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900"
        >
          No statements have been generated for this matter yet.
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Generated</TableHead>
              <TableHead className="text-right">Closing balance owing</TableHead>
              <TableHead className="text-right">Trust balance held</TableHead>
              <TableHead className="w-[1%] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {statements.map((s) => (
              <TableRow
                key={s.id}
                data-testid={`statement-row-${s.id}`}
                aria-busy={downloadingId === s.id}
              >
                <TableCell>{formatDate(s.generatedAt)}</TableCell>
                <TableCell className="text-right">
                  {/* TODO(multi-currency): read from OrgSettings when non-ZA verticals land */}
                  {formatCurrency(s.summary.closingBalanceOwing, "ZAR")}
                </TableCell>
                <TableCell className="text-right">
                  {/* TODO(multi-currency): read from OrgSettings when non-ZA verticals land */}
                  {formatCurrency(s.summary.trustBalanceHeld, "ZAR")}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => handleDownload(s)}
                    disabled={downloadingId === s.id}
                    aria-busy={downloadingId === s.id}
                    data-testid={`statement-download-${s.id}`}
                  >
                    <Download className="mr-1.5 size-4" />
                    {downloadingId === s.id ? "Downloading…" : "Download"}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <StatementOfAccountDialog
        slug={slug}
        projectId={projectId}
        projectName={projectName ?? "this matter"}
        open={generateOpen}
        onOpenChange={(next) => {
          setGenerateOpen(next);
          if (!next) mutate();
        }}
        onGenerated={() => mutate()}
      />
    </div>
  );
}
