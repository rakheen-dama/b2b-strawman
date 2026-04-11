import { Info } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface Section866AdvisoryProps {
  className?: string;
  "data-testid"?: string;
}

export function Section866Advisory({
  className,
  "data-testid": testId = "section-86-6-advisory",
}: Section866AdvisoryProps) {
  return (
    <Alert className={className} data-testid={testId}>
      <Info className="size-4" />
      <AlertDescription>
        The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section
        86(6)). Contact the LPFF to verify.
      </AlertDescription>
    </Alert>
  );
}
