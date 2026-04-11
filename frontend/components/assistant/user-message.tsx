import { cn } from "@/lib/utils";

interface UserMessageProps {
  message: {
    content: string;
  };
}

export function UserMessage({ message }: UserMessageProps) {
  return (
    <div
      className={cn(
        "ml-auto max-w-[85%] rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-900",
        "dark:bg-slate-800 dark:text-slate-100"
      )}
    >
      <p className="whitespace-pre-wrap">{message.content}</p>
    </div>
  );
}
