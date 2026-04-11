"use client";

import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAssistant } from "@/components/assistant/assistant-provider";

export function AssistantTrigger() {
  const { isOpen, isAiEnabled, toggle } = useAssistant();

  if (!isAiEnabled || isOpen) return null;

  return (
    <Button
      variant="accent"
      size="icon"
      className="fixed right-6 bottom-6 z-50 size-12 rounded-full shadow-lg"
      onClick={toggle}
      aria-label="Open assistant"
    >
      <Sparkles className="size-5" />
    </Button>
  );
}
