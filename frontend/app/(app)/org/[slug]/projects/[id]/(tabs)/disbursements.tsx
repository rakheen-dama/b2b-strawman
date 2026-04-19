// Route-group file used to organize tab panel source code alongside the
// project detail page. The actual tab component lives at
// `frontend/components/legal/project-disbursements-tab.tsx` and is imported
// directly by `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`.
// This re-export exists to match the `(tabs)/disbursements` convention
// called out in task 488.5.

export { ProjectDisbursementsTab as default } from "@/components/legal/project-disbursements-tab";
export { ProjectDisbursementsTab } from "@/components/legal/project-disbursements-tab";
