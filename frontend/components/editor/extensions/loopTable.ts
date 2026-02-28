import { Node, mergeAttributes } from "@tiptap/core";
import { ReactNodeViewRenderer } from "@tiptap/react";
import { LoopTableNodeView } from "../node-views/LoopTableNodeView";

interface LoopTableColumn {
  header: string;
  key: string;
}

export const LoopTableExtension = Node.create({
  name: "loopTable",
  group: "block",
  atom: true,

  addAttributes() {
    return {
      dataSource: {
        default: "",
        parseHTML: (element) => element.getAttribute("data-source"),
        renderHTML: (attributes) => ({
          "data-source": attributes.dataSource as string,
        }),
      },
      columns: {
        default: [] as LoopTableColumn[],
        parseHTML: (element) => {
          const val = element.getAttribute("data-columns");
          if (!val) return [];
          try {
            return JSON.parse(val);
          } catch {
            return [];
          }
        },
        renderHTML: (attributes) => ({
          "data-columns": JSON.stringify(attributes.columns),
        }),
      },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-loop-table]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      "div",
      mergeAttributes(HTMLAttributes, { "data-loop-table": "" }),
    ];
  },

  addNodeView() {
    return ReactNodeViewRenderer(LoopTableNodeView);
  },
});
