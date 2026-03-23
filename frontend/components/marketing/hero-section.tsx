import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const signUpHref = AUTH_MODE === "mock" ? "/sign-up" : "/request-access";

export function HeroSection() {
  return (
    <section className="relative overflow-hidden bg-slate-950 px-6 pt-32 pb-20 sm:pt-40 sm:pb-28 lg:pb-32">
      {/* Subtle grid pattern */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage:
            "linear-gradient(to right, white 1px, transparent 1px), linear-gradient(to bottom, white 1px, transparent 1px)",
          backgroundSize: "64px 64px",
        }}
      />

      {/* Teal glow — top right */}
      <div className="pointer-events-none absolute -top-32 right-0 h-[500px] w-[500px] rounded-full bg-teal-500/8 blur-[120px]" />

      <div className="relative mx-auto max-w-7xl">
        <div className="grid items-center gap-12 lg:grid-cols-5 lg:gap-16">
          {/* Text content — 60% */}
          <div className="lg:col-span-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-teal-500/20 bg-teal-500/10 px-4 py-1.5 text-sm font-medium text-teal-400">
              Now accepting early access requests
              <ArrowRight className="size-3.5" />
            </div>

            <h1 className="mt-8 font-display text-4xl leading-[1.1] tracking-tight text-white text-balance sm:text-6xl lg:text-7xl">
              Practice management,{" "}
              <span className="bg-gradient-to-r from-teal-400 to-teal-300 bg-clip-text text-transparent">
                built for Africa
              </span>
            </h1>

            <p className="mt-6 max-w-xl text-lg leading-8 text-slate-400">
              Time tracking, invoicing, compliance, and profitability — in one
              platform that understands South African regulations and workflows.
              Built for accounting firms, consultancies, and professional services.
            </p>

            <div className="mt-10 flex flex-wrap gap-4">
              <Button size="lg" variant="accent" asChild>
                <a href="mailto:hello@kazi.africa">
                  Book a Demo
                  <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                size="lg"
                variant="soft"
                asChild
                className="bg-white/8 text-white hover:bg-white/12"
              >
                <Link href={signUpHref}>Get Started</Link>
              </Button>
            </div>
          </div>

          {/* Dashboard screenshot placeholder — 40% */}
          <div className="lg:col-span-2">
            <div className="relative">
              {/* Browser chrome frame */}
              <div className="overflow-hidden rounded-xl border border-white/10 bg-slate-900 shadow-2xl shadow-teal-500/5">
                <div className="flex items-center gap-2 border-b border-white/5 px-4 py-3">
                  <div className="size-2.5 rounded-full bg-white/20" />
                  <div className="size-2.5 rounded-full bg-white/20" />
                  <div className="size-2.5 rounded-full bg-white/20" />
                  <div className="ml-2 h-5 flex-1 rounded bg-white/5" />
                </div>
                <div className="aspect-[4/3] bg-slate-800/50 p-1">
                  <div className="flex size-full items-center justify-center rounded bg-slate-800 text-sm text-slate-600">
                    Dashboard screenshot
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
