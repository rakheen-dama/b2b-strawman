"use client";

import { useState } from "react";
import { FileText, Loader2, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { generatePaiaManual } from "@/app/(app)/org/[slug]/settings/data-protection/actions";

interface PaiaManualSectionProps {
  slug: string;
  jurisdiction: string | null;
}

export function PaiaManualSection({
  slug,
  jurisdiction,
}: PaiaManualSectionProps) {
  const [isGenerating, setIsGenerating] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleGenerate() {
    setIsGenerating(true);
    setError(null);
    setSuccessMessage(null);

    const result = await generatePaiaManual(slug);
    if (result.success && result.data) {
      setSuccessMessage(
        `PAIA manual generated: ${result.data.fileName} (${formatFileSize(result.data.fileSize)})`,
      );
    } else {
      setError(result.error ?? "Failed to generate PAIA manual.");
    }
    setIsGenerating(false);
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        PAIA Manual
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Generate and manage your PAIA (Promotion of Access to Information Act)
        manual for South African compliance.
      </p>

      {!jurisdiction && (
        <div className="mt-4 flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
          <AlertTriangle className="size-4 shrink-0" />
          <span>
            Jurisdiction required. Please set your data protection jurisdiction
            above before generating a PAIA manual.
          </span>
        </div>
      )}

      <div className="mt-4 flex items-center gap-3">
        <Button
          size="sm"
          disabled={isGenerating || !jurisdiction}
          onClick={handleGenerate}
        >
          {isGenerating ? (
            <Loader2 className="mr-1.5 size-4 animate-spin" />
          ) : (
            <FileText className="mr-1.5 size-4" />
          )}
          Generate PAIA Manual
        </Button>
      </div>

      {successMessage && (
        <p className="mt-3 text-sm text-teal-600 dark:text-teal-400">
          {successMessage}
        </p>
      )}
      {error && (
        <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
