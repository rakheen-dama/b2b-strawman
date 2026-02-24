import Link from "next/link";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <CardTitle className="text-5xl font-bold text-slate-300">
            404
          </CardTitle>
          <CardDescription className="text-base">
            Page not found
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="accent" asChild>
            <Link href="/projects">Go to Projects</Link>
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
