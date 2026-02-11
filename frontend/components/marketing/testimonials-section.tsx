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
            className="rounded-lg bg-olive-950/[0.025] p-6"
          >
            <span className="font-display text-4xl leading-none text-olive-300">
              &ldquo;
            </span>
            <p className="mt-2 text-olive-800">{testimonial.quote}</p>

            <div className="mt-4 border-t border-olive-200 pt-4">
              <div className="flex items-center gap-3">
                <div className="flex size-12 items-center justify-center rounded-full bg-olive-200 text-sm font-medium text-olive-600">
                  {testimonial.initials}
                </div>
                <div>
                  <p className="font-semibold text-olive-950">
                    {testimonial.name}
                  </p>
                  <p className="text-sm text-olive-600">
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
