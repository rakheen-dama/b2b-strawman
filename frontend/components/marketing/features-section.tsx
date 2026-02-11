import { FileText, FolderKanban, Shield } from "lucide-react";

const features = [
  {
    icon: FileText,
    heading: "Document Management",
    description:
      "Upload, organize, and share documents across your team. Version tracking and secure storage keep everyone on the same page, from drafts to final deliverables.",
  },
  {
    icon: FolderKanban,
    heading: "Project Collaboration",
    description:
      "Assign roles, manage members, and track project progress in one place. Lead-based ownership gives every project a clear point of accountability.",
  },
  {
    icon: Shield,
    heading: "Enterprise Isolation",
    description:
      "Dedicated database schemas for Pro teams, shared infrastructure for starters. Your data stays yours with tenant-level isolation built into the architecture.",
  },
];

export function FeaturesSection() {
  return (
    <section className="px-6 py-24">
      <div className="mx-auto flex max-w-7xl flex-col gap-16">
        {features.map((feature, index) => (
          <div
            key={feature.heading}
            className={`flex flex-col items-center gap-10 lg:flex-row lg:gap-16 ${
              index % 2 === 1 ? "lg:flex-row-reverse" : ""
            }`}
          >
            {/* Text */}
            <div className="flex-1">
              <feature.icon className="size-6 text-olive-600 dark:text-olive-400" />
              <h3 className="mt-4 font-display text-2xl text-olive-950 dark:text-olive-50">
                {feature.heading}
              </h3>
              <p className="mt-3 text-base leading-7 text-olive-700 dark:text-olive-300">
                {feature.description}
              </p>
            </div>

            {/* Screenshot placeholder */}
            <div className="flex-1">
              <div className="aspect-video rounded-lg bg-olive-100 dark:bg-olive-800" />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
