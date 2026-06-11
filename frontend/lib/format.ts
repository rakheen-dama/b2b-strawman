// Staff-app re-export of the unified formatting helpers.
//
// The implementation now lives in `@b2mash/shared/format` (shared verbatim with
// the client portal — see packages/shared/src/format.ts). This thin shim exists
// for one staff-app-specific reason: it loads `@/lib/intl-polyfill` as a
// side-effect.
//
// Why the shim and not a direct import everywhere:
//   - Next.js runs on Node, which ships small-icu by default. Without the
//     @formatjs NumberFormat polyfill, `Intl.NumberFormat("en-ZA", …)` silently
//     falls back to en-US during SSR, causing ZAR hydration mismatches
//     (regression GAP-L-12). The portal runs on full-ICU runtimes and must NOT
//     load this polyfill (doing so would change its byte-for-byte output), so
//     the polyfill cannot live in the shared package.
//   - Importing the polyfill here — the module every staff-app caller already
//     imports — preserves the exact process-wide load behaviour that existed
//     when the implementation lived in this file, with no call-site churn.
import "@/lib/intl-polyfill";

export * from "@b2mash/shared/format";
