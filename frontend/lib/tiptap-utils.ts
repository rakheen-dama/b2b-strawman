/**
 * Extract plain text from a Tiptap JSON document body.
 * Returns null if the body has no extractable text content.
 */
export function extractTextFromBody(
  body: Record<string, unknown>,
): string | null {
  const content = body?.content as Array<Record<string, unknown>> | undefined;
  if (!content || !Array.isArray(content)) return null;
  const text = content
    .map((node) => {
      const children = node.content as
        | Array<Record<string, unknown>>
        | undefined;
      if (!children) return "";
      return children.map((child) => (child.text as string) ?? "").join("");
    })
    .join("\n")
    .trim();
  return text || null;
}
