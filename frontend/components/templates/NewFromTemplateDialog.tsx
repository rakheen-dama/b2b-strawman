"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { TemplatePicker } from "@/components/templates/TemplatePicker";
import { instantiateTemplateAction } from "@/app/(app)/org/[slug]/settings/project-templates/actions";
import { resolveNameTokens } from "@/lib/name-token-resolver";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

interface NewFromTemplateDialogProps {
  slug: string;
  templates: ProjectTemplateResponse[];
  orgMembers: OrgMember[];
  customers: Customer[];
  children: React.ReactNode;
}

export function NewFromTemplateDialog({
  slug,
  templates,
  orgMembers,
  customers,
  children,
}: NewFromTemplateDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Step 1
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);

  // Step 2
  const [projectName, setProjectName] = useState("");
  const [description, setDescription] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [projectLeadMemberId, setProjectLeadMemberId] = useState("");

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId) ?? null;

  const selectedCustomerName = customers.find((c) => c.id === customerId)?.name;

  const namePreview = selectedTemplate
    ? resolveNameTokens(selectedTemplate.namePattern, selectedCustomerName, new Date())
    : "";

  function handleOpenChange(newOpen: boolean) {
    if (isSubmitting) return;
    if (newOpen) {
      setStep(1);
      setSelectedTemplateId(null);
      setProjectName("");
      setDescription("");
      setCustomerId("");
      setProjectLeadMemberId("");
      setError(null);
    }
    setOpen(newOpen);
  }

  function handleSelectTemplate(id: string) {
    setSelectedTemplateId(id);
  }

  function handleNext() {
    if (!selectedTemplate) return;
    // Pre-fill name from pattern
    const resolvedName = resolveNameTokens(
      selectedTemplate.namePattern,
      selectedCustomerName,
      new Date(),
    );
    setProjectName(resolvedName);
    setDescription(selectedTemplate.description ?? "");
    setError(null);
    setStep(2);
  }

  function handleBack() {
    setStep(1);
    setError(null);
  }

  async function handleSubmit() {
    if (!selectedTemplateId) return;

    setError(null);
    setIsSubmitting(true);

    try {
      const result = await instantiateTemplateAction(slug, selectedTemplateId, {
        name: projectName.trim() || undefined,
        customerId: customerId || undefined,
        projectLeadMemberId: projectLeadMemberId || undefined,
        description: description.trim() || undefined,
      });

      if (result.success && result.projectId) {
        setOpen(false);
        router.push(`/org/${slug}/projects/${result.projectId}`);
      } else {
        setError(result.error ?? "Failed to create project.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  // Update name preview when customer changes
  function handleCustomerChange(newCustomerId: string) {
    setCustomerId(newCustomerId);
    if (selectedTemplate) {
      const newCustomerName = customers.find((c) => c.id === newCustomerId)?.name;
      setProjectName(
        resolveNameTokens(selectedTemplate.namePattern, newCustomerName, new Date()),
      );
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {step === 1
              ? "New from Template \u2014 Select Template"
              : "New from Template \u2014 Configure"}
          </DialogTitle>
          <DialogDescription>
            {step === 1
              ? "Choose a template to create a new project."
              : `Creating from: ${selectedTemplate?.name}`}
          </DialogDescription>
        </DialogHeader>

        {step === 1 ? (
          <>
            <div className="py-2">
              <TemplatePicker
                templates={templates}
                selectedId={selectedTemplateId}
                onSelect={handleSelectTemplate}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="plain" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="button" onClick={handleNext} disabled={!selectedTemplateId}>
                Next
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <div className="space-y-4 py-2">
              {/* Project Name */}
              <div className="space-y-2">
                <Label htmlFor="new-proj-name">Project name</Label>
                <Input
                  id="new-proj-name"
                  value={projectName}
                  onChange={(e) => setProjectName(e.target.value)}
                  placeholder="Project name..."
                  maxLength={255}
                />
                {namePreview && projectName !== namePreview && (
                  <p className="text-xs text-slate-400 dark:text-slate-500">
                    Pattern preview:{" "}
                    <span className="font-medium text-slate-600 dark:text-slate-300">
                      {namePreview}
                    </span>
                  </p>
                )}
              </div>

              {/* Description */}
              <div className="space-y-2">
                <Label htmlFor="new-proj-desc">
                  Description{" "}
                  <span className="font-normal text-muted-foreground">(optional)</span>
                </Label>
                <Textarea
                  id="new-proj-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional description..."
                  rows={2}
                  maxLength={2000}
                />
              </div>

              {/* Customer */}
              {customers.length > 0 && (
                <div className="space-y-2">
                  <Label htmlFor="new-proj-customer">
                    Customer{" "}
                    <span className="font-normal text-muted-foreground">(optional)</span>
                  </Label>
                  <select
                    id="new-proj-customer"
                    value={customerId}
                    onChange={(e) => handleCustomerChange(e.target.value)}
                    className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                  >
                    <option value="">None</option>
                    {customers.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Project Lead */}
              {orgMembers.length > 0 && (
                <div className="space-y-2">
                  <Label htmlFor="new-proj-lead">
                    Project lead{" "}
                    <span className="font-normal text-muted-foreground">(optional)</span>
                  </Label>
                  <select
                    id="new-proj-lead"
                    value={projectLeadMemberId}
                    onChange={(e) => setProjectLeadMemberId(e.target.value)}
                    className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                  >
                    <option value="">Unassigned</option>
                    {orgMembers.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {error && <p className="text-sm text-destructive">{error}</p>}
            </div>
            <DialogFooter>
              <Button type="button" variant="plain" onClick={handleBack} disabled={isSubmitting}>
                Back
              </Button>
              <Button type="button" onClick={handleSubmit} disabled={isSubmitting}>
                {isSubmitting ? "Creating..." : "Create Project"}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
