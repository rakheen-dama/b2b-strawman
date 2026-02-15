import Link from "next/link";
import { Button } from "@/components/ui/button";

export function CtaSection() {
  return (
    <section className="bg-white px-6 py-24 dark:bg-slate-950">
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="font-display text-3xl text-slate-950 sm:text-4xl dark:text-slate-50">
          Ready to get started?
        </h2>
        <p className="mt-4 text-lg text-slate-700 dark:text-slate-300">
          Create your workspace in seconds. No credit card required.
        </p>
        <div className="mt-8">
          <Button size="lg" asChild>
            <Link href="/sign-up">Create your workspace</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}
