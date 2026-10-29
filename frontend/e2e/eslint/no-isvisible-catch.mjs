/**
 * Phase 88 628E (U22, R10 §0 M1) — forbid swallowing Playwright locator state checks.
 *
 * `locator.isVisible()` (and isHidden / isEnabled / isDisabled / isChecked / isEditable) do NOT wait:
 * they answer for the DOM as it is at call time, and Playwright ignores the `timeout` option of
 * isVisible/isHidden. They reject only on a strict-mode violation (the locator matched 2+ elements)
 * or a closed page. So `.catch(() => false)` exists only to turn "the text is on screen twice" into
 * `false` — the soft assertion then fails, or the guarded step is silently skipped and the test
 * passes having done nothing. Write `await expect(locator.first()).toBeVisible()` (web-first, waits,
 * strict-safe) or a role locator; for genuinely optional UI, wait for either state first:
 *   await expect(a.or(b).first()).toBeVisible();
 *   if (await a.isVisible()) { … }
 * See frontend/e2e/AUTOMATION-NOTES.md › Assertion rules.
 *
 * Reports:
 *   (i)   `.catch(…)` chained directly on one of `methods`;
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

function unwrapChain(node) {
  return node && node.type === "ChainExpression" ? node.expression : node;
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
        "`.{{method}}()` rejection swallowed with `.{{handler}}()`. It never waits and only rejects on a strict-mode violation (2+ matches), so this turns visible-but-ambiguous into `false`. Use `await expect(locator.first()).toBeVisible()` or a role locator; for optional UI wait on `a.or(b)` first (e2e/AUTOMATION-NOTES.md › Assertion rules).",
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
            const method = stateMethodOf(callee.object, methods);
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
