import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";

/**
 * `<datalist>` of suggested work types. Pair it with a free-text work type
 * `<Input list={id}>` — users can pick a suggestion or type their own value;
 * the backend normalises whatever is submitted.
 */
export function WorkTypeSuggestions({ id }: { id: string }) {
  return (
    <datalist id={id} data-testid={id}>
      {SUGGESTED_WORK_TYPES.map((workType) => (
        <option key={workType} value={workType} />
      ))}
    </datalist>
  );
}
