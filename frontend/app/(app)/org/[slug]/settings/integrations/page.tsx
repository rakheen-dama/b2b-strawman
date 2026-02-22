export default async function IntegrationsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug: _slug } = await params;

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
        Integrations
      </h1>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Connect third-party tools and services to your organization.
      </p>
    </div>
  );
}
