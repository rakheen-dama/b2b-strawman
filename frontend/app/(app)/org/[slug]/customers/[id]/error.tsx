"use client";

export default function CustomerDetailError({
  error: _error,
  reset,
}: {
  error: Error;
  reset: () => void;
}) {
  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
        Something went wrong
      </h2>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Unable to load customer data. Please try again.
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
