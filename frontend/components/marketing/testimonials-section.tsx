const testimonials = [
  {
    quote:
      "DocTeams replaced three separate tools for us. Document sharing, project tracking, and team management — all under one roof with real data isolation.",
    name: "Sarah Chen",
    title: "VP of Engineering, Meridian Labs",
    initials: "SC",
  },
  {
    quote:
      "The schema-per-tenant architecture sold us on Pro. Knowing our data is physically separated from other organizations gives our compliance team peace of mind.",
    name: "James Okoro",
    title: "CTO, Verdant Health",
    initials: "JO",
  },
  {
    quote:
      "We onboarded our 40-person team in under an hour. The Clerk integration made SSO setup painless, and the role-based access works exactly how we expected.",
    name: "Maria Gonzalez",
    title: "Head of Operations, Catalyst Finance",
    initials: "MG",
  },
];

export function TestimonialsSection() {
  return (
    <section className="px-6 py-24">
      <div className="mx-auto grid max-w-7xl grid-cols-1 gap-6 md:grid-cols-3">
        {testimonials.map((testimonial) => (
          <div
            key={testimonial.name}
            className="rounded-lg bg-olive-950/[0.025] p-6 dark:bg-olive-50/[0.05]"
          >
            <span className="font-display text-4xl leading-none text-olive-300 dark:text-olive-700">
              &ldquo;
            </span>
            <p className="mt-2 text-olive-800 dark:text-olive-200">{testimonial.quote}</p>

            <div className="mt-4 border-t border-olive-200 pt-4 dark:border-olive-800">
              <div className="flex items-center gap-3">
                <div className="flex size-12 items-center justify-center rounded-full bg-olive-200 text-sm font-medium text-olive-600 dark:bg-olive-800 dark:text-olive-400">
                  {testimonial.initials}
                </div>
                <div>
                  <p className="font-semibold text-olive-950 dark:text-olive-50">
                    {testimonial.name}
                  </p>
                  <p className="text-sm text-olive-600 dark:text-olive-400">
                    {testimonial.title}
                  </p>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
