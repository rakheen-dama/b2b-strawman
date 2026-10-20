"use client";

export default function ProjectsError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="flex flex-col items-center gap-4 py-24 text-center">
      <h2 className="font-display text-xl">Something went wrong</h2>
      <p className="text-sm text-slate-600">Unable to load this page. Please try again.</p>
      <button onClick={reset} className="text-sm text-teal-600 hover:underline">
        Try again
      </button>
    </div>
  );
}
