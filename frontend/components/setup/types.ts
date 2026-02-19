import type { LucideIcon } from "lucide-react";

// ---- SetupProgressCard ----

export interface SetupStep {
  label: string;
  complete: boolean;
  actionHref?: string;
  permissionRequired?: boolean;
}

export interface SetupProgressCardProps {
  title: string;
  completionPercentage: number;
  overallComplete: boolean;
  steps: SetupStep[];
  defaultCollapsed?: boolean;
  canManage?: boolean;
}

// ---- ActionCard ----

export interface ActionCardProps {
  icon: LucideIcon;
  title: string;
  description: string;
  primaryAction?: {
    label: string;
    href: string;
  };
  secondaryAction?: {
    label: string;
    href: string;
  };
  variant?: "default" | "accent";
}

// ---- FieldValueGrid ----

export interface FieldValueProps {
  name: string;
  slug: string;
  value: string | null;
  fieldType: string;
  required: boolean;
  groupId?: string;
}

export interface FieldGroupInfo {
  id: string;
  name: string;
}

export interface FieldValueGridProps {
  fields: FieldValueProps[];
  groups?: FieldGroupInfo[];
  editHref?: string;
}

// ---- EmptyState (setup version) ----

export interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
}

// ---- TemplateReadinessCard ----

export interface TemplateReadinessItem {
  templateId: string;
  templateName: string;
  templateSlug: string;
  ready: boolean;
  missingFields: string[];
}

export interface TemplateReadinessCardProps {
  templates: TemplateReadinessItem[];
  generateHref: (templateId: string) => string;
}
