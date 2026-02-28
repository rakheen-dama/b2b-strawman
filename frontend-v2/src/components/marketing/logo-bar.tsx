const companies = ["Acme", "Globex", "Initech", "Hooli", "Pied Piper", "Soylent"];

export function LogoBar() {
  return (
    <section className="border-t border-b border-slate-200 py-16 dark:border-slate-800">
      <div className="mx-auto max-w-7xl px-6">
        <p className="text-center text-sm font-medium uppercase tracking-widest text-slate-400 dark:text-slate-500">
          Trusted by teams at
        </p>
        <div className="mt-8 grid grid-cols-3 items-center gap-8 md:grid-cols-6">
          {companies.map((name) => (
            <div key={name} className="text-center">
              <span className="text-lg font-semibold text-slate-400 dark:text-slate-500">
                {name}
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
