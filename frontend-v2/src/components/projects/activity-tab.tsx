import { Activity } from "lucide-react";

interface ActivityTabProps {
  projectId: string;
}

export function ActivityTab({ projectId }: ActivityTabProps) {
  return (
    <div className="flex flex-col items-center py-16 text-center">
      <Activity className="size-12 text-slate-300" />
      <h3 className="mt-4 font-display text-lg text-slate-900">
        Activity Feed
      </h3>
      <p className="mt-1 text-sm text-slate-500">
        Project activity timeline coming soon.
      </p>
    </div>
  );
}
