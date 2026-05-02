/**
 * Class-3 / OBS-2103 prevention.
 *
 * Two adjacent `<*Trigger asChild>{children}</*Trigger>` siblings inside the
 * same parent JSX block collide under React 19 + Radix Slot reconciliation.
 * Both call `cloneElement` on the inner child at the same unkeyed sibling
 * position, so only one trigger wires up its `onClick`. Symptom: visible
 * button, unresponsive click.
 *
 * The fix is the dialog-owns-button pattern documented in
 * `frontend/CLAUDE.md` → "Dialog Trigger Composition".
 *
 * This rule reports any JSXElement / JSXFragment whose immediate children
 * include two or more `<*Trigger asChild>` JSX elements (whether direct
 * children, or wrapped in a `{cond && <X />}` / `{cond ? <X /> : <Y />}`
 * expression container that statically resolves to such a trigger).
 *
 * `TooltipTrigger` is excluded by default — tooltips are hover-only and
 * outside the click-loss class. Override via the `excludeTriggers` option.
 */

const DEFAULT_EXCLUDE = ["TooltipTrigger"];

function isAsChildAttribute(attr) {
  if (!attr || attr.type !== "JSXAttribute") return false;
  if (attr.name?.type !== "JSXIdentifier") return false;
  if (attr.name.name !== "asChild") return false;
  // Shorthand `<X asChild>` (no value) — true.
  if (attr.value === null || attr.value === undefined) return true;
  // Explicit `<X asChild={true}>` — true.
  if (
    attr.value.type === "JSXExpressionContainer" &&
    attr.value.expression?.type === "Literal" &&
    attr.value.expression.value === true
  ) {
    return true;
  }
  return false;
}

function triggerNameOf(jsxElement, excludeSet) {
  const opening = jsxElement.openingElement;
  if (!opening) return null;
  const name = opening.name;
  if (!name || name.type !== "JSXIdentifier") return null;
  if (!name.name.endsWith("Trigger")) return null;
  if (excludeSet.has(name.name)) return null;
  if (!opening.attributes.some(isAsChildAttribute)) return null;
  return name.name;
}

function unwrapExpression(expr, excludeSet) {
  if (!expr) return null;
  if (expr.type === "JSXElement") {
    const name = triggerNameOf(expr, excludeSet);
    return name ? { node: expr, name } : null;
  }
  if (expr.type === "LogicalExpression") {
    return unwrapExpression(expr.right, excludeSet);
  }
  if (expr.type === "ConditionalExpression") {
    return (
      unwrapExpression(expr.consequent, excludeSet) || unwrapExpression(expr.alternate, excludeSet)
    );
  }
  return null;
}

function unwrapChild(child, excludeSet) {
  if (!child) return null;
  if (child.type === "JSXElement") {
    const name = triggerNameOf(child, excludeSet);
    return name ? { node: child, name } : null;
  }
  if (child.type === "JSXExpressionContainer") {
    return unwrapExpression(child.expression, excludeSet);
  }
  return null;
}

function checkSiblings(parentNode, context, excludeSet) {
  const matches = [];
  for (const child of parentNode.children || []) {
    const m = unwrapChild(child, excludeSet);
    if (m) matches.push(m);
  }
  if (matches.length < 2) return;
  for (const m of matches) {
    context.report({
      node: m.node,
      messageId: "adjacency",
      data: { trigger: m.name },
    });
  }
}

const rule = {
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow two or more `<*Trigger asChild>` siblings in the same parent JSX block (Class-3 / OBS-2103 collision).",
      recommended: false,
    },
    schema: [
      {
        type: "object",
        additionalProperties: false,
        properties: {
          excludeTriggers: {
            type: "array",
            items: { type: "string" },
          },
        },
      },
    ],
    messages: {
      adjacency:
        "`<{{trigger}} asChild>` is one of two or more `*Trigger asChild` siblings in the same parent JSX. Two adjacent Radix `Slot`-wrapping triggers collide under React 19 reconciliation — only one wires up its onClick (Class-3 / OBS-2103). Use the dialog-owns-button pattern from frontend/CLAUDE.md → Dialog Trigger Composition.",
    },
  },
  create(context) {
    const options = context.options[0] || {};
    const excludeSet = new Set(options.excludeTriggers ?? DEFAULT_EXCLUDE);
    return {
      JSXElement(node) {
        checkSiblings(node, context, excludeSet);
      },
      JSXFragment(node) {
        checkSiblings(node, context, excludeSet);
      },
    };
  },
};

export default rule;
