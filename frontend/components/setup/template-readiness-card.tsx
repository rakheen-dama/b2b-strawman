"use client";

import Link from "next/link";
import { CheckCircle2, AlertCircle } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import type { TemplateReadinessCardProps } from "./types";

export function TemplateReadinessCard({
  templates,
  generateHref,
}: TemplateReadinessCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Document Templates</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-3">
          {templates.map((tpl) => (
            <li
              key={tpl.templateId}
              className="flex items-center justify-between"
            >
              <div className="flex items-center gap-2">
                {tpl.ready ? (
                  <CheckCircle2 className="h-4 w-4 text-green-600 dark:text-green-400" />
                ) : (
                  <AlertCircle className="h-4 w-4 text-amber-500 dark:text-amber-400" />
                )}
                <span className="text-sm">{tpl.templateName}</span>
              </div>

              {tpl.ready ? (
                <Button asChild size="sm" variant="outline">
                  <Link href={generateHref(tpl.templateId)}>Generate</Link>
                </Button>
              ) : (
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled
                          className="cursor-not-allowed opacity-50"
                        >
                          Generate
                        </Button>
                      </span>
                    </TooltipTrigger>
                    <TooltipContent>
                      Fill these fields first:{" "}
                      {tpl.missingFields.join(", ")}
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              )}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
