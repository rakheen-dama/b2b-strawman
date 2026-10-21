/**
 * The signed-in user's org member id, resolved by matching the session email against the member
 * list. Case-insensitive: the identity provider and the member record may differ in casing.
 * `null` when the email is unknown or no member matches — callers must not treat that as "everyone".
 */
export function findCurrentMemberId(
  email: string | null | undefined,
  members: ReadonlyArray<{ id: string; email?: string | null }>
): string | null {
  if (!email) return null;
  const wanted = email.toLowerCase();
  return members.find((m) => m.email?.toLowerCase() === wanted)?.id ?? null;
}
