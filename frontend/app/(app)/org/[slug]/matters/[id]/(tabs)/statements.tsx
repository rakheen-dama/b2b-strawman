// Route-group file used to organize tab panel source code alongside the
// project detail page. The actual tab component lives at
// `frontend/components/legal/project-statements-tab.tsx` and is imported
// directly by `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`.
// This re-export exists to match the `(tabs)/statements` convention
// called out in task 491.9.

export { ProjectStatementsTab as default } from "@/components/legal/project-statements-tab";
export { ProjectStatementsTab } from "@/components/legal/project-statements-tab";
