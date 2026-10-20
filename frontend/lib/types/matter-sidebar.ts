import type {
  Matter,
  Client,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
  TagResponse,
} from "@/lib/types";

export interface MatterSidebarProps {
  project: Matter;
  customers: Client[];
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
