import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import type { FicaVerificationOutput } from "@/lib/api/ai";

interface FicaResultDisplayProps {
  output: FicaVerificationOutput | null;
}

function getAssessmentBadgeVariant(assessment: FicaVerificationOutput["overallAssessment"]) {
  switch (assessment) {
    case "COMPLETE":
      return "success" as const;
    case "INCOMPLETE":
      return "warning" as const;
    case "NEEDS_REVIEW":
      return "destructive" as const;
  }
}

function getRiskBadgeVariant(risk: FicaVerificationOutput["riskLevel"]) {
  switch (risk) {
    case "LOW":
      return "success" as const;
    case "MEDIUM":
      return "warning" as const;
    case "HIGH":
      return "destructive" as const;
  }
}

function getItemStatusBadgeVariant(status: string) {
  switch (status) {
    case "SATISFIED":
      return "success" as const;
    case "UNSATISFIED":
      return "destructive" as const;
    case "PARTIAL":
      return "warning" as const;
    case "REQUIRES_REVIEW":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

export function FicaResultDisplay({ output }: FicaResultDisplayProps) {
  if (!output) {
    return (
      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="py-6">
          <p className="text-center text-sm text-slate-500 dark:text-slate-400">
            No verification output available.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Assessment & Risk Badges */}
      <div className="flex items-center gap-3">
        <div className="space-y-1">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Assessment
          </p>
          <Badge variant={getAssessmentBadgeVariant(output.overallAssessment)}>
            {output.overallAssessment.replace("_", " ")}
          </Badge>
        </div>
        <div className="space-y-1">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Risk Level
          </p>
          <Badge variant={getRiskBadgeVariant(output.riskLevel)}>{output.riskLevel}</Badge>
        </div>
      </div>

      {/* Checklist Review Table */}
      {output.checklistReview.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Checklist Review
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-700">
                    <th className="pb-2 pr-4 text-left text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Item
                    </th>
                    <th className="pb-2 pr-4 text-left text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Status
                    </th>
                    <th className="pb-2 pr-4 text-left text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Evidence Document
                    </th>
                    <th className="pb-2 text-left text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Flags
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {output.checklistReview.map((item) => (
                    <tr key={item.checklistItemId}>
                      <td className="py-2 pr-4 text-slate-700 dark:text-slate-300">
                        {item.itemName}
                      </td>
                      <td className="py-2 pr-4">
                        <Badge variant={getItemStatusBadgeVariant(item.status)}>
                          {item.status.replace("_", " ")}
                        </Badge>
                      </td>
                      <td className="py-2 pr-4 font-mono text-xs text-slate-500 dark:text-slate-400">
                        {item.evidenceDocument ?? "—"}
                      </td>
                      <td className="py-2">
                        {item.flags.length > 0 ? (
                          <div className="flex flex-wrap gap-1">
                            {item.flags.map((flag) => (
                              <Badge key={flag} variant="warning">
                                {flag}
                              </Badge>
                            ))}
                          </div>
                        ) : (
                          <span className="text-slate-400 dark:text-slate-500">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Missing Documents */}
      {output.missingDocuments.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Missing Documents
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-300">
              {output.missingDocuments.map((doc) => (
                <li key={doc}>{doc}</li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* Risk Flags */}
      {output.riskFlags.length > 0 && (
        <div className="space-y-2">
          {output.riskFlags.map((flag) => (
            <div
              key={flag}
              className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
            >
              {flag}
            </div>
          ))}
        </div>
      )}

      {/* Recommended Actions */}
      {output.recommendedActions.length > 0 && (
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold text-slate-950 dark:text-slate-50">
              Recommended Actions
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {output.recommendedActions.map((action, i) => (
                <li key={i} className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Badge variant="neutral">
                      {action.action === "MARK_ITEMS_COMPLETE"
                        ? "Mark Items Complete"
                        : "Request Additional Document"}
                    </Badge>
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {action.action === "MARK_ITEMS_COMPLETE"
                        ? "Requires attorney approval (execution gate)"
                        : "Informational — no approval needed"}
                    </span>
                  </div>
                  <p className="text-sm text-slate-700 dark:text-slate-300">{action.reasoning}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
