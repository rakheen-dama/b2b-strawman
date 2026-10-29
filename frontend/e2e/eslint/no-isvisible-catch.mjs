/**
 * Phase 88 628E (U22, R10 §0 M1) — forbid swallowing Playwright locator state checks.
 *
 * Playwright's locator state checks (playwright-core 1.59 `types.d.ts` + `server/frames.js`):
 *   - `isVisible()` / `isHidden()` do NOT wait: they answer for the DOM at call time (no match →
 *     false / true) and ignore their deprecated `timeout` option. They reject only on a strict-mode
 *     violation (the locator matched 2+ elements) or a closed page.
 *   - `isEnabled()` / `isDisabled()` / `isChecked()` / `isEditable()` DO wait for the locator to
 *     match, up to `timeout` (default: the `actionTimeout`, else none), but do not retry the state
 *     itself. They reject on that timeout (the element never appeared), a strict-mode violation, or
 *     the wrong kind of element (isChecked on a non-checkbox, isEditable on a non-input).
 * Either way a `.catch(() => false)` turns "missing / ambiguous / wrong element" into a plain
 * boolean — the soft assertion then fails for the wrong reason, or the guarded step is silently
 * skipped and the test passes having done nothing. Write `await expect(locator.first()).toBeVisible()`
 * (web-first: retries the state, strict-safe) or a role locator; for genuinely optional UI, wait
 * for either state first:
 *   await expect(a.or(b).first()).toBeVisible();
 *   if (await a.isVisible()) { … }
 * See frontend/e2e/AUTOMATION-NOTES.md › Assertion rules.
 *
 * Reports:
 *   (i)   `.catch(…)` on one of `methods` — directly or further down a chain of one-argument
 *         `.then(…)` / `.finally(…)` links (`x.isVisible().then(f).catch(r)`), which pass the
 *         rejection through;
 *   (ii)  the same via a two-argument `.then(onFulfilled, onRejected)`;
 *   (iii) `isVisible({ timeout })` / `isHidden({ timeout })` (option `checkTimeout`, default on).
 *
 * Options: `{ methods?: string[], checkTimeout?: boolean }` — `methods` replaces the default set.
 *
 * Registered in eslint-rules/index.mjs (plugin `kazi-frontend-local`); scoped to e2e files in
 * eslint.config.mjs.
 */

const DEFAULT_METHODS = [
  "isVisible",
  "isHidden",
  "isEnabled",
  "isDisabled",
  "isChecked",
  "isEditable",
];
const TIMEOUT_IGNORED = new Set(["isVisible", "isHidden"]);

/** Strips an optional-chaining wrapper (`a?.b()` parses as ChainExpression → CallExpression). */
function unwrapChain(node) {
  return node && node.type === "ChainExpression" ? node.expression : node;
}

/**
 * Walks back from a promise expression through the links that pass a rejection on unchanged —
 * one-argument `.then(onFulfilled)` and `.finally(…)` — and returns the call they started from.
 * A two-argument `.then` stops the walk (it handles the rejection itself and is reported on its own).
 */
function rejectionSource(node) {
  let current = unwrapChain(node);
  while (current?.type === "CallExpression") {
    const callee = unwrapChain(current.callee);
    if (callee?.type !== "MemberExpression" || callee.computed) break;
    if (callee.property?.type !== "Identifier") break;
    const link = callee.property.name;
    const passesRejection = link === "finally" || (link === "then" && current.arguments.length < 2);
    if (!passesRejection) break;
    current = unwrapChain(callee.object);
  }
  return current;
}

/** Returns the method name if `node` is a call `<expr>.<method>(…)` with method in `methods`. */
function stateMethodOf(node, methods) {
  const call = unwrapChain(node);
  if (!call || call.type !== "CallExpression") return null;
  const callee = unwrapChain(call.callee);
  if (!callee || callee.type !== "MemberExpression" || callee.computed) return null;
  if (callee.property?.type !== "Identifier") return null;
  return methods.has(callee.property.name) ? callee.property.name : null;
}

/** True for an object-literal key spelled `timeout` or `"timeout"`. */
function isTimeoutKey(key) {
  return (
    (key.type === "Identifier" && key.name === "timeout") ||
    (key.type === "Literal" && key.value === "timeout")
  );
}

/** True if any object-literal argument of `call` sets `timeout` (`page.isVisible(sel, { timeout })` too). */
function hasTimeoutOption(call) {
  return call.arguments.some(
    (arg) =>
      arg.type === "ObjectExpression" &&
      arg.properties.some((p) => p.type === "Property" && !p.computed && isTimeoutKey(p.key))
  );
}

const rule = {
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow `.catch()` / two-argument `.then()` on Playwright locator state checks (isVisible etc.), and the ignored `timeout` option on isVisible/isHidden.",
      recommended: false,
    },
    schema: [
      {
        type: "object",
        additionalProperties: false,
        properties: {
          methods: { type: "array", items: { type: "string" } },
          checkTimeout: { type: "boolean" },
        },
      },
    ],
    messages: {
      swallowed:
        "`.{{method}}()` rejection swallowed with `.{{handler}}()`. It rejects only when the check cannot answer — a strict-mode violation (2+ matches), or (isEnabled/isDisabled/isChecked/isEditable) an element that never appeared or is the wrong kind — so this turns missing/ambiguous into a plain boolean. Use a web-first assertion (`await expect(locator.first()).toBeVisible()`, `toBeEnabled()`, `toBeChecked()` …) or a role locator; for optional UI wait on `a.or(b)` first (e2e/AUTOMATION-NOTES.md › Assertion rules).",
      timeoutIgnored:
        "`.{{method}}({ timeout })` — Playwright ignores `timeout` here; the check does not wait. Use `await expect(locator).toBeVisible({ timeout })` / `toBeHidden({ timeout })`.",
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const methods = new Set(options.methods ?? DEFAULT_METHODS);
    const timeoutMethods = new Set([...methods].filter((m) => TIMEOUT_IGNORED.has(m)));
    const checkTimeout = options.checkTimeout ?? true;
    return {
      CallExpression(node) {
        const callee = unwrapChain(node.callee);
        if (
          callee?.type === "MemberExpression" &&
          !callee.computed &&
          callee.property?.type === "Identifier"
        ) {
          const handler = callee.property.name;
          const swallows =
            handler === "catch" || (handler === "then" && node.arguments.length >= 2);
          if (swallows) {
            const method = stateMethodOf(rejectionSource(callee.object), methods);
            if (method) {
              context.report({ node, messageId: "swallowed", data: { method, handler } });
            }
          }
        }
        if (checkTimeout) {
          const method = stateMethodOf(node, timeoutMethods);
          if (method && hasTimeoutOption(node)) {
            context.report({ node, messageId: "timeoutIgnored", data: { method } });
          }
        }
      },
    };
  },
};

export default rule;
