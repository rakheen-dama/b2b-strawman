/**
 * The `--sidebar-primary` bar on the active sidebar item (replaces the old Motion `layoutId`
 * indicator). Shared by AppSidebar and SidebarUtilityFooter.
 */
export const SIDEBAR_ACTIVE_BAR =
  "relative data-[active=true]:before:absolute data-[active=true]:before:inset-y-1 data-[active=true]:before:left-0 data-[active=true]:before:w-0.5 data-[active=true]:before:rounded-full data-[active=true]:before:bg-sidebar-primary";
