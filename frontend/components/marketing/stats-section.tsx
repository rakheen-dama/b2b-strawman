const stats = [
  { value: "10,000+", label: "Documents managed" },
  { value: "500+", label: "Active teams" },
  { value: "99.9%", label: "Uptime" },
];

export function StatsSection() {
  return (
    <section className="bg-olive-100 px-6 py-16 dark:bg-olive-900">
      <div className="mx-auto grid max-w-4xl grid-cols-1 gap-8 sm:grid-cols-3">
        {stats.map((stat) => (
          <div key={stat.label} className="text-center">
            <p className="font-display text-4xl font-normal text-olive-950 sm:text-5xl dark:text-olive-50">
              {stat.value}
            </p>
            <p className="mt-2 text-sm text-olive-600 dark:text-olive-400">{stat.label}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
