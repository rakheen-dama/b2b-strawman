import type {
  Project,
  Customer,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
} from "@/lib/types";

export interface MatterSidebarProps {
  project: Project;
  customers: Customer[];
  slug: string;
  canEdit: boolean;
  canManage: boolean;
  isAdmin: boolean;
  isOwner: boolean;
  /** Custom field data */
  fieldDefinitions: FieldDefinitionResponse[];
  fieldGroups: FieldGroupResponse[];
  groupMembers: Record<string, FieldGroupMemberResponse[]>;
  /** Tags data */
  projectTags: TagResponse[];
  allTags: TagResponse[];
}
