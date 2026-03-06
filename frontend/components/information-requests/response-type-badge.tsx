import { Badge } from "@/components/ui/badge";

interface ResponseTypeBadgeProps {
  responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
}

export function ResponseTypeBadge({ responseType }: ResponseTypeBadgeProps) {
  const label = responseType === "FILE_UPLOAD" ? "File Upload" : "Text";
  const variant = responseType === "FILE_UPLOAD" ? "neutral" : "success";

  return <Badge variant={variant}>{label}</Badge>;
}
