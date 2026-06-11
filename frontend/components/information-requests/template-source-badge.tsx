import { Badge } from "@b2mash/ui/badge";

interface TemplateSourceBadgeProps {
  source: "PLATFORM" | "CUSTOM";
}

export function TemplateSourceBadge({ source }: TemplateSourceBadgeProps) {
  return (
    <Badge variant={source === "PLATFORM" ? "neutral" : "success"}>
      {source === "PLATFORM" ? "Platform" : "Custom"}
    </Badge>
  );
}
