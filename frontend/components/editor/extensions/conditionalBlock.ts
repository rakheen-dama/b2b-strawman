import { Node, mergeAttributes } from "@tiptap/core";
import { ReactNodeViewRenderer } from "@tiptap/react";
import { ConditionalBlockNodeView } from "../node-views/ConditionalBlockNodeView";

export const ConditionalBlockExtension = Node.create({
  name: "conditionalBlock",
  group: "block",
  content: "block+",
  defining: true,

  addAttributes() {
    return {
      fieldKey: { default: "" },
      operator: { default: "isNotEmpty" },
      value: { default: "" },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-conditional-block]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["div", mergeAttributes(HTMLAttributes, { "data-conditional-block": "" }), 0];
  },

  addNodeView() {
    return ReactNodeViewRenderer(ConditionalBlockNodeView);
  },
});
