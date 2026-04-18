// Ensures Intl.NumberFormat has full ICU data for locales we render.
// Node ships with small-icu by default: Intl.NumberFormat('en-ZA', ...) silently
// formats as en-US in Node, causing SSR/CSR hydration mismatches for ZAR. See
// qa_cycle/fix-specs/GAP-L-12.md.
//
// `polyfill-force` is the correct import for Next.js SSR because Node claims
// partial support via `supportedLocalesOf`; without `-force`, the polyfill
// defers to Node's (broken) implementation. On browsers with full ICU this is
// a no-op — the package detects full support and skips.
//
// Notes:
// - Explicit `.js` suffixes are required because the package's `exports`
//   map lists each subpath with the extension baked in.
// - The package only ships locale-data files for locales that differ from
//   their parent — `en-US` and `de-DE` are covered by `en.js` / `de.js`
//   (they're the defaults for those language roots). `en-ZA` and `en-GB`
//   have their own files because their separators/currency symbols differ.
import "@formatjs/intl-numberformat/polyfill-force.js";
import "@formatjs/intl-numberformat/locale-data/en.js";
import "@formatjs/intl-numberformat/locale-data/en-ZA.js";
import "@formatjs/intl-numberformat/locale-data/en-GB.js";
import "@formatjs/intl-numberformat/locale-data/de.js";
