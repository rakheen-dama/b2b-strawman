"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@b2mash/ui/button";

interface SidebarCollapseToggleProps {
  collapsed: boolean;
  onToggle: () => void;
  className?: string;
}

export function SidebarCollapseToggle({
  collapsed,
  onToggle,
  className,
}: SidebarCollapseToggleProps) {
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={onToggle}
      aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
      data-testid="sidebar-collapse-toggle"
      className={className}
    >
      {collapsed ? <ChevronRight /> : <ChevronLeft />}
    </Button>
  );
}
