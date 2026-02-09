import Link from "next/link";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";

interface UpgradePromptProps {
  slug: string;
}

export function UpgradePrompt({ slug }: UpgradePromptProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="h-5 w-5" />
          Upgrade to Pro
        </CardTitle>
        <CardDescription>
          Unlock dedicated infrastructure, higher member limits, and priority
          support for your team.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link href={`/org/${slug}/settings/billing`}>View plans</Link>
        </Button>
      </CardContent>
    </Card>
  );
}
