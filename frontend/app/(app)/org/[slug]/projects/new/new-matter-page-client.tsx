"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MatterIntakePanel } from "@/components/ai/matter-intake-panel";
import { instantiateTemplateAction } from "@/app/(app)/org/[slug]/settings/project-templates/actions";
import { toast } from "sonner";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { Customer } from "@/lib/types";

interface NewMatterPageClientProps {
  slug: string;
  initialCustomerId: string;
  customers: Customer[];
  templates: ProjectTemplateResponse[];
  canExecuteAi: boolean;
  canReviewGates: boolean;
  isAiConfigured: boolean;
}

export function NewMatterPageClient({
  slug,
  initialCustomerId,
  customers,
  templates,
  canExecuteAi,
  canReviewGates,
  isAiConfigured,
}: NewMatterPageClientProps) {
  const router = useRouter();
  const [customerId, setCustomerId] = useState(initialCustomerId);
  const [description, setDescription] = useState("");
  const [name, setName] = useState("");
  const [referenceNumber, setReferenceNumber] = useState("");
  const [priority, setPriority] = useState("");
  const [workType, setWorkType] = useState("");
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId);

  function handleTemplateSelected(templateId: string) {
    setSelectedTemplateId(templateId);
    const template = templates.find((t) => t.id === templateId);
    if (template) {
      toast.success(`Template "${template.name}" applied`);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedTemplateId) {
      setError("Please select a template.");
      return;
    }
    if (!name.trim()) {
      setError("Matter name is required.");
      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      const result = await instantiateTemplateAction(slug, selectedTemplateId, {
        name: name.trim(),
        customerId: customerId || undefined,
        description: description.trim() || undefined,
        referenceNumber: referenceNumber.trim() || undefined,
        priority: priority || undefined,
        workType: workType.trim() || undefined,
      });

      if (result.success && result.projectId) {
        toast.success("Matter created successfully");
        router.push(`/org/${slug}/projects/${result.projectId}`);
      } else {
        setError(result.error ?? "Failed to create matter.");
      }
    } catch {
      setError("An unexpected error occurred. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link
          href={`/org/${slug}/projects`}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800 dark:hover:text-slate-300"
        >
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="font-display text-2xl font-semibold text-slate-950 dark:text-slate-50">
          New Matter
        </h1>
      </div>

      {/* Two-column layout */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Left: Creation Form */}
        <Card className="border-slate-200 dark:border-slate-800">
          <CardHeader>
            <CardTitle className="text-base font-semibold text-slate-950 dark:text-slate-50">
              Matter Details
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="customerId">Customer</Label>
                <select
                  id="customerId"
                  value={customerId}
                  onChange={(e) => setCustomerId(e.target.value)}
                  className={nativeSelectClassName}
                >
                  <option value="">-- Select a customer --</option>
                  {customers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">
                  Description{" "}
                  <span className="text-sm font-normal text-slate-500">(min 20 chars for AI)</span>
                </Label>
                <Textarea
                  id="description"
                  placeholder="Describe the matter in detail for AI analysis..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={4}
                  maxLength={2000}
                />
                {description.trim().length > 0 && description.trim().length < 20 && (
                  <p className="text-xs text-slate-500">
                    {20 - description.trim().length} more characters needed for AI intake
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="templateId">Template</Label>
                <select
                  id="templateId"
                  value={selectedTemplateId ?? ""}
                  onChange={(e) => setSelectedTemplateId(e.target.value || null)}
                  className={nativeSelectClassName}
                >
                  <option value="">-- Select a template --</option>
                  {templates
                    .filter((t) => t.active)
                    .map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                </select>
                {selectedTemplate && (
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    {selectedTemplate.taskCount} tasks, {selectedTemplate.tagCount} tags
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="name">Matter Name</Label>
                <Input
                  id="name"
                  placeholder="e.g. Smith v Jones"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={255}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="referenceNumber">
                  Reference Number{" "}
                  <span className="text-sm font-normal text-slate-500">(optional)</span>
                </Label>
                <Input
                  id="referenceNumber"
                  placeholder="e.g. MAT-2026-001"
                  value={referenceNumber}
                  onChange={(e) => setReferenceNumber(e.target.value)}
                  maxLength={100}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="priority">
                  Priority <span className="text-sm font-normal text-slate-500">(optional)</span>
                </Label>
                <select
                  id="priority"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                  className={nativeSelectClassName}
                >
                  <option value="">Select priority...</option>
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="workType">
                  Work Type <span className="text-sm font-normal text-slate-500">(optional)</span>
                </Label>
                <Input
                  id="workType"
                  placeholder="e.g. Litigation, Conveyancing"
                  value={workType}
                  onChange={(e) => setWorkType(e.target.value)}
                  maxLength={50}
                />
              </div>

              {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

              <div className="flex items-center gap-3 pt-2">
                <Button
                  type="submit"
                  disabled={isSubmitting || !selectedTemplateId || !name.trim()}
                >
                  {isSubmitting ? "Creating..." : "Create Matter"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => router.push(`/org/${slug}/projects`)}
                >
                  Cancel
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {/* Right: AI Intake Panel — gated by AI_EXECUTE */}
        {canExecuteAi && (
          <div className="space-y-4">
            <MatterIntakePanel
              customerId={customerId}
              slug={slug}
              description={description}
              isAiConfigured={isAiConfigured}
              canReviewGates={canReviewGates}
              onTemplateSelected={handleTemplateSelected}
            />
          </div>
        )}
      </div>
    </div>
  );
}
