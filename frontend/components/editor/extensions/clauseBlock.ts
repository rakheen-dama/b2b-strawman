import { Node, mergeAttributes } from "@tiptap/core";
import { ReactNodeViewRenderer } from "@tiptap/react";
import { ClauseBlockNodeView } from "../node-views/ClauseBlockNodeView";

export const ClauseBlockExtension = Node.create({
  name: "clauseBlock",
  group: "block",
  atom: true,
  draggable: true,

  addAttributes() {
    return {
      clauseId: {
        default: "",
        parseHTML: (element) => element.getAttribute("data-clause-id"),
        renderHTML: (attributes) => ({
          "data-clause-id": attributes.clauseId as string,
        }),
      },
      slug: {
        default: "",
        parseHTML: (element) => element.getAttribute("data-slug"),
        renderHTML: (attributes) => ({
          "data-slug": attributes.slug as string,
        }),
      },
      title: {
        default: "",
        parseHTML: (element) => element.getAttribute("data-title"),
        renderHTML: (attributes) => ({
          "data-title": attributes.title as string,
        }),
      },
      required: {
        default: false,
        parseHTML: (element) =>
          element.getAttribute("data-required") === "true",
        renderHTML: (attributes) => ({
          "data-required": String(attributes.required),
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-clause-block]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      "div",
      mergeAttributes(HTMLAttributes, { "data-clause-block": "" }),
    ];
  },

  addNodeView() {
    return ReactNodeViewRenderer(ClauseBlockNodeView);
  },
});
