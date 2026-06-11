// `cn` lives in `@b2mash/ui` (Wave 2.4) — the single source of truth for the
// clsx + tailwind-merge class combiner. Re-export it here so the ~hundreds of
// existing `@/lib/utils` import sites keep working without mass rewrites.
export { cn } from "@b2mash/ui/cn";
