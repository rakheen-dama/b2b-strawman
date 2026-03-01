"use client";

interface ProposalPreviewProps {
  contentJson: Record<string, unknown> | null;
}

export function ProposalPreview({ contentJson }: ProposalPreviewProps) {
  if (!contentJson) {
    return (
      <p className="text-sm italic text-slate-400">No content added yet.</p>
    );
  }

  // 237A form stores body as { text: "plain text content" }
  if (typeof contentJson.text === "string") {
    return (
      <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">
        {contentJson.text}
      </p>
    );
  }

  // Future: render rich Tiptap JSON here
  return (
    <p className="text-sm italic text-slate-400">No content added yet.</p>
  );
}
