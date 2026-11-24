"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Download } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { downloadTakeOnTemplate } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import {
  TAKE_ON_DATASET_LABEL,
  TAKE_ON_DATASET_ORDER,
  TAKE_ON_SECTION_LABEL,
  takeOnKebab,
  type TakeOnDataset,
  type TakeOnDatasetCatalogue,
  type TakeOnSection,
  type TakeOnUploadResult,
} from "@/lib/types";
import { saveTakeOnFile, TakeOnError, TakeOnWarnings } from "./take-on-feedback";
import { UploadStep } from "./upload-step";

interface DatasetPickerProps {
  slug: string;
  datasets: TakeOnDatasetCatalogue[];
}

/**
 * The four take-on datasets in the order a firm takes them on (Phase 93 653A, X3): clients →
 * matters → trust → debtors. "Start" opens the first upload inline (C-17); on success the wizard
 * opens at `?tab=take-on&batch={id}`.
 */
export function DatasetPicker({ slug, datasets }: DatasetPickerProps) {
  const router = useRouter();
  const [started, setStarted] = useState<TakeOnDataset | null>(null);
  const [pending, setPending] = useState<TakeOnUploadResult | null>(null);
  const [templateError, setTemplateError] = useState<string | null>(null);

  const ordered = TAKE_ON_DATASET_ORDER.map((d) => datasets.find((c) => c.dataset === d)).filter(
    (c): c is TakeOnDatasetCatalogue => c !== undefined
  );
  const startedCatalogue = ordered.find((c) => c.dataset === started) ?? null;
  const batchHref = (id: string) => `/org/${slug}/settings/matters?tab=take-on&batch=${id}`;

  async function template(dataset: TakeOnDataset, section?: TakeOnSection) {
    setTemplateError(null);
    const result = await downloadTakeOnTemplate(dataset, section);
    if (!result.success || !result.data) {
      setTemplateError(result.error ?? "The template could not be downloaded. Try again.");
      return;
    }
    saveTakeOnFile(result.data.fileName, result.data.content);
  }

  function uploaded(result: TakeOnUploadResult) {
    if (result.warnings.length > 0) {
      setPending(result); // show the reader's warnings before moving on
      return;
    }
    router.push(batchHref(result.batch.id));
  }

  return (
    <div className="space-y-6" data-testid="take-on-dataset-picker">
      <p className="text-sm text-slate-700 dark:text-slate-300" data-testid="take-on-guidance">
        Take on clients first, then matters, then the trust position, then debtors.
      </p>
      {/* Two columns at most: from xl the go-live checklist sits beside the picker (656A). */}
      <div className="grid gap-4 md:grid-cols-2">
        {ordered.map((catalogue) => {
          const kebab = takeOnKebab(catalogue.dataset);
          const multiSection = catalogue.sections.length > 1;
          return (
            <Card key={catalogue.dataset} data-testid={`take-on-dataset-${kebab}`}>
              <CardHeader>
                <CardTitle>{TAKE_ON_DATASET_LABEL[catalogue.dataset]}</CardTitle>
                <CardDescription data-testid={`take-on-completed-${kebab}`}>
                  <span className="font-mono tabular-nums">{catalogue.completedBatches}</span>{" "}
                  completed
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <Button
                  type="button"
                  variant="accent"
                  size="sm"
                  onClick={() => {
                    setPending(null);
                    setStarted(catalogue.dataset);
                  }}
                  data-testid={`take-on-start-${kebab}`}
                >
                  Start
                </Button>
                <div className="flex flex-col items-start gap-1">
                  {multiSection ? (
                    catalogue.sections.map((s) => (
                      <Button
                        key={s.section}
                        type="button"
                        variant="link"
                        size="sm"
                        className="h-auto px-0 text-left whitespace-normal"
                        onClick={() => template(catalogue.dataset, s.section)}
                        data-testid={`take-on-template-${kebab}-${takeOnKebab(s.section)}`}
                      >
                        <Download className="size-3.5" />
                        Template: {TAKE_ON_SECTION_LABEL[s.section]}
                      </Button>
                    ))
                  ) : (
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      className="h-auto px-0"
                      onClick={() => template(catalogue.dataset)}
                      data-testid={`take-on-template-${kebab}`}
                    >
                      <Download className="size-3.5" />
                      Download template
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
      {templateError && <TakeOnError message={templateError} testId="take-on-template-error" />}

      {startedCatalogue && (
        <Card data-testid="take-on-new-upload">
          <CardHeader>
            <CardTitle>{TAKE_ON_DATASET_LABEL[startedCatalogue.dataset]} — upload a file</CardTitle>
            <CardDescription>
              Upload the file from your previous system. Nothing is created until you commit.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {pending ? (
              <div className="space-y-3">
                <TakeOnWarnings warnings={pending.warnings} />
                <Button
                  type="button"
                  variant="accent"
                  onClick={() => router.push(batchHref(pending.batch.id))}
                  data-testid="take-on-continue"
                >
                  Continue
                </Button>
              </div>
            ) : (
              <UploadStep
                key={startedCatalogue.dataset}
                dataset={startedCatalogue}
                onUploaded={uploaded}
                onClose={() => setStarted(null)}
              />
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
