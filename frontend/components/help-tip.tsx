"use client";

import { CircleHelp, ExternalLink } from "lucide-react";
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover";
import { createMessages } from "@/lib/messages";
import { docsLink } from "@/lib/docs";

interface HelpTipProps {
  code: string;
  docsPath?: string;
}

export function HelpTip({ code, docsPath }: HelpTipProps) {
  const { t } = createMessages("help");

  const title = t(`${code}.title`);
  const body = t(`${code}.body`);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex cursor-pointer text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
          aria-label={`Help: ${title}`}
        >
          <CircleHelp className="size-4" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="p-4">
        <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {title}
        </p>
        <p className="mt-1 max-w-xs text-sm text-slate-600 dark:text-slate-400">
          {body}
        </p>
        {docsPath && (
          <a
            href={docsLink(docsPath)}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 inline-flex items-center gap-1 text-sm text-teal-600 hover:text-teal-700"
          >
            Learn more
            <ExternalLink className="size-3" />
          </a>
        )}
      </PopoverContent>
    </Popover>
  );
}
