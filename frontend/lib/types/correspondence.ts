// ---- Correspondence types ----

/**
 * Frontend-only originating-correspondence reference for a {@code
 * CREATE_TASK_FROM_CORRESPONDENCE} gate. Resolved by the reviews server page from the gate
 * detail's {@code proposedAction} ({@code correspondence_id} / {@code project_id}) — there is no
 * backend subject field, so the card renders a link to the originating matter's Correspondence tab
 * rather than the subject text.
 */
export interface CorrespondenceOrigin {
  projectId: string;
  correspondenceId?: string;
}
