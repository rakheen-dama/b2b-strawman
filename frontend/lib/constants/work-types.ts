/**
 * Suggested work types offered on every Work Type input (project/matter create
 * and edit, new-from-template, template editor).
 *
 * Work type stays free text, but the backend stores one canonical form
 * (trimmed, whitespace runs -> "_", upper-cased; `WorkTypes.normalize`) and
 * work-type-scoped field groups match on it. These values are the legal-za
 * project-template pack `matterType` values
 * (`backend/src/main/resources/project-template-packs/legal-za.json`), which
 * also cover the field packs' `applicableWorkTypes` (CONVEYANCING).
 */
export const SUGGESTED_WORK_TYPES = [
  "LITIGATION",
  "CONVEYANCING",
  "ESTATES",
  "COLLECTIONS",
  "COMMERCIAL",
] as const;

export const WORK_TYPE_PLACEHOLDER = "e.g. LITIGATION, CONVEYANCING";
