"use client";

export default function ProjectsError({
  error,
  reset,
}: {
  error: Error;
  reset: () => void;
}) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <h2 className="font-display text-xl">Something went wrong</h2>
      <p className="text-sm text-slate-600">
        Unable to load projects. Please try again.
      </p>
      <button
        onClick={reset}
        className="text-sm text-teal-600 hover:underline"
      >
        Try again
      </button>
    </div>
  );
}
