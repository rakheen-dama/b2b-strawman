/**
 * Centralized brand constant for the customer portal.
 * Product name is "Kazi"; company is "b2mash".
 * The retired pre-rebrand name must never appear in source — see project
 * CLAUDE.md and `~/.claude/projects/.../feedback_product_name_kazi.md`.
 * The OBS-404 guardrail in `lib/__tests__/brand.test.ts` enforces this.
 */
export const BRAND_NAME = "Kazi" as const;
