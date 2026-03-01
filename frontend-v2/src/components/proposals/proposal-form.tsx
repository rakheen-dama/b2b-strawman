"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "@/lib/toast";
import { FeeConfigSection } from "./fee-config-section";
import { TeamMemberPicker } from "./team-member-picker";
import type { FeeData } from "./fee-config-section";
import type { Customer, OrgMember } from "@/lib/types";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type {
  FeeModel,
  CreateProposalData,
  UpdateProposalData,
  ProposalDetailResponse,
  MilestoneData,
  TeamMemberData,
  PortalContactSummary,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import {
  createProposal,
  updateProposal,
  replaceMilestones,
  replaceTeamMembers,
  getPortalContacts,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";

interface ProposalFormProps {
  mode: "create" | "edit";
  orgSlug: string;
  customers: Customer[];
  orgMembers: OrgMember[];
  projectTemplates: ProjectTemplateResponse[];
  initialData?: ProposalDetailResponse;
}

export function ProposalForm({
  mode,
  orgSlug,
  customers,
  orgMembers,
  projectTemplates,
  initialData,
}: ProposalFormProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Basics
  const [title, setTitle] = useState(initialData?.title ?? "");
  const [customerId, setCustomerId] = useState(initialData?.customerId ?? "");
  const [portalContactId, setPortalContactId] = useState(
    initialData?.portalContactId ?? "",
  );
  const [expiresAt, setExpiresAt] = useState(
    initialData?.expiresAt ? initialData.expiresAt.split("T")[0] : "",
  );

  // Fee config
  const [feeModel, setFeeModel] = useState<FeeModel>(
    initialData?.feeModel ?? "FIXED",
  );
  const [feeData, setFeeData] = useState<Partial<FeeData>>(() => {
    if (!initialData) return {};
    return {
      fixedFeeAmount: initialData.fixedFeeAmount ?? undefined,
      fixedFeeCurrency: initialData.fixedFeeCurrency ?? "ZAR",
      hourlyRateNote: initialData.hourlyRateNote ?? "",
      retainerAmount: initialData.retainerAmount ?? undefined,
      retainerCurrency: initialData.retainerCurrency ?? "ZAR",
      retainerHoursIncluded: initialData.retainerHoursIncluded ?? undefined,
      milestones: initialData.milestones?.map((m) => ({
        description: m.description,
        percentage: m.percentage,
        relativeDueDays: m.relativeDueDays,
      })) ?? [],
      showMilestones: (initialData.milestones?.length ?? 0) > 0,
    };
  });

  // Body
  const [contentBody, setContentBody] = useState(
    initialData?.contentJson
      ? JSON.stringify(initialData.contentJson, null, 2)
      : "",
  );

  // Team & Project
  const [projectTemplateId, setProjectTemplateId] = useState(
    initialData?.projectTemplateId ?? "",
  );
  const [teamMembers, setTeamMembers] = useState<TeamMemberData[]>(
    initialData?.teamMembers?.map((t) => ({
      memberId: t.memberId,
      role: t.role ?? "",
    })) ?? [],
  );

  // Portal contacts
  const [portalContacts, setPortalContacts] = useState<PortalContactSummary[]>(
    [],
  );
  const [loadingContacts, setLoadingContacts] = useState(false);

  useEffect(() => {
    if (!customerId) {
      setPortalContacts([]);
      setPortalContactId("");
      return;
    }
    setLoadingContacts(true);
    getPortalContacts(customerId)
      .then((contacts) => {
        setPortalContacts(contacts);
        if (
          !contacts.find((c) => c.id === portalContactId)
        ) {
          setPortalContactId("");
        }
      })
      .finally(() => setLoadingContacts(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId]);

  function handleFeeChange(data: FeeData) {
    setFeeModel(data.feeModel);
    setFeeData(data);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !customerId) {
      toast.error("Title and customer are required");
      return;
    }

    setIsSubmitting(true);
    try {
      let contentJson: Record<string, unknown> | undefined;
      if (contentBody.trim()) {
        try {
          contentJson = JSON.parse(contentBody);
        } catch {
          contentJson = { text: contentBody };
        }
      }

      const milestones: MilestoneData[] = feeData.milestones ?? [];

      if (mode === "create") {
        const createData: CreateProposalData = {
          title: title.trim(),
          customerId,
          feeModel,
          ...(portalContactId && { portalContactId }),
          ...(feeModel === "FIXED" &&
            feeData.fixedFeeAmount != null && {
              fixedFeeAmount: feeData.fixedFeeAmount,
              fixedFeeCurrency: feeData.fixedFeeCurrency ?? "ZAR",
            }),
          ...(feeModel === "HOURLY" &&
            feeData.hourlyRateNote && {
              hourlyRateNote: feeData.hourlyRateNote,
            }),
          ...(feeModel === "RETAINER" && {
            ...(feeData.retainerAmount != null && {
              retainerAmount: feeData.retainerAmount,
              retainerCurrency: feeData.retainerCurrency ?? "ZAR",
            }),
            ...(feeData.retainerHoursIncluded != null && {
              retainerHoursIncluded: feeData.retainerHoursIncluded,
            }),
          }),
          ...(contentJson && { contentJson }),
          ...(projectTemplateId && { projectTemplateId }),
          ...(expiresAt && { expiresAt: `${expiresAt}T23:59:59Z` }),
        };

        const proposal = await createProposal(createData);

        if (
          feeModel === "FIXED" &&
          feeData.showMilestones &&
          milestones.length > 0
        ) {
          await replaceMilestones(proposal.id, milestones);
        }
        if (teamMembers.length > 0) {
          await replaceTeamMembers(proposal.id, teamMembers);
        }

        toast.success("Proposal created");
        router.push(`/org/${orgSlug}/proposals`);
      } else {
        const updateData: UpdateProposalData = {
          title: title.trim(),
          customerId,
          feeModel,
          portalContactId: portalContactId || undefined,
          ...(feeModel === "FIXED" && {
            fixedFeeAmount: feeData.fixedFeeAmount,
            fixedFeeCurrency: feeData.fixedFeeCurrency ?? "ZAR",
          }),
          ...(feeModel === "HOURLY" && {
            hourlyRateNote: feeData.hourlyRateNote ?? "",
          }),
          ...(feeModel === "RETAINER" && {
            retainerAmount: feeData.retainerAmount,
            retainerCurrency: feeData.retainerCurrency ?? "ZAR",
            retainerHoursIncluded: feeData.retainerHoursIncluded,
          }),
          ...(contentJson && { contentJson }),
          projectTemplateId: projectTemplateId || undefined,
          ...(expiresAt && { expiresAt: `${expiresAt}T23:59:59Z` }),
        };

        await updateProposal(initialData!.id, updateData);
        await replaceMilestones(initialData!.id, milestones);
        await replaceTeamMembers(initialData!.id, teamMembers);

        toast.success("Proposal updated");
        router.push(`/org/${orgSlug}/proposals/${initialData!.id}`);
      }
    } catch (error) {
      toast.error(
        mode === "create"
          ? "Failed to create proposal"
          : "Failed to update proposal",
      );
      console.error(error);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Section 1: Basics */}
      <Card>
        <CardHeader>
          <CardTitle>Basics</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title">Title</Label>
            <Input
              id="title"
              placeholder="Proposal title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="customer">Customer</Label>
              <Select value={customerId} onValueChange={setCustomerId}>
                <SelectTrigger id="customer">
                  <SelectValue placeholder="Select customer" />
                </SelectTrigger>
                <SelectContent>
                  {customers.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="portalContact">Portal Contact</Label>
              <Select
                value={portalContactId}
                onValueChange={setPortalContactId}
                disabled={!customerId || loadingContacts}
              >
                <SelectTrigger id="portalContact">
                  <SelectValue
                    placeholder={
                      loadingContacts ? "Loading..." : "Select contact"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {portalContacts.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="expiresAt">Expiry Date</Label>
            <Input
              id="expiresAt"
              type="date"
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      {/* Section 2: Fee Configuration */}
      <Card>
        <CardHeader>
          <CardTitle>Fee Configuration</CardTitle>
        </CardHeader>
        <CardContent>
          <FeeConfigSection
            feeModel={feeModel}
            onChange={handleFeeChange}
            initialData={feeData}
          />
        </CardContent>
      </Card>

      {/* Section 3: Proposal Body */}
      <Card>
        <CardHeader>
          <CardTitle>Proposal Body</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <Label htmlFor="contentBody">Content</Label>
            <Textarea
              id="contentBody"
              placeholder="Enter proposal content..."
              value={contentBody}
              onChange={(e) => setContentBody(e.target.value)}
              rows={8}
            />
            <p className="text-xs text-slate-500">
              Plain text content for the proposal body. A rich text editor will
              be available in a future update.
            </p>
          </div>
        </CardContent>
      </Card>

      {/* Section 4: Team & Project */}
      <Card>
        <CardHeader>
          <CardTitle>Team &amp; Project</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="projectTemplate">Project Template</Label>
            <Select
              value={projectTemplateId}
              onValueChange={setProjectTemplateId}
            >
              <SelectTrigger id="projectTemplate">
                <SelectValue placeholder="Select template (optional)" />
              </SelectTrigger>
              <SelectContent>
                {projectTemplates.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <TeamMemberPicker
            members={teamMembers}
            onChange={setTeamMembers}
            orgMembers={orgMembers}
          />
        </CardContent>
      </Card>

      {/* Actions */}
      <div className="flex items-center gap-3">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Save Draft
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
      </div>
    </form>
  );
}
