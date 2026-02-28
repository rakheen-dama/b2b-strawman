import { Node, mergeAttributes } from "@tiptap/core";
import { ReactNodeViewRenderer } from "@tiptap/react";
import { VariableNodeView } from "../node-views/VariableNodeView";

export const VariableExtension = Node.create({
  name: "variable",
  group: "inline",
  inline: true,
  atom: true,

  addAttributes() {
    return {
      key: {
        default: "",
        parseHTML: (element) => element.getAttribute("data-variable-key"),
        renderHTML: (attributes) => ({
          "data-variable-key": attributes.key as string,
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: "span[data-variable-key]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      "span",
      mergeAttributes(HTMLAttributes, {
        "data-variable-key": HTMLAttributes["data-variable-key"],
      }),
    ];
  },

  addNodeView() {
    return ReactNodeViewRenderer(VariableNodeView);
  },
});
