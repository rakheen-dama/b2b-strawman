import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

function AnnouncementBadge() {
  return (
    <Link
      href="/sign-up"
      className="inline-flex items-center gap-2 rounded-full bg-indigo-50 px-4 py-1.5 text-sm font-medium text-indigo-700 transition-colors hover:bg-indigo-100 dark:bg-indigo-950 dark:text-indigo-300 dark:hover:bg-indigo-900"
    >
      New: Pro plan with dedicated infrastructure
      <ArrowRight className="size-3.5" />
    </Link>
  );
}

export function HeroSection() {
  return (
    <section className="bg-olive-50 px-6 py-20 sm:py-28 lg:py-32 dark:bg-olive-950">
      <div className="mx-auto grid max-w-7xl items-center gap-12 lg:grid-cols-5 lg:gap-16">
        {/* Text content — 60% */}
        <div className="lg:col-span-3">
          <AnnouncementBadge />

          <h1 className="mt-6 font-display text-5xl leading-tight text-olive-950 text-balance sm:text-[5rem] sm:leading-[1.1] dark:text-olive-50">
            Document collaboration for modern teams
          </h1>

          <p className="mt-6 max-w-xl text-lg leading-8 text-olive-700 dark:text-olive-300">
            Organize documents, manage projects, and collaborate across your
            organization — all in one workspace. Built for teams that value
            security and simplicity.
          </p>

          <div className="mt-8 flex flex-wrap gap-4">
            <Button size="lg" asChild>
              <Link href="/sign-up">Get Started</Link>
            </Button>
            <Button variant="soft" size="lg" asChild>
              <Link href="/sign-in">Sign In</Link>
            </Button>
          </div>
        </div>

        {/* Screenshot placeholder — 40% */}
        <div className="lg:col-span-2">
          <div className="aspect-video rounded-lg bg-olive-200 dark:bg-olive-800" />
        </div>
      </div>
    </section>
  );
}
